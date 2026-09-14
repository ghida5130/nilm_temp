"""
NILM 스마트홈 전력 시뮬레이터 HTTP 요청 핸들러 모듈

ThreadedHTTPServer 및 BaseHTTPRequestHandler 기반의 RequestHandler 클래스를 정의합니다.
웹 프런트엔드 정적 파일(waveform_viewer.html) 서빙, REST API 제어 엔드포인트(/api/status,
/api/start, /api/stop, /api/device), Server-Sent Events(SSE) 실시간 스트리밍(/api/stream)을 처리합니다.
"""

import json
import os
import queue
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

# 상위 디렉터리(infrastructure/mqtt/simulator) import 경로 등록
PARENT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PARENT_DIR not in sys.path:
    sys.path.append(PARENT_DIR)

import simulator
import scenarios
from .config import ALLOWED_SCENARIOS
from .manager import SimulatorManager, ModeConflictError


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


class RequestHandler(BaseHTTPRequestHandler):
    manager: SimulatorManager = None
    html_path: str = None

    def log_message(self, format, *args):
        # 불필요한 표준 콘솔 로그 억제
        pass

    def handle(self):
        try:
            super().handle()
        except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
            pass

    def do_GET(self):
        url_path = self.path.split('?')[0]

        if url_path in ("/", "/index.html", "/waveform_viewer.html"):
            # 1. waveform_viewer.html 파일 제공
            target_html = self.html_path or os.path.join(PARENT_DIR, "waveform_viewer.html")
            try:
                with open(target_html, "r", encoding="utf-8") as f:
                    content = f.read().encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)
            except Exception as e:
                self.send_error(500, f"HTML 파일 로드 실패: {e}")

        elif url_path == "/api/status":
            # 2. 현재 시뮬레이터 실행 상태 확인
            cfg = self.manager.get_connection_config() if hasattr(self.manager, "get_connection_config") else {
                "host": simulator.DEFAULT_BROKER_HOST,
                "port": simulator.DEFAULT_BROKER_PORT,
                "tls_enabled": False
            }
            tls_desc = " (TLS)" if cfg.get("tls_enabled") else ""
            resp_data = {
                "is_running": self.manager.is_running,
                "current_mode": self.manager.current_mode,
                "cycle_count": self.manager.cycle_count,
                "broker": f"{cfg['host']}:{cfg['port']}{tls_desc}",
                "last_metrics": self.manager.last_metrics,
                "simulation_date": self.manager.simulation_date if self.manager.is_running else None,
                "resolved_start_time": self.manager.resolved_start_time if self.manager.is_running else None,
            }
            content = json.dumps(resp_data).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        elif url_path == "/api/stream":
            # 3. Server-Sent Events (SSE) 실시간 데이터 스트리밍
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()

            q = queue.Queue(maxsize=100)
            self.manager.add_subscriber(q)

            try:
                # 초기 연결 확인 핑
                self.wfile.write(b": keepalive\n\n")
                self.wfile.flush()

                while True:
                    try:
                        # 1초 타임아웃으로 대기
                        data = q.get(timeout=1.5)
                        msg = f"data: {json.dumps(data)}\n\n".encode("utf-8")
                        self.wfile.write(msg)
                        self.wfile.flush()
                    except queue.Empty:
                        # 하트비트 핑
                        self.wfile.write(b": heartbeat\n\n")
                        self.wfile.flush()
            except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
                pass
            finally:
                self.close_connection = True
                self.manager.remove_subscriber(q)

        else:
            self.send_error(404, "Not Found")

    def send_json(self, status_code: int, data: dict):
        content = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(content)))
        if status_code >= 400:
            self.send_header("Connection", "close")
            self.close_connection = True
        self.end_headers()
        self.wfile.write(content)
        try:
            self.wfile.flush()
        except Exception:
            pass

    def send_error_json(self, status_code: int, error_code: str, message: str):
        self.send_json(status_code, {
            "status": "error",
            "code": error_code,
            "message": message
        })

    def read_json_body(self, allow_empty: bool = False):
        """Content-Length 및 JSON 본문을 안전하게 파싱하고 검증한다.
        성공 시 (data, True)를 반환하고, 실패 시 400 JSON 응답을 전송하고 (None, False)를 반환한다.
        """
        raw_length = self.headers.get("Content-Length")
        if raw_length is None:
            if allow_empty:
                return {}, True
            self.send_error_json(400, "BAD_REQUEST", "Content-Length 헤더가 누락되었거나 요청 본문이 비어있습니다.")
            return None, False

        try:
            content_length = int(raw_length.strip())
        except (ValueError, TypeError, AttributeError):
            self.send_error_json(400, "BAD_REQUEST", f"올바르지 않은 Content-Length 값입니다: '{raw_length}'")
            return None, False

        if content_length < 0:
            self.send_error_json(400, "BAD_REQUEST", f"Content-Length는 음수일 수 없습니다: {content_length}")
            return None, False

        if content_length == 0:
            if allow_empty:
                return {}, True
            self.send_error_json(400, "BAD_REQUEST", "요청 본문이 비어있습니다.")
            return None, False

        try:
            raw_body = self.rfile.read(content_length)
        except Exception as err:
            self.send_error_json(400, "BAD_REQUEST", f"요청 본문 읽기 실패: {err}")
            return None, False

        try:
            body_str = raw_body.decode("utf-8")
        except UnicodeDecodeError:
            self.send_error_json(400, "BAD_REQUEST", "UTF-8 디코딩에 실패했습니다.")
            return None, False

        if not body_str.strip():
            if allow_empty:
                return {}, True
            self.send_error_json(400, "BAD_REQUEST", "요청 본문이 비어있습니다.")
            return None, False

        try:
            params = json.loads(body_str)
        except Exception:
            self.send_error_json(400, "BAD_REQUEST", "유효하지 않은 JSON 형식입니다.")
            return None, False

        if not isinstance(params, dict):
            self.send_error_json(400, "BAD_REQUEST", "JSON 객체(dictionary)여야 합니다.")
            return None, False

        return params, True

    def do_POST(self):
        url_path = self.path.split('?')[0]

        if url_path == "/api/start":
            # 1. 시뮬레이션 시작 (MQTT 발행 + 실시간 스트림)
            params, ok = self.read_json_body(allow_empty=True)
            if not ok:
                return

            scenario = params.get("scenario", "peak")
            house = params.get("house", "H001")

            # 시나리오 허용 목록 검증 (peak, routine_missed, random, manual)
            if scenario not in ALLOWED_SCENARIOS:
                self.send_error_json(400, "BAD_REQUEST", f"지원하지 않는 시나리오입니다: '{scenario}'. 허용 목록: {sorted(ALLOWED_SCENARIOS)}")
                return

            if not isinstance(house, str) or house not in simulator.DEFAULT_HOUSES:
                self.send_error_json(400, "BAD_REQUEST", f"유효하지 않은 house ID입니다: '{house}'. 허용 목록: {simulator.DEFAULT_HOUSES}")
                return

            # simulation_date 선택 필드 엄격 검증
            simulation_date = None
            if "simulation_date" in params and params["simulation_date"] is not None:
                raw_sim_date = params["simulation_date"]
                if not isinstance(raw_sim_date, str):
                    self.send_error_json(
                        400,
                        "BAD_REQUEST",
                        "잘못된 simulation_date 필드입니다: simulation_date는 YYYY-MM-DD 형식의 문자열이어야 합니다."
                    )
                    return
                try:
                    simulation_date = scenarios.parse_simulation_date(raw_sim_date)
                except ValueError as err:
                    self.send_error_json(
                        400,
                        "BAD_REQUEST",
                        f"잘못된 simulation_date 필드입니다: {err}"
                    )
                    return

            try:
                started_info = self.manager.start(scenario=scenario, house=house, simulation_date=simulation_date)
                self.send_json(200, {
                    "status": "started",
                    "scenario": scenario,
                    "house": house,
                    "simulation_date": started_info.get("simulation_date"),
                    "resolved_start_time": started_info.get("resolved_start_time")
                })
            except (ValueError, FileNotFoundError, PermissionError) as err:
                self.send_error_json(400, "CONFIG_ERROR", str(err))
            except RuntimeError as err:
                self.send_error_json(409, "START_FAILED", str(err))
            except Exception as err:
                self.send_error_json(500, "START_FAILED", str(err))

        elif url_path == "/api/stop":
            # 2. 시뮬레이션 중지
            stopped = self.manager.stop()
            status_code = 200 if stopped else 503
            resp = {"status": "stopped" if stopped else "stop_timeout"}
            self.send_json(status_code, resp)

        else:
            self.send_error(404, "Not Found")

    def do_PUT(self):
        url_path = self.path.split('?')[0]

        if url_path == "/api/device":
            params, ok = self.read_json_body(allow_empty=False)
            if not ok:
                return

            house = params.get("house")
            device = params.get("device")
            enabled = params.get("enabled")

            if house is None or device is None or enabled is None:
                self.send_error_json(400, "BAD_REQUEST", "house, device, enabled 필드는 필수입니다.")
                return

            if not isinstance(house, str) or not isinstance(device, str) or type(enabled) is not bool:
                self.send_error_json(400, "BAD_REQUEST", "house와 device는 문자열, enabled는 정확히 bool 타입이어야 합니다.")
                return

            if device not in simulator.DEVICE_PROFILES:
                self.send_error_json(400, "BAD_REQUEST", f"지원하지 않는 가전입니다: '{device}'")
                return

            try:
                snapshot = self.manager.set_device(house, device, enabled)
                resp = {
                    "status": "success",
                    "house": house,
                    "device": device,
                    "enabled": enabled,
                    "device_state": snapshot
                }
                self.send_json(200, resp)
            except ModeConflictError as err:
                self.send_error_json(409, "INVALID_MODE", str(err))
            except ValueError as err:
                self.send_error_json(400, "BAD_REQUEST", str(err))
            except Exception as err:
                self.send_error_json(500, "INTERNAL_ERROR", f"서버 내부 오류: {err}")

        else:
            self.send_error(404, "Not Found")

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()


def create_request_handler(manager: SimulatorManager, html_path: str = None) -> type:
    """SimulatorManager 인스턴스와 HTML 경로가 주입된 RequestHandler 클래스를 생성합니다."""
    class InjectedRequestHandler(RequestHandler):
        pass

    InjectedRequestHandler.manager = manager
    InjectedRequestHandler.html_path = html_path
    return InjectedRequestHandler

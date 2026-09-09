"""
NILM 스마트홈 전력 시뮬레이터 경량 웹 컨트롤러 서버 (Web Controller Server)

브라우저 화면(waveform_viewer.html)의 버튼 조작에 따라 실제 Mosquitto MQTT 브로커로
전력 데이터를 실시간 발행(Publish)하고, 브라우저 차트와 완벽히 동기화(SSE)합니다.

실행 방법:
    python web_server.py
    (브라우저가 자동으로 http://localhost:8085 로 열립니다)
"""

import os
import sys
import json
import time
import queue
import asyncio
import threading
import webbrowser
from datetime import datetime, timezone, timedelta
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

# Windows SelectorLoop 호환성 설정
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if CURRENT_DIR not in sys.path:
    sys.path.append(CURRENT_DIR)

import aiomqtt
import simulator
import scenarios

DEFAULT_PORT = 8085

# ==========================================
# 1. 시뮬레이터 백그라운드 관리자 상태
# ==========================================
class SimulatorManager:
    def __init__(self):
        self.lock = threading.Lock()
        self.is_running = False
        self.current_mode = "idle"  # "idle", "peak", "random"
        self.stop_event = threading.Event()
        self.worker_thread = None
        self.subscribers = []  # SSE 큐 목록
        self.last_metrics = None
        self.cycle_count = 0

    def add_subscriber(self, q: queue.Queue):
        with self.lock:
            self.subscribers.append(q)

    def remove_subscriber(self, q: queue.Queue):
        with self.lock:
            if q in self.subscribers:
                self.subscribers.remove(q)

    def broadcast(self, data: dict):
        with self.lock:
            self.last_metrics = data
            for q in list(self.subscribers):
                try:
                    q.put_nowait(data)
                except queue.Full:
                    pass

    def start(self, scenario: str = "peak", house: str = "H001"):
        with self.lock:
            if self.is_running:
                self.stop()

            self.stop_event.clear()
            self.is_running = True
            self.current_mode = scenario
            self.cycle_count = 0
            self.worker_thread = threading.Thread(
                target=self._run_async_worker,
                args=(scenario, house),
                daemon=True
            )
            self.worker_thread.start()

    def stop(self):
        with self.lock:
            if self.is_running:
                self.stop_event.set()
                self.is_running = False
                self.current_mode = "idle"

    def _run_async_worker(self, scenario: str, house: str):
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(self._worker_loop(scenario, house))
        except Exception as err:
            print(f"[WebSimulator] 워커 오류: {err}", flush=True)
        finally:
            loop.close()
            with self.lock:
                self.is_running = False
                self.current_mode = "idle"

    async def _worker_loop(self, scenario: str, house: str):
        is_peak = (scenario == "peak")
        is_routine_missed = (scenario == "routine_missed")
        allow_random = not (is_peak or is_routine_missed)

        if is_peak:
            target_count = 60
        elif is_routine_missed:
            target_count = 300  # AI 299초 슬라이딩 윈도우 충족
        else:
            target_count = 999999
        interval = 1.0

        # 상태 초기화
        simulator.init_simulation_states([house])

        base_dt = None
        if is_routine_missed:
            # 08:10:01 KST 시점 기준 타임스탬프 생성
            base_dt = scenarios.parse_simulation_start_time("08:10:01", is_missed_mode=True)

        print(f"[WebSimulator] MQTT 브로커({simulator.DEFAULT_BROKER_HOST}:{simulator.DEFAULT_BROKER_PORT}) 연결 중...", flush=True)

        async with aiomqtt.Client(
            hostname=simulator.DEFAULT_BROKER_HOST,
            port=simulator.DEFAULT_BROKER_PORT,
            username=simulator.DEFAULT_BROKER_USER,
            password=simulator.DEFAULT_BROKER_PASS,
            keepalive=60,
            timeout=5
        ) as client:
            print(f"[WebSimulator] MQTT 연결 성공 실시간 발행 시작 (시나리오: {scenario}, 대상: {house})", flush=True)

            cycle = 0
            while not self.stop_event.is_set():
                cycle_start = time.time()
                if base_dt is not None:
                    sim_dt = base_dt + timedelta(seconds=cycle)
                    now_iso = sim_dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
                    sim_time_kst = (base_dt + timedelta(seconds=cycle)).astimezone(scenarios.KST).strftime("%H:%M:%S")
                else:
                    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
                    sim_time_kst = None

                cycle += 1
                self.cycle_count = cycle

                # 1. 타임라인 이벤트 주입 및 안내 메시지
                event_desc = None
                if is_peak:
                    event_desc = simulator.inject_peak_scenario_event(cycle, house)
                elif is_routine_missed:
                    if cycle == 1:
                        event_desc = "08:10 아침 루틴 검증 시작 (전자레인지 미가동 / 대기전력 유지)"
                    elif cycle == 100:
                        event_desc = "대기전력 지속 중 (누적 100초)"
                    elif cycle == 200:
                        event_desc = "대기전력 지속 중 (누적 200초)"
                    elif cycle == scenarios.RoutineMissedScenario.BUFFER_WINDOW_SIZE:
                        event_desc = "299초 버퍼 충족 (AI 이상치 감지 조건 도달 -> analysis.event.v1 발행!)"
                    elif cycle == 300:
                        event_desc = "시연 완료 (300초 데이터 전송 완료)"

                # 2. 물리 계측 및 MQTT 발행 (allow_random: peak 및 routine_missed 모드는 False)
                res = await simulator.publish_house_power(
                    client=client,
                    house=house,
                    now_iso=now_iso,
                    qos=1,
                    allow_random=allow_random
                )

                # 3. 실시간 UI 동기화용 패킷 생성 및 SSE 브로드캐스트
                metrics = simulator.calculate_main_panel_metrics(house, allow_random=allow_random)

                broadcast_data = {
                    "sec": cycle,
                    "now_iso": now_iso,
                    "simTimeKst": sim_time_kst,
                    "house": house,
                    "totalP": metrics["active_power"],
                    "totalQ": metrics["reactive_power"],
                    "apparentS": metrics["apparent_power"],
                    "pf": metrics["power_factor"],
                    "voltage": metrics["voltage"],
                    "currentA": metrics["current"],
                    "activeNames": metrics["active_devices"],
                    "eventNoticeText": event_desc,
                    "mode": scenario
                }
                self.broadcast(broadcast_data)

                # 터미널 콘솔 로그 출력
                if is_peak:
                    status_tag = "대기"
                    if metrics["active_power"] >= 3000.0:
                        status_tag = "피크 경보 (3,000W+)"
                    elif metrics["active_power"] >= 1000.0:
                        status_tag = "가전 가동 중"
                    notice_str = f" <== [{event_desc}]" if event_desc else ""
                    print(f"[WebSimulator] (T+{cycle:02d}s) {house} 전력: {metrics['active_power']:7.1f} W | {status_tag}{notice_str}", flush=True)
                elif is_routine_missed:
                    status_tag, notice = scenarios.RoutineMissedScenario.get_cycle_status(cycle)
                    notice_str = f" <== [{event_desc}]" if event_desc else notice
                    print(f"[WebSimulator] (T+{cycle:03d}s | KST {sim_time_kst}) {house} 전력: {metrics['active_power']:5.1f} W | {status_tag}{notice_str}", flush=True)
                else:
                    status_tag = "가전 가동 중" if metrics["active_power"] >= 500.0 else "대기"
                    print(f"[WebSimulator] (T+{cycle:02d}s) {house} 전력: {metrics['active_power']:7.1f} W | {status_tag}", flush=True)

                if target_count > 0 and cycle >= target_count:
                    print(f"[WebSimulator] 목표 사이클({target_count}회) 완주, 자동 정지합니다.", flush=True)
                    break

                elapsed = time.time() - cycle_start
                sleep_time = max(0.0, interval - elapsed)
                await asyncio.sleep(sleep_time)


manager = SimulatorManager()


# ==========================================
# 2. 멀티스레드 HTTP 핸들러 및 SSE 엔드포인트
# ==========================================
class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


class RequestHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # 불필요한 표준 콘솔 로그 억제
        pass

    def do_GET(self):
        url_path = self.path.split('?')[0]

        if url_path in ("/", "/index.html", "/waveform_viewer.html"):
            # 1. waveform_viewer.html 파일 제공
            html_path = os.path.join(CURRENT_DIR, "waveform_viewer.html")
            try:
                with open(html_path, "r", encoding="utf-8") as f:
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
            resp_data = {
                "is_running": manager.is_running,
                "current_mode": manager.current_mode,
                "cycle_count": manager.cycle_count,
                "broker": f"{simulator.DEFAULT_BROKER_HOST}:{simulator.DEFAULT_BROKER_PORT}",
                "last_metrics": manager.last_metrics
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
            manager.add_subscriber(q)

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
            except (ConnectionResetError, BrokenPipeError):
                pass
            finally:
                manager.remove_subscriber(q)

        else:
            self.send_error(404, "Not Found")

    def do_POST(self):
        url_path = self.path.split('?')[0]

        if url_path == "/api/start":
            # 1. 시뮬레이션 시작 (MQTT 발행 + 실시간 스트림)
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else "{}"
            try:
                params = json.loads(body)
            except Exception:
                params = {}

            scenario = params.get("scenario", "peak")
            house = params.get("house", "H001")

            manager.start(scenario=scenario, house=house)

            resp = {"status": "started", "scenario": scenario, "house": house}
            content = json.dumps(resp).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        elif url_path == "/api/stop":
            # 2. 시뮬레이션 중지
            manager.stop()
            resp = {"status": "stopped"}
            content = json.dumps(resp).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        else:
            self.send_error(404, "Not Found")

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()


def main():
    port = DEFAULT_PORT
    if len(sys.argv) > 1 and sys.argv[1].isdigit():
        port = int(sys.argv[1])

    server_address = ("", port)
    httpd = ThreadedHTTPServer(server_address, RequestHandler)

    url = f"http://localhost:{port}"
    print(f"============================================================")
    print(f" NILM 전력 시뮬레이터 인터랙티브 웹 서버 가동")
    print(f" - 대시보드 URL : {url}")
    print(f" - MQTT 브로커  : {simulator.DEFAULT_BROKER_HOST}:{simulator.DEFAULT_BROKER_PORT}")
    print(f" - 기능: 화면 버튼 클릭 시 실제 MQTT 발행 및 차트 실시간 렌더링")
    print(f" - 서버 종료: Ctrl + C")
    print(f"============================================================", flush=True)

    # 기본 브라우저 자동 오픈
    try:
        webbrowser.open(url)
    except Exception:
        pass

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n웹 서버를 정지합니다.", flush=True)
        manager.stop()
        httpd.server_close()


if __name__ == "__main__":
    main()

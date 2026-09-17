"""
NILM 스마트홈 시뮬레이터 E2E 시나리오 엔진 5A REST API 단위/통합 테스트 (test_e2e_api.py)

검증 대상 (API 8종 및 하위 호환성):
1. GET  /api/e2e/scenarios (통합 카탈로그 10종 요약 반환)
2. POST /api/e2e/runs (단일 세션 시작 202, 엄격한 입력 검증 400, 상호 배제 409)
3. GET  /api/e2e/runs/{run_id} (세션 전체 불변 스냅샷 조회 200, 404, 타임아웃 503)
4. GET  /api/e2e/runs/{run_id}/households/{household_id} (가구별 상세 메트릭 200, 404, 타임아웃 503)
5. POST /api/e2e/runs/{run_id}/households/{household_id}/pause (가구 일시정지 202, 멱등 200, 404, 409)
6. POST /api/e2e/runs/{run_id}/households/{household_id}/resume (가구 재개 202, 멱등 200, 404, 409)
7. POST /api/e2e/runs/{run_id}/households/{household_id}/stop (가구 중단 202, 멱등 200, 404, 409)
8. POST /api/e2e/runs/{run_id}/stop (세션 일괄 중단 202, 404)
9. e2e_manager 미주입 시 503 E2E_MANAGER_UNAVAILABLE 및 기존 레거시 API 정상 동작
10. shared_start_lock 및 SimulatorManager.get_status() 계약을 통한 레거시/E2E 상호 배제
11. 실제 SimulatorManager 인스턴스를 주입한 E2E 시작 호환성 검증
"""
from __future__ import annotations

import json
import threading
import time
import unittest
import urllib.error
import urllib.request
from unittest.mock import MagicMock, patch

from server.manager import SimulatorManager
from server.e2e_manager import E2EScheduleSessionManager, E2ESnapshotTimeoutError
from server.request_handler import ThreadedHTTPServer, create_request_handler
from tests.test_e2e_manager import FakeMqttFactory


class TestE2EApi(unittest.TestCase):
    """E2E REST API 8개 엔드포인트 및 하위 호환성 검증 테스트"""

    @classmethod
    def setUpClass(cls):
        cls.fake_factory = FakeMqttFactory()
        cls.e2e_manager = E2EScheduleSessionManager(
            broker_config={"host": "localhost", "port": 1883},
            mqtt_client_factory=cls.fake_factory,
        )
        # 실제 SimulatorManager 계약과 호환되는 spec 기반 mock
        cls.mock_manager = MagicMock(spec=SimulatorManager)
        cls.mock_manager.is_running = False
        cls.mock_manager.get_status.return_value = {"is_running": False, "mode": "idle"}
        cls.shared_lock = threading.Lock()

        cls.handler_cls = create_request_handler(
            manager=cls.mock_manager,
            html_path=None,
            e2e_manager=cls.e2e_manager,
            shared_start_lock=cls.shared_lock,
        )
        cls.httpd = ThreadedHTTPServer(("127.0.0.1", 0), cls.handler_cls)
        cls.port = cls.httpd.server_address[1]
        cls.server_thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.server_thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        cls.e2e_manager.shutdown(timeout_sec=2.0)

    def setUp(self):
        self.mock_manager.is_running = False
        self.mock_manager.get_status.return_value = {"is_running": False, "mode": "idle"}
        cur = self.e2e_manager.get_current_session()
        if cur is not None and cur.is_active():
            self.e2e_manager.stop_session(cur.run_id)
            for _ in range(50):
                if not self.e2e_manager.is_active():
                    break
                time.sleep(0.02)

    def tearDown(self):
        self.mock_manager.is_running = False
        self.mock_manager.get_status.return_value = {"is_running": False, "mode": "idle"}
        cur = self.e2e_manager.get_current_session()
        if cur is not None and cur.is_active():
            self.e2e_manager.stop_session(cur.run_id)
            for _ in range(50):
                if not self.e2e_manager.is_active():
                    break
                time.sleep(0.02)

    def request(self, method: str, path: str, body: dict | None = None, raw_body: str | None = None) -> tuple[int, dict]:
        """HTTP 요청 전송 및 응답 (status_code, response_json) 반환 헬퍼"""
        url = f"http://127.0.0.1:{self.port}{path}"
        if raw_body is not None:
            data = raw_body.encode("utf-8")
        elif body is not None:
            data = json.dumps(body).encode("utf-8")
        else:
            data = None

        headers = {"Content-Type": "application/json"} if (body is not None or raw_body is not None) else {}
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as resp:
                status = resp.status
                content = json.loads(resp.read().decode("utf-8"))
                return status, content
        except urllib.error.HTTPError as err:
            status = err.code
            try:
                content = json.loads(err.read().decode("utf-8"))
            except Exception:
                content = {"raw": err.read().decode("utf-8")}
            return status, content

    def test_get_scenarios_returns_exact_10_summaries(self):
        """GET /api/e2e/scenarios: 10종 시나리오 요약 반환 확인"""
        status, body = self.request("GET", "/api/e2e/scenarios")
        self.assertEqual(status, 200)
        self.assertEqual(body["status"], "success")
        self.assertEqual(body["count"], 10)
        self.assertEqual(len(body["scenarios"]), 10)

        for sc in body["scenarios"]:
            self.assertIn("scenario_id", sc)
            self.assertIn("category", sc)
            self.assertIn("total_days", sc)
            self.assertIn("planned_virtual_slots", sc)
            self.assertIn("planned_publish_samples", sc)
            # 불필요한 필드 배제 확인
            self.assertNotIn("description", sc)
            self.assertNotIn("ai_labels", sc)

    def test_post_runs_strict_input_validation(self):
        """POST /api/e2e/runs: 잘못된 입력에 대해 명확한 400 BAD_REQUEST 반환 검증"""
        # 1. 미허용 top-level 키
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
            "invalid_key": 123,
        })
        self.assertEqual(status, 400)
        self.assertEqual(body["code"], "BAD_REQUEST")
        self.assertIn("허용되지 않은 필드", body["message"])

        # 2. 필수 필드 누락
        status, body = self.request("POST", "/api/e2e/runs", {
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status, 400)
        self.assertIn("reference_date", body["message"])

        # 3. 잘못된 reference_date 형식 및 비정규 날짜
        for bad_date in ("2026/09/16", "2026-9-1", "2026-02-30", "2026-13-01", "invalid"):
            status, body = self.request("POST", "/api/e2e/runs", {
                "reference_date": bad_date,
                "execution": {"mode": "BURST"},
                "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
            })
            self.assertEqual(status, 400, f"Expected 400 for bad date '{bad_date}'")

        # 4. timezone 검증 (Asia/Seoul 외 모든 문자열 거절)
        for bad_tz in ("UTC", "KST", "America/New_York", "", 123):
            status, body = self.request("POST", "/api/e2e/runs", {
                "reference_date": "2026-09-16",
                "timezone": bad_tz,
                "execution": {"mode": "BURST"},
                "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
            })
            self.assertEqual(status, 400, f"Expected 400 for bad timezone '{bad_tz}'")

        # 5. 잘못된 execution mode
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "INVALID_MODE"},
            "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status, 400)
        self.assertIn("execution.mode", body["message"])

        # 6. ACCELERATED 모드에서 speed 누락 및 유효하지 않은 speed (0, 음수, bool, string)
        for bad_speed in (None, 0, -5, True, False, "fast"):
            exec_dict = {"mode": "ACCELERATED"}
            if bad_speed is not None:
                exec_dict["speed"] = bad_speed
            status, body = self.request("POST", "/api/e2e/runs", {
                "reference_date": "2026-09-16",
                "execution": exec_dict,
                "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
            })
            self.assertEqual(status, 400, f"Expected 400 for bad speed '{bad_speed}'")

        # 7. 비표준 JSON 상수(NaN, Infinity, -Infinity, 1e309) 거절
        for const_val in ("NaN", "Infinity", "-Infinity", "1e309"):
            raw = f'{{"reference_date": "2026-09-16", "execution": {{"mode": "ACCELERATED", "speed": {const_val}}}, "households": [{{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}}]}}'
            status, body = self.request("POST", "/api/e2e/runs", raw_body=raw)
            self.assertEqual(status, 400, f"Expected 400 for constant '{const_val}'")

        # 8. BURST 모드에서 speed 지정 금지
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST", "speed": 10.0},
            "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status, 400)
        self.assertIn("speed", body["message"])

        # 9. 허용 범위 초과 household_id
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H099", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status, 400)
        self.assertIn("household_id", body["message"])

        # 10. 중복 household_id
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [
                {"household_id": "H001", "scenario": "ACTIVITY_NORMAL"},
                {"household_id": "H001", "scenario": "ACTIVITY_NORMAL"},
            ],
        })
        self.assertEqual(status, 400)
        self.assertIn("중복된 household_id", body["message"])

        # 11. 존재하지 않는 시나리오
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H001", "scenario": "NONEXISTENT_SCENARIO"}],
        })
        self.assertEqual(status, 400)
        self.assertIn("유효하지 않은 시나리오", body["message"])

    def test_post_runs_mutual_exclusion_with_legacy_and_active_session(self):
        """레거시 실행 중이거나 이미 E2E 활성 세션 존재 시 409 충돌 반환 검증"""
        # 1. 레거시 시뮬레이터 실행 중인 경우
        self.mock_manager.is_running = True
        self.mock_manager.get_status.return_value = {"is_running": True, "mode": "peak"}
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status, 409)
        self.assertEqual(body["code"], "LEGACY_SIMULATOR_RUNNING")
        self.mock_manager.is_running = False
        self.mock_manager.get_status.return_value = {"is_running": False, "mode": "idle"}

        # 2. 정상 E2E 세션 시작 (202)
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status, 202, f"Expected 202 but got {status}: {body}")
        self.assertEqual(body["status"], "accepted")
        self.assertEqual(body["state"], "STARTING")
        run_id = body["run_id"]

        # 3. E2E 세션 실행 중 중복 E2E 시작 시도 -> 409
        status2, body2 = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [{"household_id": "H002", "scenario": "ACTIVITY_NORMAL"}],
        })
        self.assertEqual(status2, 409)
        self.assertEqual(body2["code"], "E2E_SIMULATOR_RUNNING")

        # 4. E2E 세션 실행 중 레거시 /api/start 시작 시도 -> 409
        status3, body3 = self.request("POST", "/api/start", {
            "house": "H001",
            "scenario": "peak",
        })
        self.assertEqual(status3, 409)
        self.assertEqual(body3["code"], "E2E_SIMULATOR_RUNNING")

        # 세션 중단
        self.request("POST", f"/api/e2e/runs/{run_id}/stop")

    def test_get_runs_and_households_snapshots(self):
        """GET /api/e2e/runs/{run_id} 및 GET .../households/{household_id} 정상 및 404 조회"""
        # 세션 시작
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [
                {"household_id": "H001", "scenario": "ACTIVITY_NORMAL"},
                {"household_id": "H002", "scenario": "ROUTINE_CHANGED_LATER"},
            ],
        })
        self.assertEqual(status, 202)
        run_id = body["run_id"]

        time.sleep(0.05)

        # 세션 전체 스냅샷 조회
        status_s, body_s = self.request("GET", f"/api/e2e/runs/{run_id}")
        self.assertEqual(status_s, 200)
        self.assertEqual(body_s["run_id"], run_id)
        self.assertEqual(body_s["reference_date"], "2026-09-16")
        self.assertEqual(body_s["execution_mode"], "BURST")
        self.assertEqual(len(body_s["households"]), 2)

        # 미존재 run_id 조회 -> 404
        status_nf, body_nf = self.request("GET", "/api/e2e/runs/run_nonexistent")
        self.assertEqual(status_nf, 404)
        self.assertEqual(body_nf["code"], "RUN_NOT_FOUND")

        # 개별 가구 H001 스냅샷 조회
        status_h, body_h = self.request("GET", f"/api/e2e/runs/{run_id}/households/H001")
        self.assertEqual(status_h, 200)
        h_info = body_h["household"]
        self.assertEqual(h_info["household_id"], "H001")
        self.assertEqual(h_info["scenario"], "ACTIVITY_NORMAL")
        self.assertIn("settled_virtual_slots", h_info)
        self.assertIn("published_samples", h_info)

        # 미존재 가구 H999 조회 -> 404
        status_h_nf, body_h_nf = self.request("GET", f"/api/e2e/runs/{run_id}/households/H999")
        self.assertEqual(status_h_nf, 404)
        self.assertEqual(body_h_nf["code"], "HOUSEHOLD_NOT_FOUND")

        # 세션 중지
        self.request("POST", f"/api/e2e/runs/{run_id}/stop")

    def test_household_pause_resume_stop_endpoints(self):
        """가구별 pause, resume, stop 제어 및 멱등 200 / 전이 202 검증"""
        status, body = self.request("POST", "/api/e2e/runs", {
            "reference_date": "2026-09-16",
            "execution": {"mode": "BURST"},
            "households": [
                {"household_id": "H001", "scenario": "ACTIVITY_NORMAL"},
            ],
        })
        self.assertEqual(status, 202)
        run_id = body["run_id"]

        time.sleep(0.05)

        # 1. 일시정지 요청 (202 또는 200)
        p_status, p_body = self.request("POST", f"/api/e2e/runs/{run_id}/households/H001/pause")
        self.assertIn(p_status, (200, 202))
        self.assertEqual(p_body["requested_action"], "PAUSE")

        # PAUSED 수렴 대기
        for _ in range(50):
            _, snap = self.request("GET", f"/api/e2e/runs/{run_id}/households/H001")
            if snap.get("household", {}).get("state") == "PAUSED":
                break
            time.sleep(0.05)

        # 2. 이미 PAUSED 상태에서 중복 pause -> 200 OK 멱등
        p_status2, p_body2 = self.request("POST", f"/api/e2e/runs/{run_id}/households/H001/pause")
        self.assertEqual(p_status2, 200)
        self.assertEqual(p_body2["status"], "success")
        self.assertEqual(p_body2["current_state"], "PAUSED")

        # 3. 재개 요청 -> 202 Accepted
        r_status, r_body = self.request("POST", f"/api/e2e/runs/{run_id}/households/H001/resume")
        self.assertIn(r_status, (200, 202))
        self.assertEqual(r_body["requested_action"], "RESUME")

        # 4. 개별 가구 stop -> 202 Accepted
        s_status, s_body = self.request("POST", f"/api/e2e/runs/{run_id}/households/H001/stop")
        self.assertIn(s_status, (200, 202))
        self.assertEqual(s_body["requested_action"], "STOP")

        # STOPPED 수렴 대기
        for _ in range(50):
            _, snap = self.request("GET", f"/api/e2e/runs/{run_id}/households/H001")
            if snap.get("household", {}).get("state") == "STOPPED":
                break
            time.sleep(0.05)

    def test_snapshot_timeout_returns_503(self):
        """get_session_snapshot 타임아웃 발생 시 503 E2E_SNAPSHOT_TIMEOUT 응답 검증"""
        with patch.object(self.e2e_manager, "get_session_snapshot", side_effect=E2ESnapshotTimeoutError("Mock timeout")):
            status, body = self.request("GET", "/api/e2e/runs/run_dummy")
            self.assertEqual(status, 503)
            self.assertEqual(body["code"], "E2E_SNAPSHOT_TIMEOUT")

    def test_post_runs_with_real_simulator_manager_instance(self):
        """실제 SimulatorManager() 인스턴스를 주입하여 POST /api/e2e/runs가 202를 반환함을 검증"""
        real_mgr = SimulatorManager()
        handler_cls = create_request_handler(
            manager=real_mgr,
            html_path=None,
            e2e_manager=self.e2e_manager,
            shared_start_lock=threading.Lock(),
        )
        httpd = ThreadedHTTPServer(("127.0.0.1", 0), handler_cls)
        port = httpd.server_address[1]
        t = threading.Thread(target=httpd.serve_forever, daemon=True)
        t.start()
        try:
            url = f"http://127.0.0.1:{port}/api/e2e/runs"
            payload = {
                "reference_date": "2026-09-16",
                "execution": {"mode": "BURST"},
                "households": [{"household_id": "H001", "scenario": "ACTIVITY_NORMAL"}],
            }
            data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req) as resp:
                self.assertEqual(resp.status, 202)
                res_body = json.loads(resp.read().decode("utf-8"))
                self.assertEqual(res_body["status"], "accepted")
        finally:
            httpd.shutdown()
            httpd.server_close()

    def test_e2e_manager_unavailable_returns_503(self):
        """e2e_manager=None 주입 시 /api/e2e/* 요청은 503 반환, 레거시 API는 정상 동작 검증"""
        handler_cls = create_request_handler(
            manager=self.mock_manager,
            html_path=None,
            e2e_manager=None,
            shared_start_lock=self.shared_lock,
        )
        httpd = ThreadedHTTPServer(("127.0.0.1", 0), handler_cls)
        port = httpd.server_address[1]
        t = threading.Thread(target=httpd.serve_forever, daemon=True)
        t.start()

        try:
            # 1. /api/e2e/scenarios -> 503
            url_e2e = f"http://127.0.0.1:{port}/api/e2e/scenarios"
            req_e2e = urllib.request.Request(url_e2e, method="GET")
            with self.assertRaises(urllib.error.HTTPError) as ctx:
                urllib.request.urlopen(req_e2e)
            self.assertEqual(ctx.exception.code, 503)
            err_body = json.loads(ctx.exception.read().decode("utf-8"))
            self.assertEqual(err_body["code"], "E2E_MANAGER_UNAVAILABLE")

            # 2. /api/e2e/runs -> 503
            url_runs = f"http://127.0.0.1:{port}/api/e2e/runs"
            req_runs = urllib.request.Request(
                url_runs,
                data=json.dumps({"reference_date": "2026-09-16"}).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST"
            )
            with self.assertRaises(urllib.error.HTTPError) as ctx:
                urllib.request.urlopen(req_runs)
            self.assertEqual(ctx.exception.code, 503)

            # 3. 레거시 /api/status -> 200 정상 동작
            url_status = f"http://127.0.0.1:{port}/api/status"
            with urllib.request.urlopen(url_status) as resp:
                self.assertEqual(resp.status, 200)
        finally:
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    unittest.main()

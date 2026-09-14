"""
NILM 스마트홈 전력 시뮬레이터 관리자 모듈

SimulatorManager 클래스를 통해 시뮬레이터 백그라운드 워커의 라이프사이클(start/stop/restart),
동시성 제어(3단계 락: lifecycle_lock, simulation_lock, lock), MQTT 실시간 발행 루프,
SSE 구독자 관리 및 브로드캐스트를 전담합니다.
"""

import asyncio
from datetime import datetime, timezone, timedelta
import os
import queue
import sys
import threading
import time

# Windows SelectorLoop 호환성 설정
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

# 상위 디렉터리(infrastructure/mqtt/simulator) import 경로 등록
PARENT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PARENT_DIR not in sys.path:
    sys.path.append(PARENT_DIR)

import aiomqtt
import scenarios
import simulator


class ModeConflictError(Exception):
    """시뮬레이터가 실행 중이 아니거나 manual 모드가 아닐 때 발생하는 예외"""
    pass


class SimulatorManager:
    def __init__(
        self,
        host: str | None = None,
        port: int | None = None,
        username: str | None = None,
        password: str | None = None,
        tls_enabled: bool | None = None,
        ca_file: str | None = None,
    ):
        self.lock = threading.RLock()
        # HTTP 요청 스레드에서 동시에 들어오는 start/stop/set_device를 직렬화한다.
        self.lifecycle_lock = threading.Lock()
        # 시뮬레이션 상태 변경(가전 ON/OFF, 초기화) 및 물리 계측을 보호하는 락
        self.simulation_lock = threading.Lock()
        self.is_running = False
        self.current_mode = "idle"  # "idle", "peak", "routine_missed", "random", "manual"
        self.simulation_date = None
        self.resolved_start_time = None
        # 실행별로 별도 Event를 생성해 종료된 워커가 다시 살아나는 것을 막는다.
        self.stop_event = None
        self.worker_thread = None
        self.subscribers = []  # SSE 큐 목록
        self.last_metrics = None
        self.cycle_count = 0

        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.tls_enabled = tls_enabled
        self.ca_file = ca_file

    def get_connection_config(self) -> dict:
        """현재 설정된 인자 및 환경변수를 기반으로 연결 파라미터를 결정합니다."""
        from engine.tls import resolve_mqtt_config
        return resolve_mqtt_config(
            host=self.host,
            port=self.port,
            username=self.username,
            password=self.password,
            tls_enabled=self.tls_enabled,
            ca_file=self.ca_file,
        )

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

    def start(self, scenario: str = "peak", house: str = "H001", simulation_date: str | None = None) -> dict:
        with self.lifecycle_lock:
            # 1. TLS 설정 사전 동기 검증 (CA 누락/미존재/권한 오류 시 런타임 start 호출에서 즉각 예외 발생)
            from engine.tls import get_mqtt_tls_context
            cfg = self.get_connection_config()
            tls_context = get_mqtt_tls_context(tls_enabled=cfg["tls_enabled"], ca_file=cfg["ca_file"])

            # 2. 가상 시작 시각 결정 (scenarios.resolve_simulation_start_time 순수 함수 사용)
            base_dt = scenarios.resolve_simulation_start_time(
                scenario,
                simulation_date=simulation_date,
                routine_default_time="08:10:01"
            )
            resolved_start_iso = scenarios.format_iso_utc(base_dt) if base_dt else None

            if not self._stop_and_join():
                raise RuntimeError("기존 시뮬레이터 워커가 종료되지 않았습니다.")

            # 이전 워커가 완전히 종료된 후, 새 워커를 시작하기 전에 simulation_lock 안에서 상태 초기화
            with self.simulation_lock:
                simulator.init_simulation_states([house])

            stop_event = threading.Event()
            worker_thread = threading.Thread(
                target=self._run_async_worker,
                args=(scenario, house, stop_event, cfg, tls_context, simulation_date, base_dt),
                daemon=True
            )

            with self.lock:
                self.stop_event = stop_event
                self.worker_thread = worker_thread
                self.is_running = True
                self.current_mode = scenario
                self.cycle_count = 0
                self.simulation_date = simulation_date
                self.resolved_start_time = resolved_start_iso

            worker_thread.start()
            return {
                "status": "started",
                "scenario": scenario,
                "house": house,
                "simulation_date": simulation_date,
                "resolved_start_time": resolved_start_iso
            }

    def set_device(self, house: str, device: str, enabled: bool) -> dict:
        """가전 상태를 수동 변경한다. manual 모드에서만 허용되며, lifecycle_lock 및 simulation_lock으로 보호된다."""
        with self.lifecycle_lock:
            with self.simulation_lock:
                with self.lock:
                    if not self.is_running:
                        raise ModeConflictError("시뮬레이터가 정지 상태(idle)입니다. 먼저 manual 모드로 시작하세요.")
                    if self.current_mode != "manual":
                        raise ModeConflictError(f"가전 수동 제어는 'manual' 모드에서만 가능합니다. (현재 모드: {self.current_mode})")

                return simulator.set_manual_device_state(house, device, enabled)

    def stop(self):
        with self.lifecycle_lock:
            return self._stop_and_join()

    def _stop_and_join(self) -> bool:
        """현재 워커에 종료를 요청하고 완전히 종료될 때까지 기다린다."""
        with self.lock:
            worker_thread = self.worker_thread
            stop_event = self.stop_event

            if stop_event is not None:
                stop_event.set()

        # worker의 finally도 self.lock을 사용하므로 lock 밖에서 기다린다.
        if (
            worker_thread is not None
            and worker_thread.is_alive()
            and worker_thread is not threading.current_thread()
        ):
            worker_thread.join(timeout=10.0)

        if worker_thread is not None and worker_thread.is_alive():
            return False

        with self.lock:
            # 자연 종료 직후 다른 상태가 등록된 경우 이를 덮어쓰지 않는다.
            if self.worker_thread is worker_thread:
                self.worker_thread = None
                self.stop_event = None
                self.is_running = False
                self.current_mode = "idle"
                self.simulation_date = None
                self.resolved_start_time = None

        return True

    def _run_async_worker(
        self,
        scenario: str,
        house: str,
        stop_event: threading.Event,
        cfg: dict,
        tls_context,
        simulation_date: str | None = None,
        base_dt: datetime | None = None
    ):
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(
                self._worker_loop(scenario, house, stop_event, cfg, tls_context, simulation_date, base_dt)
            )
        except Exception as err:
            print(f"[WebSimulator] 워커 오류: {err}", flush=True)
        finally:
            loop.close()
            with self.lock:
                # 과거 워커가 새 워커의 실행 상태를 덮어쓰지 않도록 보호한다.
                if self.worker_thread is threading.current_thread():
                    self.worker_thread = None
                    self.stop_event = None
                    self.is_running = False
                    self.current_mode = "idle"
                    self.simulation_date = None
                    self.resolved_start_time = None

    async def _worker_loop(
        self,
        scenario: str,
        house: str,
        stop_event: threading.Event,
        cfg: dict,
        tls_context,
        simulation_date: str | None = None,
        base_dt: datetime | None = None
    ):
        is_peak = (scenario == "peak")
        is_routine_missed = (scenario == "routine_missed")
        is_manual = (scenario == "manual")
        allow_random = not (is_peak or is_routine_missed or is_manual)

        if is_peak:
            target_count = 60
        elif is_routine_missed:
            target_count = 300  # AI 299초 슬라이딩 윈도우 충족
        else:
            target_count = 999999
        interval = 1.0

        host = cfg["host"]
        port = cfg["port"]
        user = cfg["username"]
        password = cfg["password"]
        tls_enabled = cfg["tls_enabled"]
        ca_file = cfg["ca_file"]

        tls_desc = f" (TLS ON | CA: {ca_file})" if tls_enabled else " (평문)"
        print(f"[WebSimulator] MQTT 브로커({host}:{port}{tls_desc}) 연결 중...", flush=True)

        async with aiomqtt.Client(
            hostname=host,
            port=port,
            username=user,
            password=password,
            tls_context=tls_context,
            keepalive=60,
            timeout=5
        ) as client:
            print(f"[WebSimulator] MQTT 연결 성공 실시간 발행 시작 (시나리오: {scenario}, 대상: {house})", flush=True)

            cycle = 0
            while not stop_event.is_set():
                cycle_start = time.time()
                if base_dt is not None:
                    sim_dt = base_dt + timedelta(seconds=cycle)
                    now_iso = scenarios.format_iso_utc(sim_dt)
                    sim_kst = sim_dt.astimezone(scenarios.KST)
                    sim_time_kst = sim_kst.strftime("%H:%M:%S")
                    sim_date_kst = sim_kst.strftime("%Y-%m-%d")
                    sim_datetime_kst = sim_kst.strftime("%Y-%m-%d %H:%M:%S KST")
                else:
                    curr_utc = datetime.now(timezone.utc)
                    now_iso = scenarios.format_iso_utc(curr_utc)
                    curr_kst = curr_utc.astimezone(scenarios.KST)
                    sim_time_kst = curr_kst.strftime("%H:%M:%S")
                    sim_date_kst = curr_kst.strftime("%Y-%m-%d")
                    sim_datetime_kst = curr_kst.strftime("%Y-%m-%d %H:%M:%S KST")

                cycle += 1
                self.cycle_count = cycle

                # 1. 시나리오 주입, 물리 계측, 디바이스 스냅샷 생성을 simulation_lock 하에서 원자적으로 처리
                with self.simulation_lock:
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

                    # 물리 계측 (tick당 정확히 1회 호출)
                    metrics = simulator.calculate_main_panel_metrics(house, allow_random=allow_random)

                    # 영문 device key 기반 devices SSE 스냅샷 생성 (enabled: state != "OFF")
                    devices_snapshot = {}
                    house_devices = simulator.device_states.get(house, {})
                    for dev_name in simulator.DEVICE_PROFILES:
                        dev_st = house_devices.get(dev_name, {})
                        st_state = dev_st.get("state", "OFF")
                        devices_snapshot[dev_name] = {
                            "state": st_state,
                            "enabled": (st_state != "OFF"),
                            "manualHold": bool(dev_st.get("manual_hold", False))
                        }

                # 2. simulation_lock 해제 후 MQTT 발행 (네트워크 I/O 중 lock 미보유)
                res = await simulator.publish_house_power(
                    client=client,
                    house=house,
                    now_iso=now_iso,
                    qos=1,
                    allow_random=allow_random,
                    metrics=metrics
                )

                # 3. 실시간 UI 동기화용 패킷 생성 및 SSE 브로드캐스트
                broadcast_data = {
                    "sec": cycle,
                    "now_iso": now_iso,
                    "simTimeKst": sim_time_kst,
                    "simDateKst": sim_date_kst,
                    "simDateTimeKst": sim_datetime_kst,
                    "house": house,
                    "totalP": metrics["active_power"],
                    "totalQ": metrics["reactive_power"],
                    "apparentS": metrics["apparent_power"],
                    "pf": metrics["power_factor"],
                    "voltage": metrics["voltage"],
                    "currentA": metrics["current"],
                    "activeNames": metrics["active_devices"],
                    "eventNoticeText": event_desc,
                    "mode": scenario,
                    "devices": devices_snapshot
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
                    print(f"[WebSimulator] (T+{cycle:02d}s | {sim_datetime_kst}) {house} 전력: {metrics['active_power']:7.1f} W | {status_tag}{notice_str}", flush=True)
                elif is_routine_missed:
                    status_tag, notice = scenarios.RoutineMissedScenario.get_cycle_status(cycle)
                    notice_str = f" <== [{event_desc}]" if event_desc else notice
                    print(f"[WebSimulator] (T+{cycle:03d}s | {sim_datetime_kst}) {house} 전력: {metrics['active_power']:5.1f} W | {status_tag}{notice_str}", flush=True)
                elif is_manual:
                    act_str = ", ".join(metrics["active_devices"]) if metrics["active_devices"] else "대기(가전 OFF)"
                    print(f"[WebSimulator] (T+{cycle:02d}s | {sim_datetime_kst}) {house} 전력: {metrics['active_power']:7.1f} W | 수동 제어 모드 [{act_str}]", flush=True)
                else:
                    status_tag = "가전 가동 중" if metrics["active_power"] >= 500.0 else "대기"
                    print(f"[WebSimulator] (T+{cycle:02d}s | {sim_datetime_kst}) {house} 전력: {metrics['active_power']:7.1f} W | {status_tag}", flush=True)

                if target_count > 0 and cycle >= target_count:
                    print(f"[WebSimulator] 목표 사이클({target_count}회) 완주, 자동 정지합니다.", flush=True)
                    break

                elapsed = time.time() - cycle_start
                sleep_time = max(0.0, interval - elapsed)
                end_sleep = time.time() + sleep_time
                while time.time() < end_sleep and not stop_event.is_set():
                    await asyncio.sleep(min(0.05, max(0.001, end_sleep - time.time())))

import sys
import json
import math
import random
import asyncio
from datetime import datetime, timezone
import aiomqtt

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
if sys.stderr and hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Windows 환경 호환성 설정 (aiomqtt 소켓 처리를 위해 SelectorEventLoop 적용)
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

# ==========================================
# 1. 브로커 접속 및 시뮬레이션 설정
# ==========================================
BROKER_HOST = "localhost"
BROKER_PORT = 1883
BROKER_USER = "simulator_user"
BROKER_PASS = "test1234"

# 시뮬레이션 대상 가구 목록 (H001 ~ H010 총 10개 가구)
HOUSES = [f"H{i:03d}" for i in range(1, 11)]

# AI 모델(TCN/Seq2Point) 학습 기준 6대 수동 타겟 가전 프로파일 및 파형 특성 정의
# - AI Hub 71685 데이터셋 실측 중앙값 및 물리 특성 반영:
#   1. 전기포트: 순수 저항성 히터 (실측 1,657W, PF 0.98~1.00, 단일 구형파)
#   2. 인덕션(전기레인지): 인버터 유도 가열 (실측 1,463W, PF 0.90~0.95, 서모스탯 듀티 사이클)
#   3. 전기다리미: 전열선 온도 제어 (실측 1,389W, PF 0.98~1.00, 바이메탈 듀티 사이클)
#   4. 전자레인지: 마그네트론/인버터 (실측 941W, PF 0.88~0.94, 기동 돌입 피크)
#   5. 헤어드라이기: 열선+팬 모터 (실측 934W, PF 0.94~0.98, 기동 돌입 피크)
#   6. 진공 청소기(유선): 고속 직권 모터 (실측 819W, PF 0.75~0.85, 강한 모터 돌입 피크)
DEVICE_PROFILES = {
    "kettle": {
        "name_ko": "전기포트",
        "type": "single_block",         # 단순 단일 구형파 블록
        "nominal_w": (1500.0, 1800.0),  # 정상 가동 전력 (EDA 실측 중앙: 1657W)
        "standby_w": (0.0, 0.5),
        "pf_nominal": (0.98, 1.00),     # 저항성
        "inrush_factor": 1.05,          # 돌입 배율
        "inrush_sec": 1,                # 돌입 지속 시간 (초)
        "session_sec": (60, 180),       # 1~3분 가동
        "turn_on_prob": 0.004,          # 1초당 켜짐 발생 확률
    },
    "induction": {
        "name_ko": "인덕션(전기레인지)",
        "type": "duty_cycle",           # 서모스탯 듀티 사이클 반복
        "nominal_w": (1300.0, 1750.0),  # EDA 실측 중앙: 1463W
        "standby_w": (1.0, 3.0),
        "pf_nominal": (0.91, 0.95),     # 고주파 인버터
        "inrush_factor": 1.30,          # 인버터 기동 피크
        "inrush_sec": 2,
        "session_sec": (120, 360),      # 조리 세션 2~6분
        "duty_on_sec": (20, 35),        # 듀티 사이클 가열 시간
        "duty_off_sec": (10, 20),       # 듀티 사이클 휴지 시간
        "turn_on_prob": 0.003,
    },
    "iron": {
        "name_ko": "전기다리미",
        "type": "duty_cycle",           # 바이메탈 온도제어 듀티 사이클
        "nominal_w": (1250.0, 1550.0),  # EDA 실측 중앙: 1389W
        "standby_w": (0.0, 0.5),
        "pf_nominal": (0.98, 1.00),     # 저항성 전열
        "inrush_factor": 1.05,
        "inrush_sec": 1,
        "session_sec": (120, 300),      # 다림질 세션 2~5분
        "duty_on_sec": (15, 25),        # 가열 주기 15~25초 (실측 지속 중앙 18초)
        "duty_off_sec": (10, 20),
        "turn_on_prob": 0.002,
    },
    "microwave": {
        "name_ko": "전자레인지",
        "type": "single_block",
        "nominal_w": (850.0, 1150.0),   # EDA 실측 중앙: 941W
        "standby_w": (1.0, 2.5),
        "pf_nominal": (0.88, 0.94),     # 마그네트론
        "inrush_factor": 1.40,          # 트랜스/마그네트론 돌입 피크
        "inrush_sec": 2,
        "session_sec": (30, 120),       # 30초~2분
        "turn_on_prob": 0.005,
    },
    "hair_dryer": {
        "name_ko": "헤어드라이기",
        "type": "single_block",
        "nominal_w": (800.0, 1150.0),   # EDA 실측 중앙: 934W
        "standby_w": (0.0, 0.0),
        "pf_nominal": (0.94, 0.98),     # 열선 + 모터
        "inrush_factor": 1.35,          # 모터 돌입 피크
        "inrush_sec": 2,
        "session_sec": (45, 120),
        "turn_on_prob": 0.004,
    },
    "vacuum_cleaner": {
        "name_ko": "진공 청소기(유선)",
        "type": "single_block",
        "nominal_w": (700.0, 1000.0),   # EDA 실측 중앙: 819W
        "standby_w": (0.0, 0.5),
        "pf_nominal": (0.75, 0.85),     # 유도성 모터
        "inrush_factor": 1.50,          # 고속 직권모터 강한 돌입 피크
        "inrush_sec": 2,
        "session_sec": (60, 200),
        "turn_on_prob": 0.003,
    }
}

# ==========================================
# 2. 가구별 연속 환경 및 가전 상태 머신 관리
# ==========================================
# 1) 가구별 시계열 환경 상태 (전압 AR-1 드리프트, 기본 대기전력, 냉장고 컴프레서 주기)
house_states = {}
for house in HOUSES:
    house_states[house] = {
        "voltage": round(random.gauss(220.0, 1.2), 1),
        "base_nominal_w": random.uniform(40.0, 65.0),     # 상시 기저부하
        "base_current_w": random.uniform(45.0, 60.0),
        "fridge_active": random.random() < 0.4,            # 냉장고 컴프레서 초기 가동 여부
        "fridge_remaining_sec": random.randint(300, 1200), # 컴프레서 가동/정지 주기 (5~20분)
        "fridge_nominal_w": random.uniform(70.0, 100.0),   # 냉장고 컴프레서 소비 전력
        "fridge_pf": random.uniform(0.76, 0.82),           # 냉장고 모터 지상 역률
    }

# 2) 가구별 가전 상태 머신 객체
device_states = {}
for house in HOUSES:
    device_states[house] = {}
    for device, profile in DEVICE_PROFILES.items():
        device_states[house][device] = {
            "state": "OFF",           # "OFF", "STARTING", "RUNNING"
            "session_remaining": 0,   # 전체 세션 잔여 시간 (초)
            "inrush_remaining": 0,    # 돌입전류 잔여 시간 (1~2초)
            "nominal_w": 0.0,         # 이번 세션의 정격 전력
            "nominal_pf": 0.0,        # 이번 세션의 정격 역률
            "is_heating": False,      # 듀티 사이클(인덕션, 다리미) 가열 여부
            "duty_remaining": 0       # 듀티 주기 잔여 시간
        }

def update_house_environment(house: str) -> tuple[float, float, float]:
    """
    가구별 전압(V)의 완만한 시계열 드리프트(AR-1) 및 냉장고 컴프레서 주기 반영
    반환: (voltage, base_p, base_q)
    """
    env = house_states[house]

    # 1. 전압 AR(1) 완만 드리프트 (220V 기준 완만하게 변동)
    env["voltage"] = 0.98 * env["voltage"] + 0.02 * 220.0 + random.gauss(0, 0.12)
    voltage = round(max(212.0, min(228.0, env["voltage"])), 1)

    # 2. 상시 대기전력의 완만한 변동 (Random Walk)
    env["base_current_w"] = 0.96 * env["base_current_w"] + 0.04 * env["base_nominal_w"] + random.gauss(0, 0.2)
    base_p = max(20.0, env["base_current_w"])
    base_pf = 0.92

    # 3. 냉장고 컴프레서 주기적 가동/정지 (자동 가전 주기성)
    env["fridge_remaining_sec"] -= 1
    if env["fridge_remaining_sec"] <= 0:
        env["fridge_active"] = not env["fridge_active"]
        # 가동 시간: 15~25분(900~1500초), 정지 시간: 20~35분(1200~2100초)
        env["fridge_remaining_sec"] = random.randint(900, 1500) if env["fridge_active"] else random.randint(1200, 2100)

    fridge_p = 0.0
    fridge_q = 0.0
    if env["fridge_active"]:
        fridge_p = env["fridge_nominal_w"] + random.gauss(0, 0.8)
        fridge_pf = env["fridge_pf"]
        fridge_q = fridge_p * math.sqrt(1.0 - fridge_pf * fridge_pf) / fridge_pf

    base_q = base_p * math.sqrt(1.0 - base_pf * base_pf) / base_pf + fridge_q
    total_base_p = base_p + fridge_p

    return voltage, total_base_p, base_q

def update_and_generate_device_load(house: str, device: str) -> tuple[float, float, bool]:
    """
    가전 상태 머신 전이 및 파형 특성(돌입전류 오버슈트, 듀티사이클) 계산
    반환: (P, Q, is_active)
    """
    profile = DEVICE_PROFILES[device]
    state = device_states[house][device]

    if state["state"] == "OFF":
        # 가전 켜짐 트리거 확인
        if random.random() < profile["turn_on_prob"]:
            dur_min, dur_max = profile["session_sec"]
            state["session_remaining"] = random.randint(dur_min, dur_max)
            state["nominal_w"] = random.uniform(*profile["nominal_w"])
            state["nominal_pf"] = random.uniform(*profile["pf_nominal"])
            state["state"] = "STARTING"
            state["inrush_remaining"] = profile["inrush_sec"]

            if profile["type"] == "duty_cycle":
                state["is_heating"] = True
                state["duty_remaining"] = random.randint(*profile["duty_on_sec"])

    p = 0.0
    pf = 0.95
    is_active = False

    if state["state"] == "STARTING":
        is_active = True
        # 1. 돌입전류(Inrush / Overshoot) 단계: 피크 전력 + 순간 역률 저하
        p = state["nominal_w"] * profile["inrush_factor"] + random.gauss(0, 2.0)
        pf = max(0.40, state["nominal_pf"] - 0.08)

        state["inrush_remaining"] -= 1
        state["session_remaining"] -= 1

        if state["inrush_remaining"] <= 0:
            state["state"] = "RUNNING"
        if state["session_remaining"] <= 0:
            state["state"] = "OFF"

    elif state["state"] == "RUNNING":
        state["session_remaining"] -= 1
        if state["session_remaining"] <= 0:
            state["state"] = "OFF"
        else:
            if profile["type"] == "single_block":
                # 2-A. 단일 구형파 블록: 정격 전력 유지 + 미세 변동
                is_active = True
                p = state["nominal_w"] + random.gauss(0, 1.2)
                pf = state["nominal_pf"] + random.gauss(0, 0.003)

            elif profile["type"] == "duty_cycle":
                # 2-B. 듀티 사이클: 서모스탯 제어 (가열 ON ↔ 휴지 OFF)
                state["duty_remaining"] -= 1
                if state["is_heating"]:
                    is_active = True
                    p = state["nominal_w"] + random.gauss(0, 1.5)
                    pf = state["nominal_pf"]
                    if state["duty_remaining"] <= 0:
                        state["is_heating"] = False
                        state["duty_remaining"] = random.randint(*profile["duty_off_sec"])
                else:
                    # 휴지 구간: 내부 대기전력만 소모
                    is_active = True  # 세션 가동 중
                    p = profile["standby_w"][1] + random.uniform(5.0, 15.0)
                    pf = 0.92
                    if state["duty_remaining"] <= 0:
                        state["is_heating"] = True
                        state["duty_remaining"] = random.randint(*profile["duty_on_sec"])

    if state["state"] == "OFF":
        # 3. 완전 대기 상태
        low, high = profile["standby_w"]
        p = random.uniform(low, high) if high > 0 else 0.0
        pf = 0.95

    p = max(0.0, p)
    pf = max(0.1, min(0.999, pf))
    q = p * math.sqrt(1.0 - pf * pf) / pf if p > 0.1 else 0.0

    return p, q, is_active

def calculate_main_panel_metrics(house: str) -> dict:
    """가구 내 메인 분전반의 다변량 교류 전력 특성(P, Q, S, PF, V, I) 계산"""
    # 1. 완만한 전압 및 기저 부하 획득
    voltage, base_p, base_q = update_house_environment(house)

    total_p = base_p
    total_q = base_q
    active_devices = []

    # 2. 6대 수동 가전 부하 합산 및 파형 생성
    for device in DEVICE_PROFILES:
        dev_p, dev_q, is_active = update_and_generate_device_load(house, device)
        total_p += dev_p
        total_q += dev_q
        if is_active:
            active_devices.append(DEVICE_PROFILES[device]["name_ko"])

    # 3. 분전반 메인 계측기 미세 센서 노이즈
    total_p += random.gauss(0, 0.8)
    total_q += random.gauss(0, 0.8)
    total_p = max(0.0, total_p)
    total_q = max(0.0, total_q)

    # 4. 피상전력 S (VA) = sqrt(P^2 + Q^2)
    apparent_power = math.sqrt(total_p * total_p + total_q * total_q)

    # 5. 역률 PF = P / S
    if apparent_power > 1e-3:
        power_factor = total_p / apparent_power
    else:
        power_factor = 1.0
    power_factor = max(0.0, min(1.0, power_factor))

    # 6. 전류 I (A) = S / V
    current = apparent_power / voltage

    return {
        "active_power": round(total_p, 2),
        "reactive_power": round(total_q, 2),
        "apparent_power": round(apparent_power, 2),
        "power_factor": round(power_factor, 3),
        "voltage": voltage,
        "current": round(current, 3),
        "active_devices": active_devices
    }

# ==========================================
# 3. 비동기 MQTT 전송 루프
# ==========================================
async def publish_house_power(client: aiomqtt.Client, house: str, now_iso: str) -> dict:
    """단일 가구의 메인 분전반 전력 계측 데이터(4특징 및 물리 특성) 발행"""
    metrics = calculate_main_panel_metrics(house)

    payload = {
        "house": house,
        "device": "main",
        "ts": now_iso,
        "power_w": metrics["active_power"],         # 기존 bridge.py / loader.py 하위 호환 유지
        "active_power": metrics["active_power"],     # AI 모델 입력 피처 1 (W)
        "reactive_power": metrics["reactive_power"], # AI 모델 입력 피처 2 (var)
        "power_factor": metrics["power_factor"],     # AI 모델 입력 피처 3 (역률)
        "current": metrics["current"],               # AI 모델 입력 피처 4 (A)
        "voltage": metrics["voltage"],               # 전압 (V)
        "apparent_power": metrics["apparent_power"]  # 피상전력 (VA)
    }

    topic = f"v1/power/sim/{house}/main"
    await client.publish(topic, json.dumps(payload), qos=1)
    return {"house": house, "power": metrics["active_power"], "devices": metrics["active_devices"]}

async def run_simulator():
    """시뮬레이터 메인 비동기 실행 루프"""
    print(f"Mosquitto 브로커 ({BROKER_HOST}:{BROKER_PORT}) 연결 중...", flush=True)
    
    async with aiomqtt.Client(
        hostname=BROKER_HOST,
        port=BROKER_PORT,
        username=BROKER_USER,
        password=BROKER_PASS,
        keepalive=60,
        timeout=5
    ) as client:
        print(f" 총 {len(HOUSES)}개 가구의 메인 분전반 전력 데이터 전송을 시작합니다 (종료: Ctrl+C)", flush=True)

        while True:
            # ISO 8601 UTC 타임스탬프 (밀리초 포함: YYYY-MM-DDTHH:mm:ss.sssZ)
            now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"

            # 10개 가구 데이터 동시 비동기 발행 (Concurrent Publish)
            results = await asyncio.gather(*(publish_house_power(client, house, now_iso) for house in HOUSES))

            # 가동 중인 타겟 가전이 있는 가구 간략히 출력
            active_info = [f"{r['house']}:{','.join(r['devices'])}" for r in results if r["devices"]]
            active_summary = f" [가전 ON: {'; '.join(active_info)}]" if active_info else ""

            print(f"[{now_iso}] {len(HOUSES)}개 가구 메인 분전반(main) 데이터 발행 완료{active_summary}", flush=True)
            await asyncio.sleep(1)  # 1초 논블로킹 대기 (주기 조절 가능)

def main():
    try:
        asyncio.run(run_simulator())
    except (KeyboardInterrupt, asyncio.CancelledError):
        print("\n시뮬레이터를 정지합니다.", flush=True)
    except aiomqtt.MqttError as error:
        print(f"\nMQTT 연결 오류 발생: {error}", flush=True)
    except Exception as error:
        print(f"\n오류 발생: {error}", flush=True)

if __name__ == "__main__":
    main()



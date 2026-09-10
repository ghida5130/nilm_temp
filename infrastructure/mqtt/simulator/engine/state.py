"""
NILM 스마트홈 전력 시뮬레이터 가구 및 가전 상태 머신 관리 모듈

house_states와 device_states는 단일 공유 dict 객체로 유지되며,
초기화 시 clear()를 통해 내부 항목만 갱신함으로써
외부 모듈(visualize_waveform.py, server/manager.py 등)의 직접 참조 동일성(Identity)을 보장합니다.
"""

import random

from .profiles import DEVICE_PROFILES

try:
    from scenarios import create_initial_house_environment
except ModuleNotFoundError:
    # engine 패키지가 상대 패키지 내부에서 독립 임포트될 경우를 위한 fallback
    from ..scenarios import create_initial_house_environment

# ==========================================
# 가구별 연속 환경 및 가전 상태 머신 공유 dict
# ==========================================
house_states = {}
device_states = {}


def init_simulation_states(houses: list[str]):
    """가구 목록에 맞추어 시계열 환경 및 가전 상태 머신 초기화"""
    house_states.clear()
    device_states.clear()

    for house in houses:
        # 1) 가구별 시계열 대기전력 환경 상태 (전압 AR-1 드리프트, 기본 대기전력, 냉장고 주기)
        house_states[house] = create_initial_house_environment()

        # 2) 가구별 가전 상태 머신 객체
        device_states[house] = {}
        for device, profile in DEVICE_PROFILES.items():
            device_states[house][device] = {
                "state": "OFF",           # "OFF", "STARTING", "RUNNING"
                "manual_hold": False,     # 수동 조작 유지 여부 (True 시 세션 만료로 자동 OFF 차단)
                "session_remaining": 0,   # 전체 세션 잔여 시간 (초)
                "inrush_remaining": 0,    # 돌입전류 잔여 시간 (1~2초)
                "nominal_w": 0.0,         # 이번 세션의 정격 전력
                "nominal_pf": 0.0,        # 이번 세션의 정격 역률
                "is_heating": False,      # 듀티 사이클(인덕션, 다리미) 가열 여부
                "duty_remaining": 0       # 듀티 주기 잔여 시간
            }


def set_manual_device_state(house: str, device: str, enabled: bool) -> dict:
    """
    단일 가전의 수동 ON/OFF 상태를 멱등성(Idempotency) 있게 갱신하고 스냅샷을 반환.
    - enabled=True & OFF: STARTING으로 진입하며 물리 프로파일 범위 내 정격 및 돌입/듀티 파라미터 초기화, manual_hold=True
    - enabled=True & (STARTING or RUNNING): 현재 물리 진행 상태(돌입/정격)를 보존하고 manual_hold=True만 갱신
    - enabled=False: 상태와 무관하게 즉시 OFF로 전환하고 잔여 시간 및 정격 파라미터 0 초기화, manual_hold=False
    """
    if house not in device_states:
        raise ValueError(f"존재하지 않는 가구 ID입니다: {house}")
    if device not in DEVICE_PROFILES or device not in device_states[house]:
        raise ValueError(f"지원하지 않는 가전 ID입니다: {device}")
    if type(enabled) is not bool:
        raise ValueError(f"enabled는 정확히 bool 타입이어야 합니다: {type(enabled).__name__} ({enabled!r})")

    profile = DEVICE_PROFILES[device]
    state = device_states[house][device]

    if enabled:
        if state["state"] == "OFF":
            state["state"] = "STARTING"
            state["manual_hold"] = True
            state["nominal_w"] = random.uniform(*profile["nominal_w"])
            state["nominal_pf"] = random.uniform(*profile["pf_nominal"])
            state["inrush_remaining"] = profile["inrush_sec"]
            state["session_remaining"] = random.randint(*profile["session_sec"])
            if profile["type"] == "duty_cycle":
                state["is_heating"] = True
                state["duty_remaining"] = random.randint(*profile["duty_on_sec"])
            else:
                state["is_heating"] = False
                state["duty_remaining"] = 0
        else:
            # 이미 STARTING 또는 RUNNING인 경우 돌입/정격을 리셋하지 않고 manual_hold만 보장
            state["manual_hold"] = True
    else:
        state["state"] = "OFF"
        state["manual_hold"] = False
        state["session_remaining"] = 0
        state["inrush_remaining"] = 0
        state["nominal_w"] = 0.0
        state["nominal_pf"] = 0.0
        state["is_heating"] = False
        state["duty_remaining"] = 0

    return dict(state)

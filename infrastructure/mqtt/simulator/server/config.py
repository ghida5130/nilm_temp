"""
NILM 스마트홈 전력 시뮬레이터 웹 서버 설정 모듈

웹 서버에서 사용하는 기본 포트 및 허용 시나리오 목록 등 고정 설정을 정의합니다.
MQTT 브로커 관련 설정은 simulator.py의 환경변수 기반 설정을 그대로 사용합니다.
"""

DEFAULT_PORT = 8085
ALLOWED_SCENARIOS = {"peak", "routine_missed", "random", "manual", "normal_routine"}
MIN_INTERVAL = 0.1
MAX_INTERVAL = 10.0
DEFAULT_INTERVAL = 1.0


def validate_interval(val) -> float:
    """
    발행 주기(interval) 유효성 공통 검증 함수.

    허용 범위: 0.1초 이상 10.0초 이하
    거절 대상:
    - None / null
    - bool (Python에서 bool은 int의 서브클래스이므로 isinstance(val, bool) 검사 필요)
    - str 및 비숫자 타입
    - 0, 음수
    - NaN, Infinity 등 비유한 숫자
    - 0.1 미만 또는 10.0 초과
    """
    if val is None:
        raise ValueError("interval 필드는 필수입니다.")
    if isinstance(val, bool):
        raise ValueError("interval은 boolean 타입일 수 없습니다.")
    if not isinstance(val, (int, float)):
        raise ValueError(f"interval은 숫자(int 또는 float)여야 합니다. (전달된 타입: {type(val).__name__})")

    val_float = float(val)
    import math
    if not math.isfinite(val_float):
        raise ValueError("interval은 유한한(finite) 실수여야 합니다.")
    if val_float < MIN_INTERVAL or val_float > MAX_INTERVAL:
        raise ValueError(f"interval은 {MIN_INTERVAL}초 이상 {MAX_INTERVAL}초 이하의 값이어야 합니다. (전달된 값: {val_float})")

    return val_float

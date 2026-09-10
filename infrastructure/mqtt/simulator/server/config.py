"""
NILM 스마트홈 전력 시뮬레이터 웹 서버 설정 모듈

웹 서버에서 사용하는 기본 포트 및 허용 시나리오 목록 등 고정 설정을 정의합니다.
MQTT 브로커 관련 설정은 simulator.py의 환경변수 기반 설정을 그대로 사용합니다.
"""

DEFAULT_PORT = 8085
ALLOWED_SCENARIOS = {"peak", "routine_missed", "random", "manual"}

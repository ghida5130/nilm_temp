"""
NILM 스마트홈 전력 시뮬레이터 브로커 및 기본 가구 설정
"""

import os

# ==========================================
# 1. 브로커 접속 및 시뮬레이션 기본 설정
# ==========================================
DEFAULT_BROKER_HOST = os.getenv("MQTT_HOST", "localhost")
DEFAULT_BROKER_PORT = int(os.getenv("MQTT_PORT", "1883"))
DEFAULT_BROKER_USER = os.getenv("MQTT_USER", "simulator_user")
DEFAULT_BROKER_PASS = os.getenv("MQTT_PASS", "test1234")

# 시뮬레이션 기본 대상 가구 목록 (H001 ~ H010 총 10개 가구)
DEFAULT_HOUSES = [f"H{i:03d}" for i in range(1, 11)]

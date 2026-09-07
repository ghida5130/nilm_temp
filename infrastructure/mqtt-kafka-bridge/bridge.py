import json
import os
import signal
import sys

import paho.mqtt.client as mqtt
from confluent_kafka import Producer

# 설정 (환경변수로 덮어쓰기 가능, 기본값은 로컬 개발용)
MQTT_HOST = os.getenv("MQTT_HOST", "localhost")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USER = os.getenv("MQTT_USER", "simulator_user")
MQTT_PASS = os.getenv("MQTT_PASS", "test1234")
MQTT_TOPIC = os.getenv("MQTT_TOPIC", "v1/power/sim/+/main")

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "power.raw.v1")

producer = Producer({
    "bootstrap.servers": KAFKA_BOOTSTRAP,
    "acks": "all",       # 브로커 저장 확인 후 성공 처리
    "linger.ms": 20,     # 20ms 모아서 배치 전송
})


def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        client.subscribe(MQTT_TOPIC, qos=1)
        print(f"MQTT 연결 성공, 구독: {MQTT_TOPIC}")
    else:
        print(f"MQTT 연결 실패: {reason_code}")


def on_message(client, userdata, msg):
    """MQTT 수신 → 필수 필드 검증 → Kafka 발행"""
    try:
        payload = json.loads(msg.payload)
        house = payload.get("house")
        if not house or "power_w" not in payload or "ts" not in payload:
            print(f"[skip] 필수 필드 누락: {msg.topic}")
            return
        # key=house: 같은 가구는 항상 같은 파티션 → 가구별 순서 보장
        producer.produce(
            KAFKA_TOPIC,
            key=house.encode(),
            value=json.dumps(payload).encode(),
        )
        producer.poll(0)
    except json.JSONDecodeError:
        print(f"[skip] JSON 파싱 실패: {msg.topic}")
    except BufferError:
        producer.poll(1)


def main():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    if MQTT_USER:
        client.username_pw_set(MQTT_USER, MQTT_PASS)
    client.on_connect = on_connect
    client.on_message = on_message

    def shutdown(sig, frame):
        print("\n브릿지 종료 중... (남은 메시지 flush)")
        client.disconnect()
        producer.flush(5)
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)

    print(f"MQTT({MQTT_HOST}:{MQTT_PORT}) → Kafka({KAFKA_BOOTSTRAP}/{KAFKA_TOPIC}) 브릿지 시작")
    client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
    client.loop_forever()


if __name__ == "__main__":
    main()
"""MQTT QoS 1 -> Kafka. Acknowledge only after Kafka confirms delivery."""
import json
import logging
import os
from pathlib import Path
import signal
import ssl
import threading
import time

import paho.mqtt.client as mqtt
from confluent_kafka import Producer

<<<<<<< HEAD
LOG = logging.getLogger("bridge")
HEALTH_FILE = Path("/tmp/bridge-ready")


def forward_message(client, producer, message, topic, failed):
=======
# 설정 (환경변수로 덮어쓰기 가능, 기본값은 로컬 개발용)
MQTT_HOST = os.getenv("MQTT_HOST", "localhost")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USER = os.getenv("MQTT_USER", "kafka_bridge_user")
MQTT_PASS = os.getenv("MQTT_PASS", "test1234")
MQTT_TOPIC = os.getenv("MQTT_TOPIC", "v1/power/sim/+/main")

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "power.raw.v1")

producer = Producer({
    "bootstrap.servers": KAFKA_BOOTSTRAP,
    "acks": "all",       # 브로커 저장 확인 후 성공 처리
    "linger.ms": 20,     # 20ms 모아서 배치 전송
})


def delivery_report(err, msg):
    """Kafka 메시지 발행 결과 콜백"""
    if err is not None:
        print(f"[Kafka 전송 실패] 토픽 {msg.topic()} 파티션 {msg.partition()}: {err}", flush=True)


def on_connect(client, userdata, flags, reason_code, properties=None):
    if reason_code == 0:
        client.subscribe(MQTT_TOPIC, qos=1)
        print(f"MQTT 연결 성공, 구독 시작: {MQTT_TOPIC} (QoS 1)", flush=True)
    else:
        print(f"MQTT 연결 실패 (코드: {reason_code})", flush=True)


def on_message(client, userdata, msg):
    """MQTT 수신 → 필수 필드 검증 → Kafka 발행"""
>>>>>>> 92960f7d202b99d26a7d24109b802013c3150cf2
    try:
        payload = json.loads(message.payload)
        if not isinstance(payload, dict):
            raise ValueError("payload must be an object")
        house = payload.get("house")
<<<<<<< HEAD
        if not isinstance(house, str) or not house or not {"power_w", "ts"} <= payload.keys():
            raise ValueError("house, power_w and ts required")
    except (ValueError, UnicodeError):
        LOG.warning("Skipping invalid message on %s", message.topic)
        client.ack(message.mid, message.qos)
        return

    def delivered(error, _record):
        if error:
            LOG.error("Kafka delivery failed: %s", error)
            failed.set()
        else:
            client.ack(message.mid, message.qos)

    try:
        producer.produce(topic, key=house.encode(), value=json.dumps(payload).encode(),
                         on_delivery=delivered)
    except Exception:
        LOG.exception("Kafka enqueue failed")
        failed.set()


def configure_tls(client):
    if os.getenv("MQTT_TLS_ENABLED", "false").lower() == "true":
        client.tls_set(ca_certs=os.getenv("MQTT_CA_FILE") or None,
                       cert_reqs=ssl.CERT_REQUIRED)
=======
        if not house or "power_w" not in payload or "ts" not in payload:
            print(f"[skip] 필수 필드 누락: {msg.topic}", flush=True)
            return
        # key=house: 같은 가구는 항상 같은 파티션 → 가구별 순서 보장
        producer.produce(
            KAFKA_TOPIC,
            key=house.encode(),
            value=json.dumps(payload).encode(),
            on_delivery=delivery_report,
        )
        producer.poll(0)
    except json.JSONDecodeError:
        print(f"[skip] JSON 파싱 실패: {msg.topic}", flush=True)
    except BufferError:
        print("[warn] Kafka Producer 내부 큐 버퍼 풀 - 플러시 후 재시도", flush=True)
        producer.poll(1.0)
        try:
            producer.produce(
                KAFKA_TOPIC,
                key=house.encode(),
                value=json.dumps(payload).encode(),
                on_delivery=delivery_report,
            )
        except Exception as e:
            print(f"[error] Kafka 재시도 실패: {e}", flush=True)
>>>>>>> 92960f7d202b99d26a7d24109b802013c3150cf2


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    stop, failed, subscribed = threading.Event(), threading.Event(), threading.Event()
    HEALTH_FILE.unlink(missing_ok=True)
    topic = os.getenv("KAFKA_TOPIC", "power.raw.v1")
    producer = Producer({
        "bootstrap.servers": os.getenv("KAFKA_BOOTSTRAP", "localhost:9092"),
        "enable.idempotence": True, "acks": "all", "linger.ms": 20,
        "delivery.timeout.ms": 30000,
    })
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                         client_id=os.getenv("MQTT_CLIENT_ID", "nilm-kafka-bridge"),
                         clean_session=False, manual_ack=True)
    username = os.getenv("MQTT_USER", "")
    if username:
        client.username_pw_set(username, os.getenv("MQTT_PASS", ""))
    configure_tls(client)
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    def on_connect(connection, _userdata, _flags, reason, _properties):
        subscribed.clear()
        if reason == 0:
            connection.subscribe(os.getenv("MQTT_TOPIC", "v1/power/sim/+/main"), qos=1)
        else:
            LOG.error("MQTT authentication/connection failed: %s", reason)

    def on_subscribe(_client, _userdata, _mid, reasons, _properties):
        if any(reason.is_failure for reason in reasons):
            failed.set()
        else:
            subscribed.set()
            LOG.info("MQTT subscription ready")

    def on_disconnect(_client, _userdata, _flags, _reason, _properties):
        subscribed.clear()
        HEALTH_FILE.unlink(missing_ok=True)

    client.on_connect = on_connect
    client.on_subscribe = on_subscribe
    client.on_disconnect = on_disconnect
    client.on_message = lambda c, u, m: forward_message(c, producer, m, topic, failed)
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_args: stop.set())

<<<<<<< HEAD
    client.connect_async(os.getenv("MQTT_HOST", "localhost"),
                         int(os.getenv("MQTT_PORT", "1883")), keepalive=30)
    client.loop_start()
    kafka_ready, next_check = False, 0.0
    try:
        while not stop.is_set() and not failed.is_set():
            producer.poll(0.2)
            now = time.monotonic()
            if now >= next_check:
                try:
                    metadata = producer.list_topics(topic=topic, timeout=5)
                    info = metadata.topics.get(topic)
                    kafka_ready = bool(info and info.error is None and info.partitions)
                except Exception:
                    kafka_ready = False
                    LOG.warning("Kafka not ready; retrying")
                next_check = now + 10
            if subscribed.is_set() and kafka_ready:
                HEALTH_FILE.touch()
            else:
                HEALTH_FILE.unlink(missing_ok=True)
            stop.wait(0.2)
    finally:
        HEALTH_FILE.unlink(missing_ok=True)
        # Keep unacknowledged messages in the broker's persistent session.
        client.on_message = lambda *_args: None
        remaining = producer.flush(20)
        client.disconnect()
        client.loop_stop()
    return 1 if failed.is_set() or remaining else 0
=======
    def shutdown(sig, frame):
        print("\n브릿지 종료 중... (남은 메시지 flush)", flush=True)
        client.disconnect()
        producer.flush(5)
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    print(f"MQTT({MQTT_HOST}:{MQTT_PORT}) 계정: {MQTT_USER} → Kafka({KAFKA_BOOTSTRAP}/{KAFKA_TOPIC}) 브릿지 시작", flush=True)
    client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
    client.loop_forever()
>>>>>>> 92960f7d202b99d26a7d24109b802013c3150cf2


if __name__ == "__main__":
    raise SystemExit(main())

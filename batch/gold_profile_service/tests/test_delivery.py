from datetime import datetime, timedelta, timezone

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from realtime_analysis.database import Base

from gold_profile.config import GoldProfileSettings
from gold_profile.delivery import (
    GoldProfileDeliveryOutbox, GoldProfilePublisher, enqueue_payloads,
)


class FakeProducer:
    def __init__(self, fail=False):
        self.fail = fail
        self.messages = []
        self.callback = None

    def produce(self, **kwargs):
        self.messages.append(kwargs)
        self.callback = kwargs["callback"]

    def flush(self, _timeout):
        self.callback(RuntimeError("broker unavailable") if self.fail else None, None)
        return 0


def payload(version="run-1"):
    return {
        "schema_version": 2,
        "household_id": "H001",
        "profile_version": version,
        "profile_revision": 3,
        "delivery_mode": "ACTIVE",
    }


def test_failed_publish_is_retried_and_duplicate_enqueue_is_harmless(tmp_path):
    engine = create_engine(f"sqlite+pysqlite:///{tmp_path / 'outbox.db'}")
    Base.metadata.create_all(engine)
    sessions = sessionmaker(bind=engine, expire_on_commit=False)
    now = datetime(2026, 9, 20, tzinfo=timezone.utc)
    with sessions.begin() as session:
        assert enqueue_payloads(session, [payload()], now=now) == 1
        assert enqueue_payloads(session, [payload()], now=now) == 0

    settings = GoldProfileSettings(
        profile_publisher_retry_seconds=10,
        kafka_bootstrap_servers="unused:9092",
    )
    producer = FakeProducer(fail=True)
    publisher = GoldProfilePublisher(settings, sessions, producer=producer)
    assert publisher.publish_pending(now=now) == (0, 1)
    with sessions() as session:
        row = session.query(GoldProfileDeliveryOutbox).one()
        assert row.status == "PENDING"
        assert row.attempt_count == 1
        assert "broker unavailable" in row.last_error

    producer.fail = False
    assert publisher.publish_pending(now=now + timedelta(seconds=10)) == (1, 0)
    assert publisher.publish_pending(now=now + timedelta(seconds=20)) == (0, 0)
    with sessions() as session:
        row = session.query(GoldProfileDeliveryOutbox).one()
        assert row.status == "PUBLISHED"
        assert row.attempt_count == 2
    assert len(producer.messages) == 2

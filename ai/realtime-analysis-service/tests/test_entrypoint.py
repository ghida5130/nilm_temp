from realtime_analysis.config import Settings


def test_default_topic_contracts() -> None:
    settings = Settings(_env_file=None)

    assert settings.kafka_input_topic == "power.raw.v1"
    assert settings.kafka_analysis_event_topic == "analysis.event.v1"
    assert settings.kafka_analysis_activity_topic == "analysis.activity.v1"
    assert settings.kafka_analysis_data_quality_topic == "analysis.data-quality.v1"
    assert settings.kafka_analysis_snapshot_topic == "analysis.snapshot.v1"
    assert settings.consumer_config()["enable.auto.commit"] is False
    assert settings.http_host == "0.0.0.0"
    assert settings.http_port == 8000


def test_fake_appliance_setting_is_parsed() -> None:
    settings = Settings(
        _env_file=None,
        fake_on_appliances=" microwave, hair_dryer ",
    )

    assert settings.fake_on_appliance_types == ("MICROWAVE", "HAIR_DRYER")

import pytest

from realtime_analysis.predictor import APPLIANCE_ORDER
from realtime_analysis.schemas import AppliancePrediction
from realtime_analysis.state_decider import ApplianceStateDecider


def predictions(probabilities: dict[str, float]) -> list[AppliancePrediction]:
    return [
        AppliancePrediction(
            appliance_type=appliance_type,
            probability=probabilities.get(appliance_type, 0.0),
        )
        for appliance_type in APPLIANCE_ORDER
    ]


def test_decider_applies_per_appliance_thresholds() -> None:
    thresholds = {appliance_type: 0.5 for appliance_type in APPLIANCE_ORDER}
    thresholds["MICROWAVE"] = 0.7
    decider = ApplianceStateDecider(thresholds)

    states = decider.decide(
        predictions({"KETTLE": 0.5, "MICROWAVE": 0.69})
    )
    by_appliance = {state.appliance_type: state for state in states}

    assert by_appliance["KETTLE"].is_on is True
    assert by_appliance["KETTLE"].probability == 0.5
    assert by_appliance["KETTLE"].threshold == 0.5
    assert by_appliance["MICROWAVE"].is_on is False


def test_decider_rejects_incomplete_predictions() -> None:
    decider = ApplianceStateDecider(
        {appliance_type: 0.5 for appliance_type in APPLIANCE_ORDER}
    )

    with pytest.raises(ValueError, match="Prediction appliance mismatch"):
        decider.decide(predictions({})[:-1])

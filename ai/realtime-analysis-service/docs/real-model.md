# R3 실제 모델 Predictor — 첫 단계

`SelectedScenePredictor`는 고정 R3 체크포인트를 실제로 forward한다. 원본 모델 코드는
`real_models/models.py`에 그대로 보존했고, 서비스에 포함한 reviewer lock으로 자산 해시를
검증한다. 외부 Python 코드를 실행하거나 실패 시 fake 모델로 대체하지 않는다.

입력은 정규화하지 않은 P/Q/PF/I 255행이다. 정규화는 Predictor 내부에서 정확히 한 번
수행한다. **StandardizingPredictor로 감싸지 않는다.** 출력은 선택 기기 하나의 확률이다.
나머지 기기는 OFF가 아니라 미추론이다.

## 로컬 실행

서비스 디렉터리에서 Python 3.11 이상으로 실행한다. 체크포인트는 Git 밖에 둔다.

```powershell
python -m venv .venv
.venv/Scripts/python -m pip install torch==2.11.0 --index-url https://download.pytorch.org/whl/cpu
.venv/Scripts/python -m pip install -e '.[inference,dev]'
.venv/Scripts/python -m realtime_analysis.model_replay --asset-root ../../../artifacts/nilm_r2_demo --appliance kettle --device cpu --dtype float32 --output ../../../handoff/r3/kettle_cpu_run
```

출력 디렉터리는 매 실행마다 새 경로를 지정한다. `scores.jsonl`은 실제 forward 결과와
UNKNOWN/ON/OFF 및 SYNC/전환을 담고, `runtime.json`은 해시·dtype·장치·행 수를 기록한다.
평가용 저장 점수와 정답 파일은 실행 경로에서 읽지 않는다. CPU float32 결과를 H200 BF16과
동일하다고 간주하지 말고, 사후 source index별 점수·상태 비교 결과를 별도로 기록한다.

```powershell
$env:R3_TEST_ASSET_ROOT = (Resolve-Path ../../../artifacts/nilm_r2_demo).Path
.venv/Scripts/python -m pytest tests/test_real_predictor.py -q
```

## 현재 통합 범위

이 단계는 실제 Predictor 및 로컬 panel 실행기다. 기존 `realtime-analysis` Kafka 진입점은
아직 FakePredictor를 사용한다. 기존의 299행/6기기 bool handler에 선택 기기 한 개를
강제로 끼우면 미추론을 OFF로 만들게 되므로 자동 전환하지 않는다.

다음 통합에서는 versioned selected-scene 입력/출력, run/profile/source_index,
UNKNOWN 저장·조회 규약을 추가하고 `__main__.py`에서 명시적 모드로 새 Predictor를
선택한다. decoder는 한 번만 적용하고 DB 실패 복원과 partition revoke 처리를 보존한다.
실제 MQTT/Kafka·DB/API 시험은 그 변경 이후에 수행한다.

검증 기준은 R3 C03/C04 및 TEST_MATRIX다. 단순 forward 성공은 긴 prefix·OFF 대조,
주동작 종료 검수 또는 실제 백엔드 연동 완료를 뜻하지 않는다.

## 2026-09-21 로컬 검증

- Python 3.12.14 / PyTorch 2.11.0+cpu, batch 1, thread 1.
- 기존 테스트 포함 132 passed. 별도 PostgreSQL을 요구하는 8개는 제외했다.
- kettle H046 592행 → 338 ready scores. CPU float32 cold 2회 점수 동일.
- H200 BF16 참조 대비 CPU float32 최대 절대 점수 차이 0.0082429051,
  CPU BF16은 0.0115510225. 두 설정 모두 상태·ready 불일치 0.
- 선택 평가 구간에서 TP 38 / TN 300 / FP 0 / FN 0 / UNKNOWN 0.
  SYNC source index 2490671, ON 2490791, OFF 2490829.
- TCN/LSTM/Transformer 실제 체크포인트 forward, 호출 사이 hidden state 비누적,
  잘못된 길이·비유한 입력·해시 불일치·마스크·미추론 UNKNOWN을 검증했다.

수치 평가는 이 kettle 선택 구간에 한정한다. 긴 prefix·OFF 대조 및 실제 transport,
DB/API 검수는 아직 수행하지 않았다.

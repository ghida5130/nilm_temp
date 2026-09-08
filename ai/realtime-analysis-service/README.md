# Realtime Analysis Service

Kafka 전력 데이터를 검증하고, 가구별 입력 버퍼와 MVP용 가전 ON/OFF 예측을 거쳐
평소 루틴이 누락된 가구의 이상 이벤트를 `analysis.event.v1`로 발행합니다.

## MVP 처리 흐름

```text
power.raw.v1
  -> 입력 검증 및 가구별 299개 버퍼
  -> FakePredictor 가전 6종 ON/OFF 결과
  -> JSON baseline과 일일 사용 상태 비교
  -> score가 임계치 이상이면 analysis.event.v1 발행
```

잘못된 입력은 `dlq.analysis`로 발행합니다. Kafka offset은 정상 처리 또는 DLQ 전송이
완료된 뒤에만 수동으로 commit합니다.

## 로컬 실행

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -e ".[dev]"
Copy-Item .env.example .env
python -m realtime_analysis
```

실제 Kafka 환경에 맞게 `.env`의 접속 주소와 토픽을 수정합니다. 기본 baseline은
`config/baselines.json`에 있으며, 현재 예시는 `H001`의 `MICROWAVE` 루틴입니다.

`FAKE_ON_APPLIANCES`에 쉼표로 가전명을 지정하면 FakePredictor가 해당 가전을 ON으로
반환합니다.

```env
FAKE_ON_APPLIANCES=MICROWAVE,TV
```

빈 값이면 모든 가전을 OFF로 반환하므로 마감 시각 이후 `ROUTINE_MISSED` 흐름을 확인할
수 있습니다.

## 현재 MVP 제약

- 실제 AI 모델 대신 결정적인 FakePredictor를 사용합니다.
- baseline과 당일 활동 상태는 각각 JSON과 메모리에 저장합니다.
- 재시작하면 299개 버퍼와 당일 활동·발행 상태가 초기화됩니다.
- HTTP API와 DB 연동은 포함하지 않습니다.

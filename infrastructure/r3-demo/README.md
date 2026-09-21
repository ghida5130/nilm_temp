# R3 선택 장면 로컬 통합

독립 Compose 프로젝트 `nilm-r3-demo`로 MQTT → bridge → Kafka → 실제 모델 → 분석 DB
→ scene Kafka → monitoring DB → 조회 API를 실행한다. 기존 `nilm-local` 프로젝트를 변경하지 않는다.
컨테이너 안의 실제 서비스 코드와 마이그레이션을 사용하며, 모델/정답 결과를 대신 발행하지 않는다.

## 준비와 기동

이 폴더의 Git 제외 파일 `.env`에 다음 값을 설정한다.

```dotenv
R3_DB_PASSWORD=로컬시험용비밀번호
R3_RUN_ID=r3-kettle-e2e-20260921
R3_APPLIANCE=kettle
R3_ASSET_ROOT=C:/절대경로/artifacts/nilm_r2_demo
```

```powershell
docker compose --env-file infrastructure/r3-demo/.env -f infrastructure/r3-demo/compose.yaml config --quiet
docker compose --env-file infrastructure/r3-demo/.env -f infrastructure/r3-demo/compose.yaml up -d --build
docker compose --env-file infrastructure/r3-demo/.env -f infrastructure/r3-demo/compose.yaml ps
```

`bridge`가 healthy이고 analysis/monitoring이 시작된 뒤 입력을 보낸다. MQTT는 localhost:18884,
관리 조회 API는 localhost:18084에만 바인딩한다. 이 로컬 스택에서는 인증/외부 알림/기존
위험 평가 스케줄러를 사용하지 않는다. 운영 구성으로 그대로 배포하지 않는다.

분석 서비스 디렉터리에서:

```powershell
.venv/Scripts/python -m pip install -e '.[demo]'
.venv/Scripts/python -m realtime_analysis.scene_replay publish --asset-root ../../../artifacts/nilm_r2_demo --appliance kettle --household r3-kettle --run-id r3-kettle-e2e-20260921 --start-time 2026-09-21T10:00:00+09:00
```

기본 1초 간격으로 592개 원본 행을 보낸다. 시험 가속은 `--interval 0.01`을 사용할 수 있다.
측정 시각은 항상 1Hz이며, 실제 벽시계 지연 성능 평가와 가속 시험을 구분한다. MQTT publish
성공은 모델/DB 처리 완료를 의미하지 않는다. run ID는 Compose와 일치시킨다. 재시도는 같은
run ID와 start-time을 사용한다. 새 시험은 새 run ID를 지정하고 analysis 컨테이너를 재생성한다.
이전 회차의 Kafka 행은 새 consumer group에서 읽더라도 run ID로 건너뛴다.

## 결과 조회

```text
GET http://localhost:18084/api/monitoring/admin/selected-scene?householdId=r3-kettle&runId=r3-kettle-e2e-20260921&profileId=r2-scene-kettle-008b808cf17505534886
```

최신 source index는 2491008이어야 한다. `sourceIndex=2490791`을 추가하면 참조 ON 시작,
`sourceIndex=2490829`는 OFF 전환 행을 확인할 수 있다. 원래 profile의 나머지 다섯 기기는
항상 `inferred=false`, `probability=null`, `state=UNKNOWN`, `is_on=null`이다. 준비 254행도
UNKNOWN으로 저장된다. 선택 기기의 처음 확정값과 복구는 신규 ON이 아니라 SYNC다.

오래된 스냅샷이 나중에 도착해도 source index가 가장 큰 행을 반환하며, 같은 ID의 동일한
재전송은 DB 행을 추가하지 않는다. 입력 충돌/순서 누락은 분석 서비스를 실패시켜 offset
저장을 막는다. 설정·데이터를 바로잡은 뒤 같은 회차를 재시작해 저장된 창부터 복구한다.

## Broker 없이 저장 경로 검증

```powershell
.venv/Scripts/python -m realtime_analysis.scene_replay storage-smoke --asset-root ../../../artifacts/nilm_r2_demo --run-id r3-storage-1 --start-time 2026-09-21T10:00:00+09:00 --output ../../../handoff/r3/storage-new
```

SQLite 분석 DB와 실제 스냅샷 JSONL을 새 폴더에 만든다. 이 JSONL 경로를
`R3_EVIDENCE_FILE` 환경변수로 전달해 monitoring의 `SceneSnapshotFlowTest`를 실행하면
실제 Python 출력의 Java DB/API 계약을 검증한다. 이 시험에는 MQTT/Kafka가 포함되지 않는다.

종료는 `docker compose --env-file infrastructure/r3-demo/.env -f infrastructure/r3-demo/compose.yaml stop`을 사용한다.

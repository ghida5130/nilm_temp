# Monitoring: Kafka 이벤트와 Web Push

## 이번 구현 범위

- `analysis.event.v1`을 수신하고 JSON 필수 값과 점수 범위를 검증한다.
- 가구별 대상자를 잠금 조회한 뒤 이벤트 ID로 중복 처리를 방지한다.
- 이벤트와 대상자의 안전 확인 알림을 같은 트랜잭션에서 저장한다.
- 저장 커밋 후 Web Push를 전송하고 별도 트랜잭션으로 발송 결과를 반영한다.
- 외출/모니터링 중지/정상 점수 이벤트는 이력만 저장한다.
- 담당자 알림 정책·대시보드 CRUD는 이번 구현 범위에 포함하지 않는다.

## 실행 설정

Kafka와 DB만 확인할 때는 `WEB_PUSH_ENABLED=false`(기본값)로 실행한다.
VAPID 키 없이 서버를 시작할 수 있다. Kafka까지 끄려면
`KAFKA_CONSUMER_ENABLED=false`를 설정한다.

실제 푸시 테스트:

| 환경변수 | 값 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | local |
| WEB_PUSH_ENABLED | true |
| WEB_PUSH_VAPID_PUBLIC_KEY | 프론트 구독 시 사용한 공개키 |
| WEB_PUSH_VAPID_PRIVATE_KEY | 해당 공개키와 짝인 비밀키 |
| WEB_PUSH_SUBJECT | mailto:운영자이메일 |
| PUSH_TEST_AUTH_SUB | 기본값 test-subject-3 |

`WEB_PUSH_VAPID_SUBJECT`도 이전 설정 호환용으로 지원하며
`WEB_PUSH_SUBJECT`가 우선한다.

프론트에서 구독 등록 후 `POST /api/monitoring/push-test`를 호출한다.
요청 본문은 없다. 이 API는 local 프로필과 푸시 활성화가 모두 필요하다.
구독과 테스트 알림은 동일한 PUSH_TEST_AUTH_SUB를 사용한다.
기존 구독이 auth_sub='3'이면 PUSH_TEST_AUTH_SUB=3으로 맞출 수 있다.

## Kafka 실제 이벤트 처리

`TEST_DATA_ENABLED=true`로 실행하면 Flyway 완료 후 Kafka 수신 전에
IoT Device Service의 실제 회원가입·로그인·가구 등록 API를 호출한다.

- `subject01@nilm.local` ~ `subject10@nilm.local`: `H001` ~ `H010`을 등록하고
  `subjects.auth_sub`에 Keycloak user ID를 연결한다.
- `manager01@nilm.local` ~ `manager10@nilm.local`: 기관 담당자로 가입하고
  `managers.auth_sub`에 Keycloak user ID를 연결한다.
- 각 대상자는 같은 번호의 담당자에게 1:1로 배정된다.
- 비밀번호는 `TEST_ACCOUNT_PASSWORD`, API 주소는 `IOT_DEVICE_SERVICE_URL`로
  주입한다. 테스트 데이터가 켜져 있는데 비밀번호가 비어 있으면 시작을 실패한다.

재시작 시 기존 계정은 로그인으로 복구하고, 이미 소유한 가구와 로컬 row는
재사용한다. 같은 가구 ID나 auth_sub가 다른 사용자와 충돌하면 기존 데이터를
덮어쓰지 않고 시작을 실패한다.

- 해당 household_id를 가진 대상자가 subjects에 정확히 한 명 있어야 한다.
- 대상자 auth_sub와 브라우저 구독 auth_sub가 같아야 실제 전송된다.
- 임계치 기본값은 주의 70, 위험 90이며 RISK_WARNING_THRESHOLD,
  RISK_DANGER_THRESHOLD로 조정한다. 현재는 공통 정책을 사용한다.
- 기존 Python 계약에 없는 event_type/appliance_type은 NULL로 저장한다.
  생산자가 해당 필드를 추가하면 그대로 보존한다.
- 미등록/중복 가구 또는 잘못된 이벤트는 소비 컨테이너를 중지시킨다.
  로그의 원인을 해결한 뒤 서비스를 재시작한다. 실패 메시지를 조용히 건너뛰지 않는다.
- 같은 그룹의 저장된 offset이 있으면 earliest도 그다음부터 시작한다.
- 위험 이벤트를 Kafka에서 다시 받아도 같은 ID의 알림은 추가 생성되지 않는다.

## 마이그레이션

V2는 기존 구독 테이블 생성문을 유지했다. V3는 notifications,
V4는 subjects/analysis_events를 생성한다.
이미 수정된 V2를 DB에 적용했거나 수동으로 같은 테이블을 만든 환경은
이력과 스키마를 먼저 확인해야 한다. 자동 repair나 데이터 삭제는 하지 않는다.

## 현재 동작의 한계

응답 기한은 알림 생성 시점부터 30초이며 브라우저 표시 시각 기준이 아니다.
SENT는 한 개 이상의 푸시 서비스 접수 성공이며 사용자 열람을 뜻하지 않는다.
푸시 비활성화 중 생성한 알림을 나중에 자동 발송하지 않는다.
커밋 직후 프로세스 종료 및 전송 실패의 자동 재발송은 아직 구현하지 않았다.
푸시 실패 시 이벤트는 유지되며 일반 발송 실패는 FAILED로 기록한다.
auth_sub가 없는 대상자는 이벤트만 저장하고 알림 생성 생략 로그를 남긴다.

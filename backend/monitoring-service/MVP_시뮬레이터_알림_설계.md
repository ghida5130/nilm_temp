# MVP 시뮬레이터 알림 간소화 설계

작성일: 2026-09-14. 설계 제안이며 실행 코드·환경 설정 변경은 수행하지 않았다.

## 1. 권장 구성

**H001 한 가구 + local-user 한 사용자 + 기본 SSE 화면 배너**로 시연한다. 로그인·담당자 배정·개인 임계치·수신 설정 화면을 거치지 않는다. 화면을 닫은 상태의 알림이 필요하면 Web Push를 선택적으로 추가하고 브라우저 알림 허용만 최초 한 번 받는다.

| 요구 | SSE 배너 기본 모드 | Web Push 추가 모드 |
|---|---|---|
| 로그인·역할 설정 | 없음 | 없음 |
| 대상자·가구 수동 배정 | 없음, H001 자동 연결 | 동일 |
| 공개키 직접 입력 | 없음 | 서버 설정 자동 조회 |
| 브라우저 알림 허용 | 필요 없음 | 최초 허용 필요 |
| 페이지가 열린 동안 알림 | 가능 | 가능 |
| 페이지를 닫은 상태의 알림 | 불가 | 지원 환경에서 가능 |
| 기존 30초 위험 응답 흐름 | 현재 Push 발송 성공에 종속, 별도 보완 전 미지원 | 기존 흐름 유지 |

브라우저 시스템 알림 권한은 서버의 담당자 권한과 다르다. 시스템 알림을 사용자 허용 없이 켤 수는 없다. 권한은 사용자 동작으로 요청하고 보안 컨텍스트가 필요하다. 따라서 ‘권한 설정 0회’는 SSE 배너로 충족한다. [MDN Notifications 안내](https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API)

## 2. 현재 구현에서 이미 가능한 부분

- V3 마이그레이션이 `household_access(H001, local-user)`를 존재 확인 후 등록한다.
- Monitoring의 APP_SECURITY_ENABLED=false이면 CurrentUserService는 기본 local-user를 사용한다. Gateway도 인증 비활성 설정을 맞춰야 한다.
- 시뮬레이터는 H001부터 가구 ID를 생성하며 `--houses 1`로 한 가구만 실행할 수 있다.
- 분석 서비스의 샘플 기준선은 H001, MICROWAVE, expected_until=08:10이다.
- 분석 결과는 기존 `analysis.event.v1 → analysis_event → incident → ui_outbox → SSE` 경로를 사용한다.
- Push 구독 POST는 같은 endpoint가 있으면 새 행을 계속 만들지 않고 갱신한다.
- Web Push 비활성 또는 유효 구독 없음이면 현재 발송은 FAILED가 된다. SSE 사건 이벤트는 이와 별개로 생성된다.

인증을 끄는 것만으로 모든 가구를 볼 수 있는 것은 아니다. 가구 접근 조회는 남으므로 H001을 사용한다. LOCAL_USER_ID를 변경하면 V3의 local-user와 불일치하므로 MVP에서는 고정한다.

## 3. 최소 설정과 운영 범위

시연 전용 프로필 `demo`를 제안한다. 아래 환경 변수 중 APP_DEMO_ENABLED와 DEMO_ALERT_MODE는 신규 구현 항목이다.

```properties
# Gateway와 Monitoring에 공통 적용
APP_SECURITY_ENABLED=false

# Monitoring
SPRING_PROFILES_ACTIVE=demo
APP_DEMO_ENABLED=true
LOCAL_USER_ID=local-user
DEMO_ALERT_MODE=SSE
WEB_PUSH_ENABLED=false
KAFKA_CONSUMER_ENABLED=true
```

demo 프로필은 전용 데이터와 제한된 시연 환경에서만 사용한다. MQTT 브로커·Kafka 접속 자격정보는 기존 파이프라인 설정을 유지한다. 사용자 권한 화면을 생략하는 것과 인프라 인증 제거는 별개다.

처음부터 새로운 권한 테이블이나 수신 대상 자동 추론을 추가하지 않는다. H001은 기존 V3 연결을 사용한다. 향후 H002 이상이 필요할 때만 demo 시작 초기화기가 명시된 시연 가구 목록을 local-user에 멱등 등록한다. 수신되는 임의 householdId를 자동으로 모두 개방하지 않는다.

## 4. SSE 기본 모드의 흐름

```text
시연 페이지 접속
  → GET /api/monitoring/stream 자동 연결
  → 시뮬레이터가 H001 전력 발행
  → 분석 서비스가 ROUTINE_MISSED 판단
  → AnalysisEvent + Incident + UiOutbox 저장
  → incident-opened 수신
  → 화면 상단 배너 + 사건 목록 갱신
```

화면에는 ‘시뮬레이터 알림 연결됨’, 연결 장애, 최근 수신 시각만 표시한다. 브라우저 Notification API·구독 요청·VAPID 입력을 호출하지 않는다. 이벤트의 reason에 없는 가전명은 추측하지 않고 ‘H001 일상 활동 미감지’로 표시한다.

프런트는 연결 직후와 재연결 시 사건 GET으로 현재 상태를 복구한다. 과거 replay 이벤트는 목록을 갱신하되 알림 배너를 반복 표시하지 않는다. 배너 중복은 incidentId, SSE 처리 중복은 outbox id로 제거한다. 탭 간 배너는 별개 화면이므로 기본적으로 각각 표시하되 MVP 시연은 한 탭을 기준으로 한다.

**백엔드 최소 변경:** demo + SSE 모드에서는 AnalysisEventIngestService가 Push 전용 NotificationDelivery 생성만 생략한다. AnalysisEvent·Incident·DETECTED 조치·UiOutbox는 그대로 저장한다. 현재처럼 Push를 끄기만 하면 FAILED 발송 행이 쌓이므로 이를 준비 완료 상태로 오해하지 않게 한다. 조건은 일반 운영 흐름에 전역 적용하지 않는다.

이 모드에서 배너 닫기는 화면 동작이다. 위험/위험하지 않음 응답이나 30초 자동 확정은 기본 범위에서 제외한다. 기존 PUT은 SENT 발송에만 응답 가능하므로 가짜 구독을 만들거나 실제 전송 없이 SENT로 표시하지 않는다. 응답 시연까지 필요하면 다음 Push 모드를 사용한다.

## 5. Web Push 모드: 최초 한 번 허용 후 자동 복구

추가 서버 설정:

```properties
DEMO_ALERT_MODE=WEB_PUSH
WEB_PUSH_ENABLED=true
WEB_PUSH_VAPID_PUBLIC_KEY=<배포 환경 공개키>
WEB_PUSH_VAPID_PRIVATE_KEY=<서버 비밀 설정>
WEB_PUSH_SUBJECT=<운영 연락 주소>
```

동일 배포의 키를 유지한다. VAPID 공개키를 프런트 입력과 환경 변수 양쪽에 따로 관리하지 않고 신규 `GET /api/monitoring/push-config`를 단일 원본으로 사용한다.

```json
{"enabled":true,"vapidPublicKey":"<공개키>"}
```

프런트 흐름:

1. 서비스 워커 준비와 push-config 조회를 수행한다.
2. permission=default이면 ‘알림 받기’ 버튼 하나를 보여 준다. 클릭 시 권한을 요청한다.
3. permission=granted이면 기존 getSubscription 결과를 재사용한다. 구독이 없을 때만 생성하고, 필요한 브라우저에서 사용자 동작을 요구하면 동일 버튼으로 진행한다.
4. 기존 구독이 있어도 구독 POST를 다시 호출하여 서버 등록을 확인한다. DB를 다시 만들었는데 브라우저만 구독되어 있는 상태를 복구한다.
5. 서버 POST 성공 후에만 ‘알림 연결됨’으로 표시한다. 페이지 새로고침 시 허용 팝업을 반복 요청하지 않는다.
6. permission=denied이면 반복 팝업을 시도하지 않고 SSE 배너로 계속 시연한다. 구독 실패도 SSE 연결 상태와 별도로 표시한다.

현재 프런트는 브라우저에 구독이 존재하면 서버 등록 확인 없이 subscribed로 표시한다. 이 부분과 공개키 입력 UI를 변경해야 한다.

시연 순서는 **구독 등록 성공 → 시뮬레이터 실행**이다. 구독 이전의 사건은 이미 FAILED가 될 수 있으며 구독 POST만으로 과거 실패 발송이 자동 재전송되지 않는다.

하나의 local-user를 쓰므로 등록된 모든 브라우저에 알림이 갈 수 있다. MVP는 지정한 브라우저 하나를 사용하고, 기기 교체 시 기존 구독을 해제한다. 같은 endpoint의 멱등 갱신과 서로 다른 기기의 수신은 구분한다.

화면 갱신은 SSE, 시스템 알림은 서비스 워커 Push 경로로만 처리한다. SSE 수신 코드가 별도로 시스템 Notification을 만들지 않는다. 서비스 워커는 notificationId를 알림 tag/중복 처리 키로 사용하여 재전달의 반복 표시를 줄인다. Push가 운영체제에 표시되었다는 보장과 Push 제공자의 접수 성공은 구분한다.

30초 기준과 응답은 기존 서버 구현을 사용한다. 클라이언트 타이머는 자동 yes를 전송하지 않는다. 브라우저/운영체제 종료·차단 상태까지 전달을 보장하는 기능으로 시연하지 않는다.

## 6. 시뮬레이터 실행과 반복 시연

기존 시뮬레이터 디렉터리에서 한 가구 루틴 누락 시나리오를 실행한다.

```text
python simulator.py --scenario routine_missed --houses 1
```

MQTT → 수집 → Kafka → 분석 → Monitoring이 모두 실행되어야 한다. `peak` 전력 급증이 현재 ROUTINE_MISSED 이벤트를 반드시 만든다고 가정하지 않는다. 분석 모델/시연 predictor, 기준선 활성 여부와 가상 시간이 기준선 마감 이후인지 확인한다.

현재 routine_missed 기본 가상 시각은 당일 08:15 KST다. 실제 시연 시간과 차이가 생길 수 있어 최근 24시간 제한과 미래 시각 검증 도입 여부를 확인한다. 실제 발생 시각처럼 잘못 표시하지 않고 ‘시뮬레이션 시각’을 명시한다.

### 재실행했는데 알림이 없는 이유

현재 중복 기준은 두 종류다.

- 같은 event_id는 중복 저장하지 않는다.
- UUID가 달라도 `(household_id, event_type, event_date, expected_until)`가 같으면 같은 사건으로 처리한다.

따라서 같은 날 H001·08:10 시나리오를 반복 실행하면 재알림이 없는 것이 정상이다. 분석 서비스 자체의 일별 발행 상태도 영향을 준다. **UUID만 새로 만들거나 기존 중복 제약을 끄는 방식은 사용하지 않는다.**

### 권장 반복 방법

1. 초기 파이프라인 검증은 한 번 수행하고, 이후 동일 이벤트 재수신은 ‘중복 알림 방지’ 시연으로 사용한다.
2. 새 사건이 필요한 반복 시연은 전용 시연 기준선의 expected_until을 실행 회차별 다른 시각으로 바꾸고 시뮬레이터 시작 시각을 그 이후로 맞춘다. 분석 서비스가 사용하는 실제 기준선 원본에 반영하고 재로딩·발행 상태 적용을 확인한다. JSON 파일을 수정하면 즉시 반영된다고 가정하지 않는다.
3. 이를 자주 실행해야 하면 후속 `demo-run` 스크립트가 유효한 당일 시각 범위에서 기준선과 가상 시작 시각을 함께 준비하도록 한다. DB 이벤트를 삭제하는 초기화는 기본 흐름에 넣지 않는다.

추가로 화면/Push 연결만 빠르게 검사하고 싶으면 시연 전용 테스트 발송 기능을 별도 구현할 수 있다. 이것은 원천 전력 감지 검증과 구분하며 이번 최소 구성의 필수 API는 아니다.

## 7. 필요한 변경 범위

| 구성 요소 | SSE 모드 최소 변경 | Push 모드 추가 |
|---|---|---|
| 프런트 | SSE 자동 연결·배너·사건 조회 | 공개키 자동 조회·최초 허용·서버 구독 재동기화 |
| Monitoring | demo 모드에서 Push 발송 행 생성 생략 | push-config 제공; 기존 구독/발송/응답 사용 |
| 가구 접근 | H001→local-user 기존 seed 사용 | 동일 |
| 분석 서비스 | H001 시연 기준선·predictor 확인 | 동일 |
| 시뮬레이터 | H001 1가구·루틴 누락 사용 | 동일 |
| DB | analysis_event 이름·중복 제약 유지 | 동일 |

MVP에는 담당자 프로필·개인 알림 설정·정책 선택 UI를 선행 조건으로 넣지 않는다. 이후 정식 서비스에서 담당자 배정 및 정책 기반 흐름으로 전환할 때 demo 분기를 제거한다. 기존 [전체 API 명세](./백엔드_API_명세.md)는 정식 화면 목표로 유지한다.

## 8. 완료 기준

- SSE 모드: 로그인·알림 권한·키 입력 없이 페이지 접속 후 H001 사건 배너 수신.
- Push 모드: 최초 한 번 허용 후 새로고침해도 설정 입력 없이 수신 가능.
- 브라우저 구독만 남고 DB 구독이 없을 때 자동 재등록.
- 동일 이벤트 재수신은 사건·알림 중복 없음; 새 시연 기준선은 새 사건 생성.
- 구독/Push 실패가 SSE 표시를 막지 않음.
- DB 발송 상태를 실제 전송 없이 SENT로 만들지 않음.
- Push 모드의 30초 무응답은 서버에서 처리하며 수동 응답과 경합해도 결과가 일관됨.

참조 구현: [V3 시연 매핑](./src/main/resources/db/migration/V3__seed_test_household_access.sql), [현재 프런트](../../frontend/src/App.tsx), [시뮬레이터](../../infrastructure/mqtt/simulator/README.md), [발송 작업](./src/main/java/com/nilm/monitoring/notification/service/NotificationDeliveryWorker.java).

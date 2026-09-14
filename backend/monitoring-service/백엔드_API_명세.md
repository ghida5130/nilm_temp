# 와이어프레임 기반 Monitoring 백엔드 API 명세

작성일: 2026-09-14. 상태: 구현 전 설계안.

대상 화면: 대시보드, 대상자 상세, 대상자 추가, 설정. 현재 Controller·DTO·SSE 구현을 기준으로 호환 계약과 신규 계약을 구분한다. 이 문서는 실행 코드·DB 변경이 아니다. 첨부 화면의 예시 점수·이름·날짜는 화면 예시이며 실제 판단 규칙으로 사용하지 않는다.

관련 문서: [도메인 설계](./도메인_설계.md), [패키지 구조 계약](./패키지_구조_계약.md).

## 1. 화면과 API 대응

모든 아래 경로에는 `/api/monitoring` 접두사를 붙인다.

| 화면 요소 | API | 상태 |
|---|---|---|
| 로그인 담당자·상단 날짜 | GET /me, 각 조회 응답 serverTime | 신규 |
| 전체·위험·주의·정상 인원 | GET /dashboard/summary | 신규 |
| 대상자 카드·검색·정렬 | GET /subjects | 신규 |
| 상세 기본 정보·연락처·메모 | GET /subjects/{subjectId} | 신규 |
| 현재 점수·가전 상태·마지막 활동 | GET /subjects/{subjectId}/snapshot | 신규 |
| 오늘 전력 그래프 | GET /subjects/{subjectId}/power-usage | 신규 |
| 위험 징후 기록 | GET /subjects/{subjectId}/analysis-events | 신규 |
| 기존 DB 대상자 검색 | POST /subject-candidates/search | 신규 |
| 대상자 추가·담당자 배정 | POST /subject-assignments | 신규 |
| 내 담당 메모 수정 | PATCH /subject-assignments/{assignmentId} | 신규 |
| 알림 임계치 선택지 | GET /risk-policies | 신규 |
| 알림 수신·요약·알림음 설정 | GET, PUT /me/notification-settings | 신규 |
| 임계치 설정 조회·저장 | GET /me/risk-policy-settings, POST /risk-policy-changes | 신규 |
| 임계치 반영 결과 확인 | GET /risk-policy-changes/{changeId} | 신규 |
| 이름·소속 저장 | PATCH /me | 신규 |
| 브라우저 구독 | POST /push-subscriptions | 구현됨 |
| 공개 Push 설정·구독 해제 | GET /push-config, DELETE /push-subscriptions/{subscriptionId} | 신규 |
| 위험/위험하지 않음 응답 | PUT /notifications/{notificationId}/responses | 구현됨 |
| 알림 클릭 후 본인 알림 조회 | GET /notifications/{notificationId} | 신규 |
| 사건 목록·상세 | GET /incidents, GET /incidents/{incidentId} | 구현됨 |
| 화면 실시간 변경 | GET /stream | 구현됨; 신규 이벤트는 별도 추가 |

보고서·별도 알림 목록·대상자 앱 전환은 사이드바 아이콘만 있고 상세 와이어프레임이 없어 이번 API 범위에 넣지 않는다. 화면 우상단 등록은 신규 개인정보 생성이 아니라 **기존 대상자를 내 관리 목록에 배정**하는 동작이다. 검색 결과의 ‘추가’ 버튼은 로컬 선택만 수행하며 마지막 ‘대상자 등록’에서 요청한다.

## 2. 공통 규칙

### 경로·인증·타입

- Gateway는 `/api/monitoring/**`를 Monitoring에 그대로 전달한다. 제공된 `/api/push-subscriptions`, `/api/notifications/...`는 현재 구현 경로가 아니다. 별도 별칭은 만들지 않는다.
- 운영 요청은 `Authorization: Bearer <access_token>`으로 인증한다. 현재 로컬 보안 비활성 설정은 운영 권한 보장을 의미하지 않는다.
- 신규 화면 API는 ACTIVE 담당자 본인의 현재 배정 대상에 한정한다. 담당자 ID를 요청에 받아 권한을 결정하지 않는다. 관리자 전체 조회는 현재 사건 API와 신규 담당자 화면 범위를 구분한다.
- UUID는 Java/DB에서는 UUID이며 **JSON에서는 UUID 형식 문자열**이다. 예: `77f1c0fa-3b02-4d0a-91af-e198360f1785`. `notification-001`은 유효한 UUID가 아니다.
- 신규 API의 BIGINT ID는 JavaScript 정밀도 보존을 위해 JSON 10진 문자열로 반환한다. 기존 API의 subscriptionId·action.id 숫자는 호환상 유지한다. householdId는 `H001` 같은 문자열이다.
- 날짜는 YYYY-MM-DD, 절대 시각은 UTC ISO 8601, 기준 시간대는 Asia/Seoul이다. Push expirationTime만 epoch milliseconds 숫자 또는 null이다.
- 신규 조회 응답은 `serverTime`을 포함한다. 상대시간·현재 나이는 이 기준으로 계산한다. UNKNOWN/null을 점수 0 또는 정상으로 표시하지 않는다.
- 개인정보 응답은 권한 확인 후 복호화하고 `Cache-Control: no-store`를 사용한다. 목록은 addressSummary, 상세만 필요한 연락처를 반환한다.

### 페이지·정렬·동시 수정

신규 목록 기본 size=20, 최대 100. size는 1~100 정수, cursor는 서버가 발급한 불투명 문자열이다. 응답은 `items`, `nextCursor`(마지막이면 null), `hasNext`, `serverTime`이다. cursor는 필터·정렬 조건에 귀속하며 조건 변경 시 버린다. 현재 사건 목록은 이 형식이 아닌 단순 배열이다.

변경 가능한 신규 설정·배정·프로필은 응답에 revision(10진 문자열)을 제공하고 수정 요청의 expectedRevision과 비교한다. 불일치는 409 CONFLICT다. 이 revision은 도메인 문서에 추가할 낙관적 잠금용 저장 필드이며 아직 구현되어 있지 않다.

### 오류

현재 ErrorResponse 형식을 따른다.

```json
{
  "timestamp": "2026-09-14T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "입력값 검증에 실패했습니다.",
  "path": "/api/monitoring/me/notification-settings",
  "errors": [{"field": "dangerEnabled", "reason": "필수 값입니다."}]
}
```

| HTTP | 의미 |
|---|---|
| 400 | 필수값·범위·날짜·UUID·cursor·JSON 형식 오류 |
| 401 | 인증 누락·만료 |
| 403 | 담당자 비활성 또는 수행 권한 없음 |
| 404 | 대상 리소스 없음; 신규 후보/대상자 API는 허용 범위 밖 ID도 404 |
| 409 | 중복 배정, 수정 버전 충돌, 처리 상태 충돌 |
| 429 | 후보 검색 등 요청 제한 |
| 503 | 정책 원본·후보 원본 등 필수 외부 서비스 사용 불가 |

400 형식 오류와 401 응답 형식은 목표 계약이다. 현재 전역 예외 처리에는 JSON 파싱/타입 변환 전용 처리가 없어 일부 오류가 500이 될 수 있고 보안 필터 오류도 별도다. 구현 시 통일한다. Redis만 실패한 경우 Snapshot은 200 + UNKNOWN으로 제공한다.

## 3. 대시보드

### GET /api/monitoring/dashboard/summary — 신규

Query 없음. 본인의 현재 배정 중 종료되지 않은 대상자 수를 반환한다. PENDING/PAUSED도 관리 수에 포함하되 실시간 위험 분류는 UNKNOWN이다. ENDED/DECEASED는 기본 목록에서 제외한다.

```json
{
  "totalSubjects": 8,
  "dangerCount": 2,
  "warningCount": 2,
  "normalCount": 3,
  "unknownCount": 1,
  "serverTime": "2026-09-14T00:00:00Z"
}
```

전체 = 위험 + 주의 + 정상 + 알 수 없음. 최신 riskLevel로 분류하며 사건 처리 상태로 대체하지 않는다. UNKNOWN이 있으면 화면에 별도 수치/안내를 추가한다. NORMAL 카드에 합산하지 않는다. 검색·필터와 무관한 내 관리 전체 수이며 목록과 별도 조회 중 발생하는 순간적인 차이는 허용한다.

### GET /api/monitoring/subjects — 신규

| Query | 기본값·허용값 |
|---|---|
| query | 선택; 이름 또는 관리번호 검색, 공백 제거 후 1~100자 |
| riskLevel | 선택; NORMAL, WARNING, DANGER, UNKNOWN |
| sort | RISK_DESC 기본; NAME_ASC, LAST_ACTIVITY_ASC |
| cursor, size | 공통 페이지 규칙 |

```json
{
  "items": [{
    "subjectId": "101", "assignmentId": "501", "householdId": "H001",
    "name": "김영숙", "age": 82, "addressSummary": "서울시 노원구 상계동",
    "serviceStatus": "ACTIVE", "riskScore": 87, "riskLevel": "DANGER",
    "snapshotStatus": "AVAILABLE", "observedAt": "2026-09-13T23:59:30Z",
    "lastActivityAt": "2026-09-13T10:00:00Z", "openIncidentCount": 3
  }],
  "nextCursor": null, "hasNext": false, "serverTime": "2026-09-14T00:00:00Z"
}
```

위험 정렬: DANGER → WARNING → NORMAL → UNKNOWN, 동일 등급은 점수 내림차순, 동률은 subjectId 오름차순. NAME_ASC는 이름 오름차순 후 ID, LAST_ACTIVITY_ASC는 오래된 활동 우선 후 ID이며 null은 마지막이다. 변하는 Snapshot 기반 페이지는 전체 정렬 스냅샷을 보장하지 않으므로 UI는 subjectId로 중복 제거하고 실시간 갱신 시 첫 페이지를 재조회한다.

`openIncidentCount`는 목표 OPEN/ACKNOWLEDGED 사건 수다. 카드의 ‘이상 3건’ 문구는 ‘미처리 사건 3건’으로 명확히 한다. 레거시 CONFIRMED/FALSE_POSITIVE는 미처리로 자동 재분류하지 않는다.

이름 검색은 암호화 컬럼 LIKE로 구현할 수 없다. 1차 계약은 정규화된 이름 **완전 일치** 또는 관리번호 완전 일치다. 이름 검색용 keyed blind index를 별도 저장하고 권한 범위 안에서 조회한다. 부분 이름 검색은 후속 기능이다. 색상·이름 첫 글자·상대시간 표시는 프런트 책임이다.

## 4. 대상자 상세

### GET /api/monitoring/subjects/{subjectId} — 신규

```json
{
  "subjectId": "101", "householdId": "H001", "subjectNumber": "SUB-000101",
  "name": "김영숙", "age": 82, "phone": "010-2341-5678",
  "address": "서울시 노원구 상계동 123-4", "serviceStatus": "ACTIVE",
  "totalIncidentCount": 3, "openIncidentCount": 3,
  "assignment": {"id": "501", "memo": "독거, 자녀 연락처 보유", "revision": "1"},
  "riskPolicy": {"id": "12", "version": 2, "name": "기본 위험 정책"},
  "serverTime": "2026-09-14T00:00:00Z"
}
```

totalIncidentCount는 대상자 연결이 확정된 전체 사건 수(종료·오탐 포함)다. 감지 중복 메시지 수가 아니다. 화면 ‘누적 이상’은 이 정의를 사용한다. 공동 담당일 때 memo는 로그인한 담당자의 배정 메모다. nullable 연락처/정책은 null을 허용한다. birthDate 원본은 나이 표시에 필수 노출하지 않는다.

### GET /api/monitoring/subjects/{subjectId}/snapshot — 신규

```json
{
  "subjectId": "101", "householdId": "H001", "snapshotStatus": "AVAILABLE",
  "revision": "182736", "observedAt": "2026-09-13T23:59:30Z",
  "expiresAt": "2026-09-14T00:01:30Z",
  "riskScore": 87, "riskLevel": "DANGER", "dataStatus": "LIVE",
  "activePowerW": 61.82, "lastActivityAt": "2026-09-13T10:00:00Z",
  "appliances": [], "latestDetection": {
    "eventId": "cf8615c3-1ae6-4d83-9b74-d4ca5f60f83b",
    "eventType": "ROUTINE_MISSED", "occurredAt": "2026-09-13T23:00:00Z",
    "description": "예상 시각까지 일상 활동이 감지되지 않았습니다."
  },
  "serverTime": "2026-09-14T00:00:00Z"
}
```

appliances 각 항목: applianceType(string), isOn(boolean), probability(number 0~1), threshold(number 0~1), confirmedAt(ISO 시각). latestDetection은 PostgreSQL 최근 이벤트의 별도 과거 근거이며 nullable이다. 현재 Snapshot의 직접 판단 근거임을 보장하지 않으므로 시각을 함께 표시한다. 기존 v1에는 가전 유형이 없어 ‘냉장고 미작동’으로 임의 변환하지 않는다.

키 없음·만료·장애 시 snapshotStatus=UNKNOWN, revision/observedAt/expiresAt/riskScore/riskLevel/dataStatus/activePowerW/lastActivityAt=null, appliances=[]로 반환한다. latestDetection은 과거 기록으로 반환 가능하다. AVAILABLE이어도 미학습 등으로 riskScore/riskLevel은 null일 수 있다. 요약 분류에서는 이를 UNKNOWN으로 집계한다.

새로고침 버튼은 상세·Snapshot·그래프·이벤트 첫 페이지를 다시 GET한다. 분석 재실행 API를 호출하지 않는다. 화면은 expiresAt이 지나면 새 SSE가 없어도 상태를 UNKNOWN으로 바꾸고 재조회한다.

### GET /api/monitoring/subjects/{subjectId}/power-usage?date=2026-09-14 — 신규

date 필수(Asia/Seoul 기준 날짜). 오늘 이후 날짜는 400. 응답은 해당 현지 날짜의 시간별 구간을 순서대로 반환하며 데이터가 없는 구간도 포함한다.

```json
{
  "subjectId": "101", "date": "2026-09-14", "timezone": "Asia/Seoul", "interval": "PT1H",
  "points": [{
    "hourStart": "2026-09-13T15:00:00Z", "energyWh": 61.820,
    "avgActivePowerW": 61.820, "maxActivePowerW": 105.100,
    "sampleCount": 3600, "coverageRatio": 1.0000, "dataStatus": "COMPLETE",
    "aggregationVersion": "v1"
  }],
  "serverTime": "2026-09-14T00:00:00Z"
}
```

예시는 1개 구간만 표기했다. 실제는 24개 구간이다. dataStatus=COMPLETE(coverageRatio=1), PARTIAL(0보다 크고 1 미만), MISSING(집계 없음/정상 샘플 0), NOT_YET(미래 구간). MISSING/NOT_YET의 전력값은 null이며 0을 그리지 않는다. 숫자는 화면용 소수이며 저장·집계는 BigDecimal이다.

화면의 ‘사용량(W)’ 단위를 정정한다. 기본 막대는 **평균 전력(W)** = avgActivePowerW, 실제 사용 전력량을 표시하려면 energyWh와 Wh를 사용한다. 2시간마다 축 레이블을 생략해도 API는 1시간 구간이다. 막대의 빨간색을 위험 등급으로 칠할 근거는 이 집계에 없으므로 기본 단색을 사용한다.

### GET /api/monitoring/subjects/{subjectId}/analysis-events — 신규

Query: from, to(선택, YYYY-MM-DD; 둘 다 생략 시 오늘 포함 7일; 함께 전달; 양끝 현지 날짜 포함, 최대 90일), cursor, size. occurredAt 내림차순·eventId 내림차순. DB는 **analysis_event**를 조회한다.

```json
{
  "items": [{
    "eventId": "cf8615c3-1ae6-4d83-9b74-d4ca5f60f83b",
    "incidentId": "8d0d5a22-979e-4592-b80b-58a287bba981",
    "occurredAt": "2026-09-13T23:00:00Z", "eventType": "ROUTINE_MISSED",
    "score": 87, "riskLevel": null, "applianceType": null,
    "description": "예상 시각까지 일상 활동이 감지되지 않았습니다.",
    "reason": {"expected_until": "08:00:00"}
  }],
  "nextCursor": null, "hasNext": false, "serverTime": "2026-09-14T00:00:00Z"
}
```

v1 riskLevel은 없으므로 null이다. 현재 정책의 임계치로 과거 등급을 추정하지 않는다. incidentId는 지연 이벤트 등 사건 미생성 시 null이다. reason은 화면에 필요한 허용 필드만 반환한다.

현재 이벤트는 정상 일일 기록을 발행하지 않는다. 와이어프레임의 ‘정상 패턴 유지’ 일별 행은 이 API로 생성할 수 없다. 1차 화면은 실제 감지 기록만 표시하고 빈 날짜는 ‘감지 기록 없음’으로 표시한다. 일별 정상 판정이 반드시 필요하면 분석 서비스의 일별 요약 계약 및 별도 영속 조회 모델이 선행되어야 한다. Redis의 하루 마지막 값이나 이벤트 부재로 정상 기록을 만들지 않는다.

### PATCH /api/monitoring/subject-assignments/{assignmentId} — 신규

```json
{"memo": "자녀에게 연락 후 방문 예정", "expectedRevision": "1"}
```

200: `{"assignmentId":"501","memo":"자녀에게 연락 후 방문 예정","revision":"2","updatedAt":"2026-09-14T00:00:00Z"}`. 본인의 현재 배정만 수정 가능. memo 필수 문자열, 최대 2000자, 빈 문자열은 지우기. 이미 해제된 배정은 409.

## 5. 대상자 추가

### POST /api/monitoring/subject-candidates/search — 신규

검색어가 URL·접근 로그에 남지 않도록 POST를 사용한다. 요청 본문도 로그에서 제외한다.

```json
{"name": "홍길동", "birthDate": null, "subjectNumber": null, "cursor": null, "size": 20}
```

name 또는 subjectNumber 중 하나 필수; name은 공백 제거 후 1~100자 완전 일치, subjectNumber는 최대 50자 완전 일치. birthDate는 YYYY-MM-DD 선택 필터로 name 검색과 함께만 사용한다. 두 검색키 동시 전달은 400. 본인 등록 가능 지역의 기존 ACTIVE/PENDING/PAUSED 대상자만 반환한다.

```json
{
  "items": [{
    "subjectId": "202", "name": "홍길동", "age": 74,
    "addressSummary": "서울시 마포구 서교동", "serviceStatus": "ACTIVE",
    "alreadyAssigned": false
  }],
  "nextCursor": null, "hasNext": false, "serverTime": "2026-09-14T00:00:00Z"
}
```

화면의 ‘주민등록번호 앞 6자리’는 도메인에 없고 세기 모호성이 있다. 이 설계에서는 **이름/관리번호 + 필요 시 생년월일**로 문구를 수정한다. 주민번호를 새로 저장하지 않는다. 기존 DB는 care_subject를 기본 원본으로 정하며 외부 기관 DB를 뜻한다면 후보 조회 어댑터와 지역 권한 계약이 추가로 필요하다.

### GET /api/monitoring/risk-policies — 신규

Query 없음. 활성화하여 선택할 수 있는 불변 정책 버전 목록. 200:

```json
{
  "items": [{"id":"12","policyCode":"STANDARD","name":"기본 정책","version":2,
    "algorithmType":"SCORE_THRESHOLD","warningThreshold":40,"dangerThreshold":70,
    "minDurationSeconds":1800}],
  "serverTime":"2026-09-14T00:00:00Z"
}
```

점수 없는 정책은 임계치 null. 이 선택은 브라우저 알림만 거르는 설정이 아니라 대상자의 **분석 위험 정책** 선택이다. 화면 레이블을 ‘위험 판단 정책’으로 표시한다.

### POST /api/monitoring/subject-assignments — 신규

```json
{"subjectId":"202","riskPolicyId":"12","memo":"주 1회 안부 확인"}
```

subjectId/riskPolicyId 필수, memo 선택(기본 빈 문자열, 최대 2000자). staffId는 인증으로 결정한다. 201 + Location `/api/monitoring/subject-assignments/502`:

```json
{"assignmentId":"502","subjectId":"202","riskPolicyId":"12","revision":"1","assignedAt":"2026-09-14T00:00:00Z"}
```

대상자의 기존 정책이 선택과 같으면 배정만 생성한다. 다른 공동 담당자가 관리 중인 대상자의 정책을 등록 과정에서 바꾸지 않으며 다른 정책 선택은 409다. 미배정 대상의 정책 변경은 분석 적용 확인이 완료되어야 성공 처리한다. 즉시 확인할 수 없다면 이 API는 503을 반환하고 배정을 만들지 않는다. 분산 적용 요청은 멱등하게 처리하고 재시도 시 분석 원본 상태를 확인한다.

같은 담당자·대상자의 활성 배정이 있으면 409. 성공 후 목록·요약을 재조회한다. 관리 대상 배정만으로 PAUSED를 ACTIVE로 변경하지 않는다.

## 6. 설정

### GET /api/monitoring/me, PATCH /api/monitoring/me — 신규

GET 200:

```json
{"staffId":"10","displayName":"이수진","organizationName":"노원구 복지센터",
 "email":"sujin@example.org","status":"ACTIVE","revision":"1","serverTime":"2026-09-14T00:00:00Z"}
```

PATCH 요청: `{"displayName":"이수진","organizationName":"노원구 복지센터","expectedRevision":"1"}`. 두 문자열 모두 필수, 공백만 불가, 최대 100자. 200은 변경 후 GET 형식. 소속 표시명은 권한 부여 근거가 아니며 수정으로 지역 권한을 얻을 수 없다. email은 Keycloak 조회/검증된 claim에서 읽는 nullable 표시값이며 여기서는 수정하지 않는다. 비밀번호·이메일 변경은 인증 서비스 계정 화면 책임이다.

### GET /api/monitoring/me/notification-settings — 신규

```json
{
  "dangerEnabled": true, "warningEnabled": true,
  "dailySummaryEnabled": true, "dailySummaryTime": "09:00", "timezone": "Asia/Seoul",
  "soundEnabled": false, "revision": "1", "serverTime": "2026-09-14T00:00:00Z"
}
```

### PUT /api/monitoring/me/notification-settings — 신규

```json
{"dangerEnabled":true,"warningEnabled":true,"dailySummaryEnabled":true,
 "soundEnabled":false,"expectedRevision":"1"}
```

4개 boolean과 expectedRevision 필수. 200 변경 후 GET 형식. 요약 시간은 이 화면에서는 09:00 Asia/Seoul 고정·읽기 전용이다. 최초 기본값은 dangerEnabled=true, warningEnabled=true, dailySummaryEnabled=false, soundEnabled=false로 초기화한다.

danger/warning은 담당자 인앱 배너와 WEB_PUSH 수신에 적용한다. 데이터 조회와 사건 생성은 중단하지 않는다. 정확한 riskLevel이 없는 v1 ROUTINE_MISSED 알림은 레거시 수신 경로를 유지하며 임의 DANGER로 바꾸지 않는다. 정책 기반 알림 적용은 계약 확장 후다.

soundEnabled는 열린 화면의 알림음 재생 선호이며 브라우저 알림 권한/운영체제 소리를 제어하는 값이 아니다. 일일 요약은 전날 현지 날짜 사건 집계를 오전 9시에 담당자당 1회 발송한다. 수신자+집계일+채널 기준 중복 방지 작업과 사건 ID가 없는 요약 알림 저장 구조가 필요하다. 구현 전에는 dailySummaryEnabled=true 저장을 409로 거절하고 UI를 비활성 표시한다. 저장만 성공하고 실제 발송하지 않는 계약으로 배포하지 않는다.

### GET /api/monitoring/me/risk-policy-settings — 신규

이 화면의 임계치는 **향후 등록에 사용할 내 기본 위험 정책**이다. 기존 공동 관리 대상의 정책을 일괄 변경하지 않는다.

```json
{"defaultPolicyId":"12","policyVersion":2,"warningThreshold":40,"dangerThreshold":70,
 "minDurationSeconds":1800,"allowedDurationSeconds":[0,900,1800,3600],
 "scope":"FUTURE_ASSIGNMENTS","revision":"1","serverTime":"2026-09-14T00:00:00Z"}
```

### POST /api/monitoring/risk-policy-changes — 신규

```json
{"warningThreshold":40,"dangerThreshold":70,"minDurationSeconds":1800,
 "expectedRevision":"1","changeReason":"신규 등록 기본 기준 변경"}
```

모든 필드 필수. 0 ≤ warningThreshold < dangerThreshold ≤ 100. 지속 시간은 조회된 허용 목록 중 하나. changeReason은 공백 아닌 1~2000자(화면에 사유 입력 추가). 기존 정책 행을 수정하지 않고 분석 서비스에서 새 버전을 만든다.

202 + Location `/api/monitoring/risk-policy-changes/89bd7e48-8dda-4b9f-814a-9557ff486e28`:

```json
{"changeId":"89bd7e48-8dda-4b9f-814a-9557ff486e28","status":"PENDING"}
```

### GET /api/monitoring/risk-policy-changes/{changeId} — 신규

본인의 변경 작업만 조회. 200:

```json
{"changeId":"89bd7e48-8dda-4b9f-814a-9557ff486e28","status":"APPLIED",
 "policyId":"13","policyVersion":3,"failureCode":null,"serverTime":"2026-09-14T00:00:00Z"}
```

status=PENDING/APPLIED/FAILED. PENDING/FAILED는 policyId/policyVersion=null, 실패 시 failureCode 제공. 담당자별 진행 작업 1개만 허용(경합은 409). 분석 요청의 멱등 키는 changeId로 사용한다. APPLIED 이후에만 기본 정책 참조와 revision을 바꾸며 실패하면 기존 설정을 유지한다. 작업 저장소·재시도·완료 확인은 신규 구현이다. 프런트는 2초부터 최대 10초 간격으로 조회하고 화면 이탈 시 중단한다.

## 7. 기존 Push·알림 응답 계약

### POST /api/monitoring/push-subscriptions — 구현됨

```json
{
  "endpoint": "https://push-service.example/subscription/123",
  "expirationTime": null,
  "keys": {"p256dh": "브라우저가 생성한 공개키", "auth": "브라우저가 생성한 인증값"}
}
```

endpoint, keys.p256dh, keys.auth는 공백 아닌 필수 문자열. expirationTime은 null 또는 epoch milliseconds(Long). endpoint에 `< >`를 넣지 않는다. 201 Created, Location `/api/monitoring/push-subscriptions/1`, body `{"subscriptionId":1}`. 같은 endpoint는 현재 구현에서 갱신되고 응답도 201이다. 사용자 ID는 인증에서 결정한다.

신규 보완: HTTPS endpoint·유효한 key 형식 검증과 외부 요청 대상 검증을 추가한다. 현재 구현은 같은 endpoint의 소유자를 갱신할 수 있으므로 계정 전환 시 기존 사용자 구독 해제와 재등록 동작을 함께 확인한다.

### GET /api/monitoring/push-config — 신규

200 `{"enabled":true,"vapidPublicKey":"<base64url 공개키>"}`. 비활성 시 false와 null. private key는 반환하지 않는다. 이 API는 구독 생성에 필요한 공개 설정이며 인증된 사용자에게 제공한다.

### DELETE /api/monitoring/push-subscriptions/{subscriptionId} — 신규

본문 없음. 본인 소유 구독의 revokedAt을 기록하고 204. 동일 구독 반복 해제도 204, 없는 ID/타인 ID는 404. 브라우저 측 unsubscribe와 함께 호출한다.

### PUT /api/monitoring/notifications/{notificationId}/responses — 구현됨

```json
{"answer":"yes","source":"user","respondedAt":"2026-09-14T00:00:12Z"}
```

answer=yes(위험) 또는 no(위험하지 않음), source는 user만, respondedAt은 필수 ISO 시각. 200:

```json
{"id":"77f1c0fa-3b02-4d0a-91af-e198360f1785","status":"yes",
 "respondedAt":"2026-09-14T00:00:13Z","responseSource":"user"}
```

현재 Controller는 클라이언트 respondedAt을 형식 검증 후 서비스에 전달하지 않는다. 응답 시각·마감 판정은 **서버 시각**을 사용한다. 응답 status는 IncidentStatus가 아니라 yes/no다.

- 발송 성공 후 sentAt + 기본 30초가 responseDeadlineAt이다. 브라우저에서 알림을 본 시각 기준이 아니다.
- 클라이언트는 타이머 만료에 자동 yes 요청을 보내지 않는다. 서버 스케줄러(현재 기본 5초 간격)가 무응답을 yes/timeout으로 저장한다. 기한 후 실제 처리까지 지연될 수 있다.
- 기한이 지난 사용자 요청도 서버는 yes/timeout을 반환할 수 있다. no를 눌렀다고 no로 저장된 것으로 먼저 표시하지 않는다.
- 같은 기존 답변 재요청은 기존 결과 200, 다른 답변 재요청은 409. 발송되지 않은 알림은 409. 본인 알림 또는 현재 관리자 권한만 응답 가능하다.
- 현재 응답/만료는 OPEN 사건을 CONFIRMED 또는 FALSE_POSITIVE로 바꾼다. 담당자 업무 상태 전환은 도메인 설계의 별도 후속 변경이며 현재 API가 RESOLVED를 만든다고 명세하지 않는다.

### GET /api/monitoring/notifications/{notificationId} — 신규

본인 알림 조회(이 신규 API는 관리자도 타인 알림을 응답용 화면에서 열지 않음). 200:

```json
{"id":"77f1c0fa-3b02-4d0a-91af-e198360f1785",
 "incidentId":"8d0d5a22-979e-4592-b80b-58a287bba981","deliveryStatus":"SENT",
 "sentAt":"2026-09-14T00:00:00Z","responseDeadlineAt":"2026-09-14T00:00:30Z",
 "answer":null,"responseSource":null,"respondedAt":null,"canRespond":true,
 "serverTime":"2026-09-14T00:00:05Z"}
```

canRespond는 SENT + 미응답 + 기한 전일 때 true. 버튼 활성화는 참고값이며 최종 판정은 PUT의 서버 처리다. 클라이언트 남은 시간은 responseDeadlineAt - serverTime 기준으로 계산한다.

현재 Push payload는 notificationId, incidentId, householdId, title이다. 이름·연락처를 추가하지 않는다. 클릭 후 본 API와 사건 상세를 권한 확인하여 조회한다. 구독은 발송 경로를 등록하는 것으로, 서버가 실제 Push를 발송하려면 Web Push 활성화와 키 설정도 필요하다.

## 8. 기존 사건 API

### GET /api/monitoring/incidents?status=OPEN — 구현됨

status 선택: 현재 OPEN/CONFIRMED/FALSE_POSITIVE. 본인 household_access 범위, 관리자는 전체. 응답은 페이지 없는 배열:

```json
[{"id":"8d0d5a22-979e-4592-b80b-58a287bba981","householdId":"H001",
  "type":"ROUTINE_MISSED","status":"OPEN","openedAt":"2026-09-14T00:00:00Z","closedAt":null}]
```

### GET /api/monitoring/incidents/{incidentId} — 구현됨

200 객체: incident(위 목록 원소), score(integer), reason(object), actions(array), notifications(array).

actions 원소: id(number), type(string), actorType(SYSTEM/USER), actorId(nullable string), previousStatus(nullable), nextStatus, occurredAt.
notifications 원소: id(UUID 문자열), recipientUserId(string), deliveryStatus(PENDING/SENT/FAILED), answer(nullable yes/no), sentAt, responseDeadlineAt, respondedAt, responseSource(nullable user/timeout). 시각들은 미발송/미응답일 때 null이다. 알림 목록은 본인 발송만, 관리자는 전체 반환한다.

미존재 404, 타인 가구 사건은 현재 403. 신규 대상자 상세를 이 API의 데이터만으로 조립하지 않는다. 이름·연락처·전력·Snapshot은 신규 대상자 API 책임이다.

## 9. SSE 계약과 화면 동기화

### GET /api/monitoring/stream — 구현됨

요청: Accept=text/event-stream, Authorization=Bearer 토큰, 선택 Last-Event-ID=숫자 10진 문자열. 응답 200 Content-Type=text/event-stream. 기존 단일 연결을 유지한다.

```text
id: 1201
event: incident-opened
data: {"incidentId":"8d0d5a22-979e-4592-b80b-58a287bba981","householdId":"H001","type":"ROUTINE_MISSED","score":87,"status":"OPEN","openedAt":"2026-09-14T00:00:00Z","reason":{"expected_until":"09:00:00"}}

id: 1202
event: incident-updated
data: {"incidentId":"8d0d5a22-979e-4592-b80b-58a287bba981","notificationId":"77f1c0fa-3b02-4d0a-91af-e198360f1785","householdId":"H001","status":"CONFIRMED","answer":"yes","responseSource":"user","respondedAt":"2026-09-14T00:00:12Z"}

: keep-alive

```

- id는 UUID가 아니라 ui_outbox.id(BIGINT 문자열)다. heartbeat는 현재 기본 25초 주기의 주석이고 업무 이벤트·새 id가 아니다.
- 현재 서버는 Last-Event-ID보다 큰 이벤트를 권한에 따라 최대 1000개 재생한다. 누락 여부 표식·추가 페이지·완전 재생 보장은 없다.
- replay 전에 연결 등록하므로 실시간과 재생이 겹쳐 중복·역순이 가능하다. 관리자와 가구 접근 권한이 겹쳐도 같은 이벤트가 중복 전달될 수 있다.
- 현재 전달 권한은 household_access와 관리자다. 신규 staff_subject_assignment만 추가해서는 SSE를 받지 못하므로 권한 조회 경로도 확장해야 한다.
- 기존 incident-updated의 notificationId는 사건의 최초 처리자 알림일 수 있다. 수신자 자신의 응답용 ID라고 가정하지 않는다.

### 신규 이벤트 — 아직 미구현

| event | data 계약 | 화면 동작 |
|---|---|---|
| snapshot-updated | subjectId, householdId, revision, observedAt | 카드·상세 Snapshot·요약 재조회 |
| subject-updated | subjectId, changedAt | 대상자 기본정보·목록 재조회 |
| assignment-updated | subjectId, assignmentId, changeType(ASSIGNED/UNASSIGNED), changedAt | 목록·요약 재조회, 해제된 상세 닫기 |
| settings-updated | section(NOTIFICATION/RISK_POLICY/PROFILE), revision | 해당 설정 재조회 |

새 이벤트는 전체 개인정보 대신 변경 사실만 전달한다. snapshot-updated는 고빈도이므로 Redis 최신 상태와 별도 배포 경로에서 전송하고 **SSE id를 붙이지 않는다**. 재생하지 않으며 revision으로 최신성을 비교한다. 나머지는 ui_outbox ID로 식별하고 기존 트랜잭션에 기록한다. 개인 settings/assignment 이벤트 전달에는 현재 household 중심 outbox에 수신 담당자 지정 구조가 추가로 필요하다. 현재 스키마로 개인 설정을 가구 전체에 방송하지 않는다.

### 프런트 처리 순서

1. 인증 후 SSE 연결을 열고 이벤트를 수신하면서 각 화면 GET을 수행한다. GET 진행 중 관련 이벤트가 오면 완료 직후 재조회한다.
2. 사건/대상자 변경은 관련 GET을 300~1000ms 범위로 묶어 재조회한다. 점수만으로 카드 상태 전체를 덮어쓰지 않는다.
3. SSE id가 있으면 최근 처리 ID 집합으로 중복 제거한다. 숫자가 작다는 이유만으로 미처리 이벤트를 버리지 않는다. BIGINT 비교는 JS Number가 아닌 BigInt/문자열 비교를 사용한다.
4. 재연결할 때 Last-Event-ID를 전달하되 **항상 현재 화면의 API를 다시 조회**한다. 최대 1000개 재생과 커밋 순서 차이로 이력 완전성을 보장하지 않기 때문이다.
5. 연결이 끊기면 지수 백오프(1초~30초, jitter)로 재시도하고 화면에 연결 상태를 표시한다. 끊긴 동안 30초 간격으로 현재 화면 조회, 탭 숨김 시 중단. 연결 중에도 60초 간격/탭 복귀 시 전체 재조회로 누락을 복구한다.
6. 401이면 토큰 갱신 후 연결을 다시 만들고 실패하면 로그인 화면으로 이동한다. 만료·로그아웃·배정 해제 시 개인정보 화면을 지운다.

인증 헤더를 설정할 수 있는 fetch 스트리밍 클라이언트를 기준으로 한다. 기본 EventSource만으로 Bearer 헤더를 전달할 수 있다고 가정하지 않는다. URL query에 토큰을 넣지 않는다. 서버도 장기 연결의 토큰 만료·권한 회수를 반영해 연결을 종료하도록 보완한다.

다중 인스턴스에서는 현재 프로세스 내부 SseHub와 단일 publishedAt만으로 모든 연결에 전달할 수 없다. 공유 배포 경로·전달 권한 재검증·프록시 버퍼링 비활성화를 구현한다. Gateway의 기존 SSE 응답 타임아웃 해제는 유지한다.

배너의 Cancel은 로컬 알림 닫기다. 사건 종료 또는 위험하지 않음 응답을 자동 전송하지 않는다. 실제 응답 버튼을 누른 경우만 알림 응답 PUT을 호출한다.

## 10. 도메인·화면 보완 및 구현 우선순위

| 보완 사항 | 이유·필요 데이터 |
|---|---|
| 이름 검색용 keyed blind index | BYTEA 암호화 이름의 완전 일치 조회; 부분 검색은 미지원 |
| 담당자 지역별 후보 조회 권한 | 배정 전 대상자 검색 범위가 기존 assignment만으로 표현되지 않음 |
| 담당자 기본 정책 참조 | 기존 CareSubject.riskPolicyId와 별개인 미래 등록 기본값 |
| 설정/프로필/배정 revision | 동시 수정 유실 방지 |
| 일일 요약·알림음 설정 | NotificationPreference의 enabled/minimumSeverity만으로 표현되지 않음 |
| 정책 변경 작업 저장소 | PENDING/APPLIED/FAILED, 원격 적용 멱등성·복구 |
| 사건 없는 일일 요약 발송 | 기존 NotificationDelivery.incidentId 필수 구조와 충돌 |
| Snapshot 소비·배포 | 현재 Redis 설정만 있으며 최신 상태 API 구현 없음 |
| 시간별 집계 공급 계약 | 현재 monitoring-service가 그래프 데이터를 받는 경로 없음 |
| 개인별 SSE 수신 대상 | 현재 가구 기반 UiOutbox만으로 설정·배정 변경을 안전하게 전달할 수 없음 |

구현 순서:

1. 기존 Push·알림 응답·SSE 계약을 프런트와 맞춘다. 본인 알림 조회/공개키/구독 해제를 추가한다.
2. 담당자·대상자·배정 기반과 이름 검색을 구현한 뒤 대시보드·상세 기본정보·등록·계정 API를 제공한다. 작업 중인 Entity 존재만으로 API 구현 완료로 취급하지 않는다.
3. Snapshot·시간 집계 계약을 연결하여 실시간 점수와 그래프를 제공한다. 준비 전에는 UNKNOWN/데이터 없음으로 표시한다.
4. 설정 저장과 정책 비동기 적용, 요약 작업, 개인 SSE를 구현한다. 기능 준비 상태에 맞춰 UI를 활성화한다.
5. 담당자 사건 확인·종료 UI를 설계할 때 상태 전환 API를 별도로 명세한다. 현재 와이어프레임에는 해당 버튼이 없어 이번에 임의 노출하지 않는다.

검증 기준: 네 화면의 각 값이 API 필드로 매핑되는지, 타 담당자 조회/수정이 차단되는지, UUID·BIGINT·시각 형식, 이름 검색 범위, 중복 배정, 수정 revision 충돌, 정책 원격 실패 복구, 30초 마감 경합, Redis 만료, 데이터 없음과 0 구분, SSE 재생 중복·역순·1000개 초과·재연결을 확인한다. 실제 구현 변경 시 PostgreSQL/Redis 통합 검증과 기존 테스트를 수행한다. 이번 문서 작성 단계에서는 실행 코드·마이그레이션을 변경하지 않았다.

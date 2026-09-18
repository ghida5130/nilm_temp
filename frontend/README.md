# On:마음 프론트엔드

`tempfrontend`의 담당자 대시보드·대상자 모바일 화면 디자인을 참고하고, 현재 Java 백엔드의 API에 연결한 React 화면입니다. 시연용 `/api/mvp` 및 가상 데이터는 사용하지 않습니다.

## 실행

```powershell
cd frontend
npm ci
npm run dev
```

개발 서버는 `/api` 요청을 `http://localhost:8080`으로 전달합니다. 배포 nginx는 기존 `api-gateway:8080` 프록시와 SSE 버퍼링 해제 설정을 사용합니다.

- `/`: 서비스 선택
- `/staff`: 담당자 로그인 및 대시보드
- `/staff?view=subjects`: 대상자 검색·필터·등록
- `/staff?view=alerts`: 대상자별 최근 알림
- `/staff/subjects/{subjectId}`: 연락처, 위험 점수 추이, 날짜별 전력 사용량, 최근 7일 이상 징후 및 응답 기록
- `/user`: 대상자 로그인, 담당자 연락처, 외출 즉시 시작·예약·해제
- `/user?notificationId={id}`: 수신한 알림에 대한 예·아니오 응답
- `/user/orange-preview`: 기존 대상자 배치·글자 크기를 유지하고 주황색만 적용한 임시 비교 화면

## 브랜드 색상과 대상자 디자인 비교

공통 색상은 `src/Brand.css`에서 로고 색상 `#D97E3F`와 밝은 배경·짙은 갈색 계층으로 관리합니다. 버튼의 주황색 배경에는 짙은 글자를 사용하고, 위험 응답은 붉은색으로 구별합니다. 담당자 위험도 상태의 의미를 전달하는 색상은 유지합니다.

`/user`는 고령자를 고려한 기본 화면입니다. 본문 18px, 주요 버튼 높이 58px 이상을 기준으로 구성하고 장식 그림·반복 안내·수동 새로고침을 줄였습니다. 외출 상태는 테두리 없는 정보 영역으로, 동작은 테두리와 배경이 있는 버튼으로 구별합니다. 자주 쓰는 외출 시간은 2열 선택 버튼으로 제공하고 직접 입력·출발 예약은 펼침 영역에 모았습니다. 자동 조회, 알림 응답, 외출 예약 및 담당자 전화 기능은 유지합니다. 대상자 로그인 화면도 가독성을 맞췄습니다.

디자인 비교 시 같은 대상자 계정으로 `/user`와 `/user/orange-preview`를 확인합니다. 비교 화면은 일반 메뉴에 노출하지 않습니다. 두 화면은 동일한 API와 상태 처리 코드를 사용하며, 비교 화면에서도 외출 설정·알림 응답은 실제 서버에 저장됩니다. 고령자 전용 스타일은 `src/Senior.css`로 분리되어 비교 화면에는 적용되지 않습니다.

## 인증 및 실행 전제

실제 등록된 계정으로 `POST /api/auth/login`을 호출합니다. 토큰은 탭의 sessionStorage에 보관하며 만료 응답 시 `POST /api/auth/refresh`로 갱신합니다. 로그아웃은 해당 탭의 토큰을 제거합니다.

담당자와 대상자 조회는 JWT의 subject로 계정을 식별하므로, 게이트웨이와 각 서비스의 인증 구성이 활성화되어 있고 Keycloak 및 해당 monitoring DB 프로필이 준비되어 있어야 합니다. 보안 비활성 설정에서는 백엔드가 JWT principal을 만들지 않으므로 토큰을 전달해도 해당 조회가 401로 거절될 수 있습니다. 프론트엔드는 이 오류를 표시하며 테스트 계정으로 우회하지 않습니다. 회원가입만으로 monitoring DB의 담당자·대상자 프로필이 자동 생성된다고 가정하지 않습니다.

## 연결한 API

| 용도 | API |
| --- | --- |
| 로그인 / 갱신 | `POST /api/auth/login`, `POST /api/auth/refresh` |
| 담당 대상자 | `GET /api/monitoring/dashboard` |
| 담당자 실시간 상태 | `GET /api/monitoring/stream`, `subject-status` 이벤트 |
| 대상자 등록 | `POST /api/monitoring/subjects` |
| 전력 사용량 | `GET /api/monitoring/subjects/{id}/power-usage?date=YYYY-MM-DD` |
| 이상 징후 기록 | `GET /api/monitoring/subjects/{id}/events?size=20&cursor=...` |
| 대상자 홈 | `GET /api/monitoring/my-dashboard` |
| 외출 설정 | `PUT /api/monitoring/my-dashboard/away-mode` |
| 알림 응답 | `PUT /api/monitoring/notifications/{id}/responses` |

담당자 SSE는 Bearer 인증을 위해 fetch 스트림을 사용합니다. 대상자별 version으로 오래된 이벤트를 무시하고, 재연결 때 전체 목록을 다시 조회합니다. 화면이 보이는 동안 60초 간격의 목록 보정 조회와 탭 복귀 시 조회도 수행합니다. 상세 전력·이벤트 영역은 대상자 version 변경 시 갱신됩니다.

## 현재 백엔드 제약에 따른 제외 기능

- 대상자용 SSE 경로는 현재 소스에 없습니다. 대상자 홈은 30초 간격 및 탭 복귀 시 조회합니다.
- 푸시 구독 등록은 로그인한 사용자 대신 `app.push.test-auth-sub`에 고정됩니다. 사용자별 정상 등록을 보장할 수 없어 등록 버튼과 공개키 입력 화면을 제공하지 않습니다. 기존 구독의 수신은 서비스 워커에서 유지하며 알림을 누르면 앱에서 로그인 후 응답합니다.
- 알림 응답 API는 현재 사용자 소유권을 검증하지 않습니다. 배포 전 백엔드 보완이 필요한 부분이며 프론트엔드는 수신한 notificationId에 대한 응답 UI만 제공합니다.
- 담당자 상황 종료, GPS 자동 외출, 임의 위험 점수, 기기 연결 상태, 일일 보고서, 조회할 수 없는 담당자 설정은 표시하지 않습니다.
- 서버 오류를 빈 데이터나 안전 상태로 바꾸지 않으며, 이전 조회 정보가 있으면 오류와 함께 표시합니다.

서비스 워커는 API 또는 개인정보 응답을 캐시하지 않습니다. 기존 셸 캐시를 정리해 이전 시연 화면이 남지 않게 합니다. 변경 검증 시 빌드 명령은 실행하지 않습니다.

## 빌드 없이 확인

```powershell
npm run lint
npx tsc --noEmit -p tsconfig.app.json
node --check public/sw.js
node --test tests/api-stream.test.mjs
```

SSE 및 인증 검증은 Node 22.18 이상에서 실행합니다. 테스트는 가짜 fetch 응답을 사용하며 실제 계정이나 서버 데이터를 변경하지 않습니다.

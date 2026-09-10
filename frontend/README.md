# On:마음 PWA 알림

스마트폰에 설치하고 Web Push 알림을 받을 수 있는 React PWA입니다. 백엔드의 기존 구독·응답 API 계약에 맞춰 동작합니다.

## 공개키 설정

백엔드 `WEB_PUSH_VAPID_PUBLIC_KEY`와 같은 공개키를 `frontend/.env.local`에 설정합니다.

```env
VITE_WEB_PUSH_PUBLIC_KEY=...
```

환경변수가 없으면 화면의 공개키 입력란을 이용할 수 있습니다. VAPID 비밀키는 `WEB_PUSH_VAPID_PRIVATE_KEY`로 백엔드에만 보관해야 합니다.

## 연결 API

- 구독 등록: `POST /api/monitoring/push-subscriptions`
- 알림 응답: `PUT /api/monitoring/notifications/{notificationId}/responses`

서비스 워커는 백엔드 push payload의 `notificationId`, `incidentId`, `householdId`, `title`, `expiresAt`을 사용합니다. 알림의 `예` 또는 `아니오` 버튼을 누르면 `{ answer, source: "user", respondedAt }` 형식으로 응답 API를 호출합니다.

## 로컬 실행

```powershell
npm ci
npm run dev
```

웹 푸시는 보안 컨텍스트에서만 동작합니다. 로컬에서는 `http://localhost:5173`, 스마트폰에서는 유효한 HTTPS 주소를 사용해야 합니다. iPhone/iPad는 Safari에서 홈 화면에 추가한 뒤 해당 앱에서 알림을 등록해야 합니다.

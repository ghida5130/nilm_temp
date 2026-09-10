const RESPONSE_API = '/api/monitoring/notifications'
const CACHE_NAME = 'onmaeum-shell-v1'
const APP_SHELL = ['/', '/manifest.webmanifest', '/app-icon.svg']

function parsePayload(event) {
  if (!event.data) return {}
  try {
    return event.data.json()
  } catch {
    return { title: 'On:마음 알림', body: event.data.text() }
  }
}

async function notifyClients(message) {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  clients.forEach((client) => client.postMessage({ type: 'PUSH_RESPONSE_RESULT', message }))
}

async function openOrFocus(url) {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  const targetUrl = new URL(url, self.location.origin)
  const current = clients.find((client) => new URL(client.url).origin === targetUrl.origin)
  if (current) {
    await current.focus()
    if ('navigate' in current) await current.navigate(targetUrl.href)
    return
  }
  await self.clients.openWindow(targetUrl.href)
}

async function sendResponse(notificationId, answer, data) {
  const response = await fetch(`${RESPONSE_API}/${encodeURIComponent(notificationId)}/responses`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer, source: 'user', respondedAt: new Date().toISOString() }),
  })

  if (!response.ok) {
    const message = response.status === 401 || response.status === 403
      ? '로그인 후 응답을 완료해 주세요.'
      : '응답을 전송하지 못했습니다. 앱에서 다시 확인해 주세요.'
    await notifyClients(message)
    await openOrFocus(`/?notificationId=${encodeURIComponent(notificationId)}&answer=${answer}`)
    return
  }

  const answerLabel = answer === 'yes' ? '예' : '아니오'
  await notifyClients(`‘${answerLabel}’ 응답이 완료되었습니다.`)
  await self.registration.showNotification('응답이 전달되었어요', {
    body: `‘${answerLabel}’로 답변했습니다.`,
    icon: '/app-icon.svg',
    tag: `response-${data.incidentId || notificationId}`,
  })
}

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()))
})
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const requestUrl = new URL(event.request.url)
  if (event.request.method !== 'GET' || requestUrl.origin !== self.location.origin || requestUrl.pathname.startsWith('/api/')) return
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const copy = response.clone()
        void caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy))
        return response
      })
      .catch(() => caches.match(event.request).then((cached) => cached || caches.match('/'))),
  )
})

self.addEventListener('push', (event) => {
  const data = parsePayload(event)
  const title = data.title || 'On:마음 안심 알림'
  const body = data.body || '일상 패턴 이상이 감지되었습니다. 현재 상황을 확인해 주세요.'
  const notificationId = data.notificationId
  const options = {
    body,
    icon: '/app-icon.svg',
    badge: '/app-icon.svg',
    tag: data.incidentId ? `incident-${data.incidentId}` : undefined,
    renotify: Boolean(data.incidentId),
    requireInteraction: true,
    actions: notificationId ? [
      { action: 'no', title: '아니오' },
      { action: 'yes', title: '예' },
    ] : [],
    data: {
      notificationId,
      incidentId: data.incidentId,
      householdId: data.householdId,
      expiresAt: data.expiresAt,
      url: data.url || '/',
    },
  }
  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const data = event.notification.data || {}
  if ((event.action === 'yes' || event.action === 'no') && data.notificationId) {
    event.waitUntil(sendResponse(data.notificationId, event.action, data))
    return
  }
  event.waitUntil(openOrFocus(data.url || '/'))
})

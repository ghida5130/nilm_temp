function parsePayload(event) {
  try { return event.data?.json() || {} }
  catch { return { title: 'On:마음 알림', body: event.data?.text() } }
}

function notificationUrl(data) {
  if (data.notificationId && /^\d+$/.test(String(data.notificationId))) {
    return `/user?notificationId=${encodeURIComponent(data.notificationId)}`
  }
  try {
    const url = new URL(data.url || '/user', self.location.origin)
    return url.origin === self.location.origin ? url.href : '/user'
  } catch { return '/user' }
}

self.addEventListener('install', (event) => event.waitUntil(self.skipWaiting()))
self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((key) => key.startsWith('onmaeum-shell-')).map((key) => caches.delete(key))))
    .then(() => self.clients.claim()))
})

self.addEventListener('push', (event) => {
  const data = parsePayload(event)
  event.waitUntil(Promise.all([
    self.registration.showNotification(data.title || 'On:마음 안심 알림', {
      body: data.body || '앱을 열어 알림을 확인해 주세요.',
      icon: '/app-icon.svg', badge: '/app-icon.svg',
      tag: data.notificationId ? `notification-${data.notificationId}` : undefined,
      requireInteraction: true,
      data: { url: notificationUrl(data) },
    }),
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      clients.forEach((client) => client.postMessage({ type: 'PUSH_RECEIVED', notificationId: data.notificationId }))
    }),
  ]))
})

self.addEventListener('pushsubscriptionchange', (event) => {
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
    clients.forEach((client) => client.postMessage({ type: 'PUSH_SUBSCRIPTION_CHANGED' }))
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil((async () => {
    const data = event.notification.data || {}
    const target = new URL(notificationUrl(data), self.location.origin)
    const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const client = clients.find((item) => new URL(item.url).pathname.startsWith('/user'))
    if (client && 'navigate' in client) {
      const navigated = await client.navigate(target.href)
      if (navigated) { await navigated.focus(); return }
    }
    await self.clients.openWindow(target.href)
  })())
})

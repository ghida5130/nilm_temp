import { useEffect, useMemo, useState } from 'react'
import './App.css'

type PushState = 'checking' | 'unsupported' | 'idle' | 'subscribed' | 'denied'
type Feedback = { tone: 'success' | 'error' | 'info'; message: string } | null

const SUBSCRIPTION_API = '/api/monitoring/push-subscriptions'
const PUBLIC_KEY_STORAGE = 'onmaeum.webPushPublicKey'

function urlBase64ToUint8Array(value: string) {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  return Uint8Array.from([...rawData].map((character) => character.charCodeAt(0)))
}

async function readError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string; error?: string }
    return body.message || body.error || `요청에 실패했습니다. (${response.status})`
  } catch {
    return `요청에 실패했습니다. (${response.status})`
  }
}

function Icon({ name }: { name: 'bell' | 'shield' | 'check' | 'phone' | 'key' | 'lock' | 'arrow' }) {
  const paths = {
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></>,
    shield: <><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10"/><path d="m9 12 2 2 4-4"/></>,
    check: <path d="m5 12 4 4L19 6"/>,
    phone: <><rect width="14" height="20" x="5" y="2" rx="2"/><path d="M12 18h.01"/></>,
    key: <><circle cx="7.5" cy="15.5" r="5.5"/><path d="m11.5 11.5 8-8M15 8l3 3M17 6l3 3"/></>,
    lock: <><rect width="18" height="11" x="3" y="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></>,
    arrow: <><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></>,
  }
  return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>
}

function App() {
  const envPublicKey = (import.meta.env.VITE_WEB_PUSH_PUBLIC_KEY as string | undefined)?.trim() ?? ''
  const [publicKey, setPublicKey] = useState(() => envPublicKey || localStorage.getItem(PUBLIC_KEY_STORAGE) || '')
  const [pushState, setPushState] = useState<PushState>('checking')
  const [isWorking, setIsWorking] = useState(false)
  const [showSetup, setShowSetup] = useState(!envPublicKey)
  const [feedback, setFeedback] = useState<Feedback>(null)

  useEffect(() => {
    async function checkSubscription() {
      if (!window.isSecureContext || !('serviceWorker' in navigator) || !('PushManager' in window)) {
        setPushState('unsupported')
        return
      }
      if (Notification.permission === 'denied') {
        setPushState('denied')
        return
      }
      try {
        const registration = await navigator.serviceWorker.ready
        setPushState((await registration.pushManager.getSubscription()) ? 'subscribed' : 'idle')
      } catch {
        setPushState('idle')
      }
    }
    void checkSubscription()
  }, [])

  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const receiveMessage = (event: MessageEvent<{ type?: string; message?: string }>) => {
      if (event.data?.type !== 'PUSH_RESPONSE_RESULT') return
      setFeedback({
        tone: event.data.message?.includes('완료') ? 'success' : 'error',
        message: event.data.message || '알림 응답 처리 결과를 확인했습니다.',
      })
    }
    navigator.serviceWorker.addEventListener('message', receiveMessage)
    return () => navigator.serviceWorker.removeEventListener('message', receiveMessage)
  }, [])

  const status = useMemo(() => {
    if (pushState === 'checking') return { label: '확인 중', detail: '브라우저 상태를 확인하고 있어요.' }
    if (pushState === 'subscribed') return { label: '알림 연결됨', detail: '이 기기로 중요한 소식을 받을 수 있어요.' }
    if (pushState === 'denied') return { label: '알림 차단됨', detail: '브라우저 설정에서 알림 권한을 허용해 주세요.' }
    if (pushState === 'unsupported') return { label: '지원되지 않음', detail: 'HTTPS 환경의 최신 브라우저에서 이용해 주세요.' }
    return { label: '연결 필요', detail: '한 번만 등록하면 바로 알림을 받을 수 있어요.' }
  }, [pushState])

  async function subscribe() {
    setFeedback(null)
    if (!window.isSecureContext) {
      setFeedback({ tone: 'error', message: '알림 등록은 HTTPS 또는 localhost에서만 사용할 수 있습니다.' })
      return
    }
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      setPushState('unsupported')
      setFeedback({ tone: 'error', message: '이 브라우저는 웹 푸시 알림을 지원하지 않습니다.' })
      return
    }
    const normalizedPublicKey = publicKey.trim()
    if (!normalizedPublicKey) {
      setShowSetup(true)
      setFeedback({ tone: 'info', message: '백엔드와 같은 VAPID 공개키를 먼저 입력해 주세요.' })
      return
    }

    setIsWorking(true)
    try {
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') {
        setPushState(permission === 'denied' ? 'denied' : 'idle')
        throw new Error('알림 권한이 허용되지 않았습니다.')
      }
      const registration = await navigator.serviceWorker.ready
      let subscription = await registration.pushManager.getSubscription()
      if (!subscription) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(normalizedPublicKey),
        })
      }
      const response = await fetch(SUBSCRIPTION_API, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(subscription.toJSON()),
      })
      if (!response.ok) throw new Error(await readError(response))
      localStorage.setItem(PUBLIC_KEY_STORAGE, normalizedPublicKey)
      setPushState('subscribed')
      setFeedback({ tone: 'success', message: '알림 등록이 완료되었습니다. 이제 이 기기로 알려드릴게요.' })
    } catch (error) {
      setFeedback({ tone: 'error', message: error instanceof Error ? error.message : '알림 등록 중 문제가 발생했습니다.' })
    } finally {
      setIsWorking(false)
    }
  }

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="/" aria-label="On:마음 홈"><span className="brand-mark"><Icon name="shield" /></span><span>On:마음</span></a>
        <span className="service-badge"><span /> 안심 모니터링</span>
      </header>

      <section className="hero-section">
        <div className="eyebrow"><Icon name="bell" /> 놓치지 않는 안심 알림</div>
        <h1>소중한 일상,<br /><em>바로 곁에서</em> 알려드려요.</h1>
        <p className="hero-copy">평소와 다른 생활 패턴이 감지되면 스마트폰으로 빠르게 알려드리고, 바로 응답할 수 있어요.</p>
        <div className="phone-visual" aria-hidden="true">
          <div className="orb orb-one" /><div className="orb orb-two" />
          <div className="phone-frame"><div className="phone-speaker" /><div className="phone-screen">
            <div className="screen-time">9:41</div>
            <div className="notification-preview">
              <div className="preview-head"><span className="preview-logo"><Icon name="bell" /></span><b>On:마음</b><small>지금</small></div>
              <strong>일상 패턴 이상이 감지되었어요</strong><p>현재 상황을 확인해 주세요.</p>
              <div className="preview-actions"><span>아니오</span><span>예</span></div>
            </div>
            <div className="screen-shield"><Icon name="shield" /></div><p className="screen-caption">늘 연결되어 있어요</p>
          </div></div>
        </div>
      </section>

      <section className="connect-section" aria-labelledby="connect-title">
        <div className={`status-pill status-${pushState}`}><span className="status-dot" /> {status.label}</div>
        <h2 id="connect-title">내 휴대폰에 알림 연결하기</h2><p>{status.detail}</p>
        {showSetup && <div className="key-field">
          <label htmlFor="public-key"><Icon name="key" /> VAPID 공개키</label>
          <input id="public-key" value={publicKey} onChange={(event) => setPublicKey(event.target.value)} placeholder="백엔드의 WEB_PUSH_VAPID_PUBLIC_KEY 입력" autoComplete="off" spellCheck="false" />
          <small><Icon name="lock" /> 비밀키는 서버에서만 안전하게 보관됩니다.</small>
        </div>}
        {feedback && <div className={`feedback feedback-${feedback.tone}`} role="status">{feedback.message}</div>}
        <button className="primary-button" type="button" onClick={() => void subscribe()} disabled={isWorking || pushState === 'unsupported' || pushState === 'checking'}>
          {isWorking ? '연결하고 있어요…' : pushState === 'subscribed' ? '알림 다시 등록하기' : '알림 등록하기'}{!isWorking && <Icon name="arrow" />}
        </button>
        {!envPublicKey && <button className="text-button" type="button" onClick={() => setShowSetup((value) => !value)}>{showSetup ? '공개키 입력란 접기' : '공개키 직접 입력하기'}</button>}
        <div className="trust-row"><span><Icon name="check" /> 언제든 브라우저에서 해제</span><span><Icon name="check" /> 필요한 알림만 전송</span></div>
      </section>

      <section className="steps-section" aria-labelledby="steps-title">
        <div className="section-heading"><span>이렇게 동작해요</span><h2 id="steps-title">3단계로 간단하게</h2></div>
        <ol className="steps-list">
          <li><span>1</span><div className="step-icon"><Icon name="phone" /></div><div><strong>알림 등록</strong><p>위 버튼을 누르고 알림 권한을 허용해요.</p></div></li>
          <li><span>2</span><div className="step-icon"><Icon name="bell" /></div><div><strong>이상 감지</strong><p>평소와 다른 패턴이 생기면 바로 알려드려요.</p></div></li>
          <li><span>3</span><div className="step-icon"><Icon name="check" /></div><div><strong>빠른 응답</strong><p>알림에서 예 또는 아니오로 간편하게 답해요.</p></div></li>
        </ol>
      </section>

      <footer><span className="footer-mark"><Icon name="shield" /></span><p><strong>On:마음</strong><br />가족의 평온한 일상을 연결합니다.</p></footer>
    </main>
  )
}

export default App

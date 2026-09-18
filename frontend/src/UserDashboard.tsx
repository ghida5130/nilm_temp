import { useCallback, useEffect, useRef, useState } from 'react'
import { api, json, message, time } from './api'
import Icon from './Icon'
import type { MyDashboard } from './types'

export default function UserDashboard({ onLogout }: { onLogout: () => void }) {
  const [data, setData] = useState<MyDashboard | null>(null)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [busy, setBusy] = useState(false)
  const [tab, setTab] = useState<'home' | 'away'>('home')
  const [duration, setDuration] = useState(60)
  const [start, setStart] = useState('')
  const [notificationId, setNotificationId] = useState(() => {
    const value = new URLSearchParams(window.location.search).get('notificationId')
    return value && /^\d+$/.test(value) ? value : null
  })
  const controller = useRef<AbortController | null>(null)
  const refresh = useCallback(async () => {
    controller.current?.abort()
    const request = new AbortController()
    controller.current = request
    try {
      const result = await api<MyDashboard>('/api/monitoring/my-dashboard', { signal: request.signal })
      if (!request.signal.aborted) { setData(result); setError('') }
    } catch (cause) { if (!request.signal.aborted) setError(message(cause)) }
  }, [])
  useEffect(() => {
    const initial = window.setTimeout(() => void refresh(), 0)
    const interval = window.setInterval(() => { if (document.visibilityState === 'visible') void refresh() }, 30000)
    const visible = () => { if (document.visibilityState === 'visible') void refresh() }
    const receive = (event: MessageEvent) => {
      if (event.data?.type === 'PUSH_RECEIVED' && /^\d+$/.test(String(event.data.notificationId))) {
        setNotificationId(String(event.data.notificationId))
      }
    }
    document.addEventListener('visibilitychange', visible)
    navigator.serviceWorker?.addEventListener('message', receive)
    return () => {
      controller.current?.abort(); window.clearTimeout(initial); window.clearInterval(interval)
      document.removeEventListener('visibilitychange', visible)
      navigator.serviceWorker?.removeEventListener('message', receive)
    }
  }, [refresh])
  async function away(enabled: boolean) {
    if (enabled && (!Number.isInteger(duration) || duration < 1 || duration > 1440)) { setFeedback('외출 시간을 1~1440분 사이로 입력해 주세요.'); return }
    const startsAt = enabled && start ? new Date(start) : new Date()
    if (enabled && start && startsAt.getTime() <= Date.now()) { setFeedback('예약 시작 시간은 현재보다 이후로 선택해 주세요.'); return }
    setBusy(true); setFeedback('')
    try {
      await api('/api/monitoring/my-dashboard/away-mode', json('PUT', enabled ? { enabled, ...(start ? { startsAt: startsAt.toISOString() } : {}), endsAt: new Date(startsAt.getTime() + duration * 60000).toISOString() } : { enabled }))
      setFeedback(enabled ? '외출 설정을 저장했습니다.' : '외출 모드를 해제했습니다.')
      await refresh()
    } catch (cause) { setFeedback(message(cause)) }
    finally { setBusy(false) }
  }
  async function answer(value: 'yes' | 'no') {
    if (!notificationId) return
    setBusy(true); setFeedback('')
    try {
      await api(`/api/monitoring/notifications/${notificationId}/responses`, json('PUT', { answer: value, source: 'user', respondedAt: new Date().toISOString() }))
      setNotificationId((current) => current === notificationId ? null : current)
      setFeedback(value === 'yes' ? '위험 상황이라는 응답을 전달했습니다.' : '안전하다는 응답을 전달했습니다.')
      const url = new URL(window.location.href)
      if (url.searchParams.get('notificationId') === notificationId) {
        url.searchParams.delete('notificationId'); url.searchParams.delete('answer')
      }
      window.history.replaceState(null, '', url)
    } catch (cause) { setFeedback(message(cause)) }
    finally { setBusy(false) }
  }
  const mode = data?.awayMode
  return <main className="mvp mvp-user"><header className="user-header"><a className="mvp-brand" href="/"><span className="brand-symbol"><Icon name="shield" /></span>On:마음</a><button className="text-button" onClick={onLogout}>로그아웃</button></header>
    <div className="user-content"><div className="user-greeting"><p>함께하는 안심 일상</p><h1>{data ? `${data.name}님, 안녕하세요` : '안녕하세요'}</h1><p>오늘도 편안한 하루 보내세요.</p></div>
      {error && <p className="error-box" role="alert">{error}<button className="secondary" onClick={() => void refresh()}>다시 시도</button></p>}
      {notificationId && <section className="mvp-card response-card"><Icon name="bell" /><h2>현재 위험한 상황인가요?</h2><p>받으신 알림에 응답해 주세요.</p><div className="mvp-actions"><button className="secondary" disabled={busy || !data} onClick={() => void answer('no')}>아니요, 안전해요</button><button className="danger-button" disabled={busy || !data} onClick={() => void answer('yes')}>예, 위험해요</button></div></section>}
      {tab === 'home' ? <>
        <section className="mvp-card user-status-card"><div className="status-illustration"><Icon name={mode?.enabled ? 'walk' : 'home'} /></div><h2>{!data ? '내 정보를 확인하고 있어요' : mode?.enabled ? '외출 중이에요' : mode?.scheduled ? '외출이 예약되어 있어요' : '외출 모드가 꺼져 있어요'}</h2><p>{mode?.enabled ? `종료 예정: ${time(mode.until)}` : mode?.scheduled ? `시작 예정: ${time(mode.startedAt)}` : '외출하실 때 알려주시면 돌봄에 도움이 돼요.'}</p><button disabled={!data} onClick={() => setTab('away')}><Icon name="walk" />외출 설정하기</button></section>
        <section className="mvp-card"><h2><Icon name="phone" />나의 복지담당자</h2>{data?.manager ? <><strong>{data.manager.name} 담당자</strong><p>{data.manager.phone ?? '등록된 전화번호가 없습니다.'}</p>{data.manager.phone && <a className="mvp-button" href={`tel:${data.manager.phone.replace(/[^+\d]/g, '')}`}><Icon name="phone" />담당자에게 전화하기</a>}</> : <p>{data ? '연결된 담당자가 없습니다.' : '담당자 정보를 확인하는 중입니다.'}</p>}</section>
      </> : <><section className="mvp-card"><h2><Icon name="walk" />외출 모드</h2><p>{mode?.enabled ? `외출 중 · ${time(mode.until)}까지` : mode?.scheduled ? `예약됨 · ${time(mode.startedAt)}부터` : '외출 시간을 설정해 주세요.'}</p>
        <div className="duration-presets">{[30, 60, 120, 240].map((minutes) => <button className={duration === minutes ? 'selected' : 'secondary'} key={minutes} aria-pressed={duration === minutes} onClick={() => setDuration(minutes)}>{minutes < 60 ? `${minutes}분` : `${minutes / 60}시간`}</button>)}</div>
        <div className="care-form"><label>외출 시간 (분)<input type="number" min="1" max="1440" step="1" value={duration || ''} onChange={(event) => setDuration(Number(event.target.value))} /></label><label>예약 시작 (선택 · 비워 두면 즉시 시작)<input type="datetime-local" value={start} onChange={(event) => setStart(event.target.value)} /></label></div>
        <div className="mvp-actions"><button disabled={busy || !data} onClick={() => void away(true)}>{busy ? '처리 중…' : start ? '외출 예약 저장' : '외출 시작'}</button>{(mode?.enabled || mode?.scheduled) && <button className="secondary" disabled={busy} onClick={() => void away(false)}>{mode.scheduled ? '예약 취소' : '귀가했어요'}</button>}</div>
      </section><p className="card-note">설정한 시간이 지나면 외출 모드가 자동 종료됩니다.</p></>}
      {feedback && <p className="mvp-feedback" role="status">{feedback}<button className="icon-button" aria-label="안내 닫기" onClick={() => setFeedback('')}><Icon name="close" /></button></p>}
      <button className="text-button" onClick={() => void refresh()}><Icon name="history" />내 정보 새로고침</button>
    </div><nav className="user-bottom-nav" aria-label="대상자 메뉴"><button className={tab === 'home' ? 'selected' : ''} aria-current={tab === 'home' ? 'page' : undefined} onClick={() => setTab('home')}><Icon name="home" />홈</button><button className={tab === 'away' ? 'selected' : ''} aria-current={tab === 'away' ? 'page' : undefined} onClick={() => setTab('away')}><Icon name="walk" />외출 모드</button></nav>
  </main>
}

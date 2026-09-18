import { useCallback, useEffect, useRef, useState } from 'react'
import { api, json, message, time } from './api'
import Icon from './Icon'
import type { MyDashboard } from './types'

export default function UserDashboard({ onLogout, comparison = false }: { onLogout: () => void; comparison?: boolean }) {
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
    if (busy) return
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
    if (!notificationId || busy) return
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
  const displayTime = (value: string | null | undefined) => value
    ? new Date(value).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric', minute: '2-digit' }) : '종료 시간 미정'
  if (!comparison) return <main className="mvp mvp-user senior-user">
    <header className="user-header"><a className="mvp-brand" href="/"><span className="brand-symbol"><Icon name="shield" /></span>On:마음</a><button className="secondary senior-logout" onClick={onLogout}>로그아웃</button></header>
    <div className="user-content">
      <div className="user-greeting"><h1>{tab === 'away' ? '외출 시간 정하기' : data ? `${data.name}님, 안녕하세요.` : '안녕하세요.'}</h1></div>
      {error && <div className="error-box" role="alert"><p>{error}</p><button className="secondary" onClick={() => void refresh()}>다시 불러오기</button></div>}
      {notificationId && <section className="mvp-card response-card" aria-labelledby="response-title"><h2 id="response-title">지금 상태를 알려주세요</h2><p>선택한 응답을 담당자에게 전달해요.</p><div className="senior-actions"><button className="danger-button" disabled={busy || !data} onClick={() => void answer('yes')}>위험해요 · 도움 필요</button><button className="secondary" disabled={busy || !data} onClick={() => void answer('no')}>안전해요 · 위험 없음</button></div></section>}
      {feedback && <div className="mvp-feedback senior-feedback" role="status"><p>{feedback}</p><button className="secondary" onClick={() => setFeedback('')}>확인</button></div>}
      {tab === 'home' ? <>
        <section className="mvp-card senior-outing" aria-labelledby="outing-title"><h2 id="outing-title">외출 알림</h2><div className="senior-status"><strong>{!data ? '정보를 불러오고 있어요' : mode?.enabled ? '외출 중입니다' : mode?.scheduled ? '외출을 예약했어요' : '등록된 외출이 없어요'}</strong>{mode?.enabled && <p>{displayTime(mode.until)}까지</p>}{mode?.scheduled && <p>{displayTime(mode.startedAt)}부터</p>}</div>
          <div className="senior-actions">{(mode?.enabled || mode?.scheduled) && <button disabled={busy} onClick={() => void away(false)}><Icon name={mode.enabled ? 'home' : 'close'} />{busy ? '처리 중…' : mode.enabled ? '집에 돌아왔어요' : '외출 예약 취소'}</button>}<button className={mode?.enabled || mode?.scheduled ? 'secondary' : ''} disabled={!data || busy} onClick={() => setTab('away')}><Icon name="walk" />{mode?.enabled || mode?.scheduled ? '외출 시간 바꾸기' : '외출 시간 정하기'}<Icon name="arrow" /></button></div>
        </section>
        <section className="mvp-card senior-contact" aria-labelledby="contact-title"><h2 id="contact-title">나의 복지담당자</h2>{data?.manager ? <><p className="senior-manager">{data.manager.name}<span> 담당자</span></p>{data.manager.phone ? <><p className="senior-phone">{data.manager.phone}</p><a className="mvp-button" href={`tel:${data.manager.phone.replace(/[^+\d]/g, '')}`}><Icon name="phone" />담당자에게 전화하기</a></> : <p>등록된 전화번호가 없습니다.</p>}</> : <p>{data ? '아직 배정된 담당자가 없습니다.' : '담당자 정보를 불러오고 있어요.'}</p>}</section>
      </> : <section className="mvp-card senior-away" aria-labelledby="away-title"><h2 id="away-title">외출할 시간을 선택해 주세요</h2>
        <fieldset className="senior-duration" disabled={busy}><legend>외출 시간 선택</legend><div className="duration-presets">{[30, 60, 120, 240].map((minutes) => <button type="button" className={duration === minutes ? 'selected' : 'secondary'} key={minutes} aria-pressed={duration === minutes} onClick={() => setDuration(minutes)}>{duration === minutes && <Icon name="check" />}{minutes < 60 ? `${minutes}분` : `${minutes / 60}시간`}</button>)}</div></fieldset>
        <p className="senior-selection">{start ? `${displayTime(start)} 출발` : '지금 출발'} · {duration >= 60 && duration % 60 === 0 ? `${duration / 60}시간` : `${duration}분`} 외출</p>
        <div className="senior-actions"><button disabled={busy || !data} onClick={() => void away(true)}><Icon name="walk" />{busy ? '저장 중…' : start ? '선택한 시간으로 외출 예약하기' : '지금 외출 시작하기'}</button>{(mode?.enabled || mode?.scheduled) && <button className="secondary" disabled={busy} onClick={() => void away(false)}>{mode.scheduled ? '기존 예약 취소' : '집에 돌아왔어요'}</button>}</div><p className="senior-note">정한 시간이 지나면 외출 설정이 자동으로 해제돼요.</p>
        <div className="senior-options">
          <details className="senior-extra"><summary><span className="when-closed">외출 시간 직접 입력하기</span><span className="when-open">시간 입력 닫기</span></summary><div className="care-form"><label>외출 시간 (분)<input type="number" inputMode="numeric" min="1" max="1440" step="1" value={duration || ''} disabled={busy} onChange={(event) => setDuration(Number(event.target.value))} /></label><p className="senior-note">1분부터 1,440분(24시간)까지 입력할 수 있어요.</p><button disabled={busy || !data} onClick={() => void away(true)}>{busy ? '저장 중…' : start ? '입력한 시간으로 외출 예약하기' : '입력한 시간으로 외출 시작하기'}</button></div></details>
          <details className="senior-extra"><summary><span className="when-closed">나중에 출발하도록 예약하기</span><span className="when-open">예약 설정 닫기</span></summary><div className="care-form"><label>출발할 날짜와 시간<input type="datetime-local" value={start} disabled={busy} onChange={(event) => setStart(event.target.value)} /></label><p className="senior-note">외출 시간: {duration}분</p><button disabled={busy || !data || !start} onClick={() => void away(true)}>{busy ? '저장 중…' : '외출 예약 저장하기'}</button>{start && <button className="secondary" disabled={busy} onClick={() => setStart('')}>지금 출발로 바꾸기</button>}</div></details>
        </div>
      </section>}
    </div>
    <nav className="user-bottom-nav" aria-label="대상자 메뉴"><button className={tab === 'home' ? 'selected' : ''} aria-current={tab === 'home' ? 'page' : undefined} onClick={() => setTab('home')}><Icon name="home" />홈</button><button className={tab === 'away' ? 'selected' : ''} aria-current={tab === 'away' ? 'page' : undefined} onClick={() => setTab('away')}><Icon name="walk" />외출</button></nav>
  </main>
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

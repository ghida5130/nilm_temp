import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../api/client'
import { clearSession } from '../api/tokenStorage'
import Brand from '../components/common/Brand'
import Icon from '../components/common/Icon'
import { useAwayMode, useMyDashboardQuery, useNotificationResponse } from '../hooks/useMonitoring'
import { formatShortDateTime, telephoneHref } from '../utils/format'

const cardClass = 'rounded-2xl border border-stone-200 bg-white p-6 shadow-sm'
const primaryButton = 'flex min-h-14 w-full items-center justify-center gap-2 rounded-xl bg-brand-500 px-4 py-3 text-lg font-bold text-white shadow-sm hover:bg-brand-600 disabled:bg-stone-300'
const secondaryButton = 'flex min-h-14 w-full items-center justify-center gap-2 rounded-xl border-2 border-brand-300 bg-white px-4 py-3 text-lg font-bold text-brand-800 hover:bg-brand-50 disabled:border-stone-300 disabled:text-stone-400'

export default function UserPage({ comparison = false }: { comparison?: boolean }) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const dashboard = useMyDashboardQuery()
  const awayMutation = useAwayMode()
  const responseMutation = useNotificationResponse()
  const [feedback, setFeedback] = useState('')
  const [tab, setTab] = useState<'home' | 'away'>('home')
  const [duration, setDuration] = useState(60)
  const [start, setStart] = useState('')
  const [notificationId, setNotificationId] = useState(() => {
    const value = searchParams.get('notificationId')
    return value && /^\d+$/.test(value) ? value : null
  })
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.data?.type === 'PUSH_RECEIVED' && /^\d+$/.test(String(event.data.notificationId))) setNotificationId(String(event.data.notificationId))
    }
    navigator.serviceWorker?.addEventListener('message', receive)
    return () => navigator.serviceWorker?.removeEventListener('message', receive)
  }, [])
  const data = dashboard.data
  const mode = data?.awayMode
  const busy = awayMutation.isPending || responseMutation.isPending
  const updateAway = async (enabled: boolean) => {
    if (enabled && (!Number.isInteger(duration) || duration < 1 || duration > 1440)) { setFeedback('외출 시간을 1~1440분 사이로 입력해 주세요.'); return }
    const startsAt = enabled && start ? new Date(start) : new Date()
    if (enabled && start && startsAt.getTime() <= Date.now()) { setFeedback('예약 시작 시간은 현재보다 이후로 선택해 주세요.'); return }
    try {
      await awayMutation.mutateAsync(enabled ? { enabled, ...(start ? { startsAt: startsAt.toISOString() } : {}), endsAt: new Date(startsAt.getTime() + duration * 60_000).toISOString() } : { enabled })
      setFeedback(enabled ? '외출 설정을 저장했습니다.' : '외출 모드를 해제했습니다.')
      if (enabled) setTab('home')
    } catch (cause) {
      setFeedback(getApiErrorMessage(cause))
    }
  }
  const answer = async (value: 'yes' | 'no') => {
    if (!notificationId) return
    try {
      await responseMutation.mutateAsync({ notificationId, answer: value })
      setFeedback(value === 'yes' ? '위험 상황이라는 응답을 전달했습니다.' : '안전하다는 응답을 전달했습니다.')
      setNotificationId(null)
      const url = new URL(window.location.href)
      url.searchParams.delete('notificationId')
      url.searchParams.delete('answer')
      window.history.replaceState(null, '', url)
    } catch (cause) {
      setFeedback(getApiErrorMessage(cause))
    }
  }
  const logout = () => { clearSession(); navigate('/') }
  return <main className={`min-h-screen bg-stone-100 pb-28 text-stone-800 ${comparison ? 'selection:bg-brand-200' : ''}`}>
    <header className="mx-auto flex max-w-xl items-center justify-between px-5 py-5"><Brand /><button className="rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-semibold text-stone-600" onClick={logout}>로그아웃</button></header>
    <div className="mx-auto grid max-w-xl gap-5 px-5">
      <div><h1 className="text-3xl leading-tight font-extrabold">{tab === 'away' ? '외출 시간 정하기' : data ? `${data.name}님, 안녕하세요.` : '안녕하세요.'}</h1>{comparison && <p className="mt-2 text-sm font-semibold text-brand-700">기존 디자인 비교 화면</p>}</div>
      {dashboard.isError && <div className="rounded-xl bg-red-50 p-4 text-red-700" role="alert"><p>{getApiErrorMessage(dashboard.error)}</p><button className="mt-2 font-bold underline" onClick={() => void dashboard.refetch()}>다시 불러오기</button></div>}
      {notificationId && <section className={`${cardClass} border-2 border-red-200 bg-red-50`} aria-labelledby="response-title"><h2 className="text-2xl font-extrabold" id="response-title">지금 상태를 알려주세요</h2><p className="mt-2 text-lg text-stone-600">선택한 응답을 담당자에게 전달해요.</p><div className="mt-6 grid gap-3"><button className="min-h-16 rounded-xl bg-red-700 px-4 text-lg font-bold text-white disabled:bg-stone-300" disabled={busy || !data} onClick={() => void answer('yes')}>위험해요 · 도움 필요</button><button className={secondaryButton} disabled={busy || !data} onClick={() => void answer('no')}>안전해요 · 위험 없음</button></div></section>}
      {feedback && <div className="flex items-center gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4" role="status"><p className="flex-1 text-brand-900">{feedback}</p><button className="font-bold text-brand-800" onClick={() => setFeedback('')}>확인</button></div>}
      {tab === 'home' ? <>
        <section className={cardClass} aria-labelledby="outing-title"><h2 className="text-2xl font-bold" id="outing-title">외출 알림</h2><div className="mt-5 border-l-4 border-brand-500 pl-4"><strong className="text-xl">{!data ? '정보를 불러오고 있어요' : mode?.enabled ? '외출 중입니다' : mode?.scheduled ? '외출을 예약했어요' : '등록된 외출이 없어요'}</strong>{mode?.enabled && <p className="mt-1 text-lg text-stone-600">{formatShortDateTime(mode.until)}까지</p>}{mode?.scheduled && <p className="mt-1 text-lg text-stone-600">{formatShortDateTime(mode.startedAt)}부터</p>}</div>
          <div className="mt-6 grid gap-3">{(mode?.enabled || mode?.scheduled) && <button className={primaryButton} disabled={busy} onClick={() => void updateAway(false)}><Icon name={mode.enabled ? 'home' : 'close'} />{busy ? '처리 중…' : mode.enabled ? '집에 돌아왔어요' : '외출 예약 취소'}</button>}<button className={mode?.enabled || mode?.scheduled ? secondaryButton : primaryButton} disabled={!data || busy} onClick={() => setTab('away')}><Icon name="walk" />{mode?.enabled || mode?.scheduled ? '외출 시간 바꾸기' : '외출 시간 정하기'}<Icon name="arrow" /></button></div>
        </section>
        <section className={cardClass} aria-labelledby="contact-title"><h2 className="text-2xl font-bold" id="contact-title">나의 복지담당자</h2>{data?.manager ? <><p className="mt-5 text-2xl font-bold">{data.manager.name}<span className="text-lg font-normal"> 담당자</span></p>{data.manager.phone ? <><p className="my-2 text-lg text-stone-600">{data.manager.phone}</p><a className={primaryButton} href={telephoneHref(data.manager.phone)}><Icon name="phone" />담당자에게 전화하기</a></> : <p className="mt-4 text-stone-500">등록된 전화번호가 없습니다.</p>}</> : <p className="mt-4 text-stone-500">{data ? '아직 배정된 담당자가 없습니다.' : '담당자 정보를 불러오고 있어요.'}</p>}</section>
      </> : <section className={cardClass} aria-labelledby="away-title"><h2 className="text-2xl font-bold" id="away-title">외출할 시간을 선택해 주세요</h2>
        <fieldset className="mt-5" disabled={busy}><legend className="text-stone-600">외출 시간 선택</legend><div className="mt-3 grid grid-cols-2 gap-3">{[30, 60, 120, 240].map((minutes) => <button type="button" className={duration === minutes ? primaryButton : secondaryButton} key={minutes} aria-pressed={duration === minutes} onClick={() => setDuration(minutes)}>{duration === minutes && <Icon name="check" />}{minutes < 60 ? `${minutes}분` : `${minutes / 60}시간`}</button>)}</div></fieldset>
        <p className="mt-6 text-lg font-bold">{start ? `${formatShortDateTime(start)} 출발` : '지금 출발'} · {duration >= 60 && duration % 60 === 0 ? `${duration / 60}시간` : `${duration}분`} 외출</p>
        <div className="mt-5 grid gap-3"><button className={primaryButton} disabled={busy || !data} onClick={() => void updateAway(true)}><Icon name="walk" />{busy ? '저장 중…' : start ? '선택한 시간으로 외출 예약하기' : '지금 외출 시작하기'}</button>{(mode?.enabled || mode?.scheduled) && <button className={secondaryButton} disabled={busy} onClick={() => void updateAway(false)}>{mode.scheduled ? '기존 예약 취소' : '집에 돌아왔어요'}</button>}</div><p className="mt-4 text-stone-500">정한 시간이 지나면 외출 설정이 자동으로 해제돼요.</p>
        <div className="mt-6 grid gap-4 border-t border-stone-200 pt-5"><details className="group"><summary className="cursor-pointer rounded-xl border-2 border-brand-300 p-4 font-bold text-brand-800">외출 시간 직접 입력하기</summary><div className="mt-4 grid gap-3"><label className="grid gap-2 font-semibold">외출 시간 (분)<input className="h-13 rounded-xl border border-stone-300 px-4" type="number" inputMode="numeric" min="1" max="1440" step="1" value={duration || ''} disabled={busy} onChange={(event) => setDuration(Number(event.target.value))} /></label></div></details>
          <details><summary className="cursor-pointer rounded-xl border-2 border-brand-300 p-4 font-bold text-brand-800">나중에 출발하도록 예약하기</summary><div className="mt-4 grid gap-3"><label className="grid gap-2 font-semibold">출발할 날짜와 시간<input className="h-13 rounded-xl border border-stone-300 px-4" type="datetime-local" value={start} disabled={busy} onChange={(event) => setStart(event.target.value)} /></label>{start && <button className={secondaryButton} disabled={busy} onClick={() => setStart('')}>지금 출발로 바꾸기</button>}</div></details></div>
      </section>}
    </div>
    <nav className="fixed inset-x-0 bottom-0 z-10 mx-auto flex max-w-xl gap-3 border-t border-stone-200 bg-white/95 px-5 py-3 backdrop-blur" aria-label="대상자 메뉴"><button className={`flex min-h-14 flex-1 items-center justify-center gap-2 rounded-xl text-lg font-bold ${tab === 'home' ? 'bg-brand-50 text-brand-800' : 'text-stone-500'}`} aria-current={tab === 'home' ? 'page' : undefined} onClick={() => setTab('home')}><Icon name="home" />홈</button><button className={`flex min-h-14 flex-1 items-center justify-center gap-2 rounded-xl text-lg font-bold ${tab === 'away' ? 'bg-brand-50 text-brand-800' : 'text-stone-500'}`} aria-current={tab === 'away' ? 'page' : undefined} onClick={() => setTab('away')}><Icon name="walk" />외출</button></nav>
  </main>
}

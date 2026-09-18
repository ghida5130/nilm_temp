import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../api/client'
import { clearSession } from '../api/tokenStorage'
import Brand from '../components/common/Brand'
import Icon from '../components/common/Icon'
import type { IconName } from '../components/common/Icon'
import RegistrationForm from '../components/staff/RegistrationForm'
import SubjectDetail from '../components/staff/SubjectDetail'
import { useSubjectsQuery, useSubjectStream } from '../hooks/useMonitoring'
import { responseLabel, riskLabels } from '../types/monitoring'
import type { Risk } from '../types/monitoring'
import { formatDateTime, riskBadgeClass } from '../utils/format'

const navigation: { key: string; label: string; icon: IconName }[] = [
  { key: 'overview', label: '대시보드', icon: 'grid' },
  { key: 'subjects', label: '대상자 관리', icon: 'users' },
  { key: 'alerts', label: '최근 알림', icon: 'bell' },
]

export default function StaffPage() {
  const navigate = useNavigate()
  const { subjectId } = useParams()
  const [searchParams] = useSearchParams()
  const query = useSubjectsQuery()
  const stream = useSubjectStream()
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<'ALL' | Risk>('ALL')
  const [registering, setRegistering] = useState(false)
  const [feedback, setFeedback] = useState('')
  const subjects = useMemo(() => query.data?.subjects ?? [], [query.data?.subjects])
  const requestedView = searchParams.get('view')
  const view = navigation.some((item) => item.key === requestedView) ? requestedView! : 'overview'
  const selected = subjects.find((subject) => subject.subjectId === subjectId)
  const visible = useMemo(() => subjects.filter((subject) => `${subject.name} ${subject.address} ${subject.phone}`.toLowerCase().includes(search.trim().toLowerCase()) && (filter === 'ALL' || subject.riskLevel === filter)).sort((a, b) => b.riskScore - a.riskScore), [subjects, search, filter])
  const alerts = subjects.filter((subject) => subject.latestAlert).sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
  const title = subjectId ? '대상자 상세' : navigation.find((item) => item.key === view)?.label
  const logout = () => { clearSession(); navigate('/') }
  const metrics = [
    { label: '담당 대상자', value: subjects.length, icon: 'users' as IconName, style: 'bg-brand-100 text-brand-700' },
    ...(['DANGER', 'WARNING', 'NORMAL'] as Risk[]).map((risk) => ({ label: riskLabels[risk], value: subjects.filter((subject) => subject.riskLevel === risk).length, icon: 'shield' as IconName, style: risk === 'DANGER' ? 'bg-red-100 text-red-700' : risk === 'WARNING' ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700' })),
  ]
  return <main className="min-h-screen bg-stone-100 text-stone-800 lg:grid lg:grid-cols-[250px_1fr]">
    <aside className="flex items-center gap-4 border-b border-stone-200 bg-white p-4 lg:fixed lg:inset-y-0 lg:w-[250px] lg:flex-col lg:items-stretch lg:border-r lg:border-b-0 lg:p-6"><Brand /><nav className="ml-auto flex gap-1 lg:mt-10 lg:ml-0 lg:grid" aria-label="담당자 메뉴">{navigation.map((item) => <Link className={`flex items-center gap-3 rounded-xl px-3 py-3 font-semibold transition ${subjectId ? item.key === 'subjects' ? 'bg-brand-50 text-brand-700' : 'text-stone-500 hover:bg-stone-50' : view === item.key ? 'bg-brand-50 text-brand-700' : 'text-stone-500 hover:bg-stone-50'}`} to={`/staff?view=${item.key}`} key={item.key}><Icon name={item.icon} /><span className="hidden lg:inline">{item.label}</span></Link>)}</nav><div className="mt-auto hidden border-t border-stone-100 pt-5 lg:block"><p className="font-bold">복지담당자</p><p className="text-sm text-stone-500">함께 지키는 안심 일상</p><button className="mt-4 text-sm font-semibold text-stone-500 hover:text-brand-700" onClick={logout}>로그아웃</button></div></aside>
    <div className="lg:col-start-2"><header className="sticky top-0 z-10 flex min-h-16 items-center justify-between border-b border-stone-200 bg-white/90 px-5 backdrop-blur md:px-8"><strong>{title}</strong><div className="flex items-center gap-3"><span className={`hidden items-center gap-2 text-sm sm:flex ${stream.connection === 'connected' ? 'text-emerald-700' : 'text-amber-700'}`}><i className={`h-2 w-2 rounded-full ${stream.connection === 'connected' ? 'bg-emerald-500' : 'bg-amber-500'}`} />{stream.connection === 'connected' ? '실시간 연결됨' : '재연결 중'}</span><button className="rounded-lg border border-stone-300 px-3 py-2 text-sm font-semibold hover:bg-stone-50" disabled={query.isFetching} onClick={() => void query.refetch()}>{query.isFetching ? '갱신 중…' : '새로고침'}</button></div></header>
      <div className="mx-auto grid max-w-7xl gap-5 p-5 md:p-8">
        {query.isError && <p className="rounded-xl bg-red-50 p-4 text-red-700" role="alert">{getApiErrorMessage(query.error)}</p>}
        {stream.connection === 'disconnected' && <p className="rounded-xl bg-amber-50 p-4 text-amber-800" role="status">실시간 연결이 끊겨 재연결 중입니다. {stream.error}</p>}
        {stream.notice && <aside className="flex flex-wrap items-center gap-4 rounded-2xl border border-red-200 bg-red-50 p-4" role="status"><Icon className="text-red-600" name="bell" /><div className="min-w-60 flex-1"><strong>{subjects.find((subject) => subject.subjectId === stream.notice?.subjectId)?.name ?? '대상자'}님의 상태가 갱신되었습니다.</strong><p className="text-sm text-red-700">{stream.notice.lastDetection?.description ?? responseLabel(stream.notice.latestAlert?.subjectResponse)}</p></div><Link className="rounded-lg bg-red-600 px-4 py-2 font-semibold text-white" to={`/staff/subjects/${stream.notice.subjectId}`}>상세보기</Link><button aria-label="알림 닫기" onClick={() => stream.setNotice(null)}><Icon name="close" /></button></aside>}
        {subjectId ? <><Link className="flex w-fit items-center gap-2 font-semibold text-brand-700" to="/staff?view=subjects"><Icon name="back" />대상자 목록</Link>{selected ? <SubjectDetail subject={selected} /> : <section className="rounded-2xl bg-white p-10 text-center text-stone-500">{query.isLoading ? '대상자 정보를 불러오는 중입니다.' : '담당 대상자를 찾을 수 없습니다.'}</section>}</> : <>
          <div className="flex flex-wrap items-end justify-between gap-4"><div><h1 className="text-3xl font-extrabold">{view === 'overview' ? '오늘의 돌봄 현황' : title}</h1><p className="mt-2 text-stone-500">작은 신호도 놓치지 않도록, 오늘의 일상을 살펴보세요.</p></div>{view !== 'alerts' && <button className="flex items-center gap-2 rounded-xl bg-brand-500 px-4 py-3 font-bold text-white hover:bg-brand-600" onClick={() => setRegistering((value) => !value)}><Icon name="users" />대상자 등록</button>}</div>
          {registering && <RegistrationForm onCancel={() => setRegistering(false)} onDone={() => { setRegistering(false); setFeedback('대상자를 등록했습니다.') }} />}
          {view === 'overview' && <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="대상자 현황">{metrics.map((metric) => <article className="flex items-center justify-between rounded-2xl border border-stone-200 bg-white p-5 shadow-sm" key={metric.label}><div><span className="text-sm text-stone-500">{metric.label}</span><strong className="mt-2 block text-3xl">{query.isLoading ? '—' : metric.value}<small className="ml-1 text-sm">명</small></strong></div><span className={`grid h-11 w-11 place-items-center rounded-xl ${metric.style}`}><Icon name={metric.icon} /></span></article>)}</section>}
          {view === 'alerts' ? <section className="rounded-2xl border border-stone-200 bg-white p-6 shadow-sm"><h2 className="flex items-center gap-2 text-xl font-bold"><Icon name="bell" />대상자별 최근 알림</h2><p className="mt-2 text-stone-500">각 대상자의 가장 최근 알림입니다.</p><div className="mt-5 divide-y divide-stone-100">{alerts.map((subject) => <article className="flex flex-wrap items-center gap-4 py-4" key={subject.subjectId}><span className="grid h-10 w-10 place-items-center rounded-full bg-brand-100 font-bold text-brand-700">{subject.name.slice(0, 1)}</span><div className="min-w-48 flex-1"><strong>{subject.name} · {responseLabel(subject.latestAlert?.subjectResponse)}</strong><p className="text-sm text-stone-500">응답 시각 {formatDateTime(subject.latestAlert?.subjectResponse.respondedAt)}</p></div><Link className="font-semibold text-brand-700" to={`/staff/subjects/${subject.subjectId}`}>상세보기</Link></article>)}</div>{!alerts.length && <p className="py-10 text-center text-stone-500">조회된 최근 알림이 없습니다.</p>}</section> : <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm"><div className="flex flex-wrap items-center gap-4 border-b border-stone-100 p-4"><div className="flex flex-wrap gap-2">{[{ id: 'ALL' as const, label: '전체' }, ...(['DANGER', 'WARNING', 'NORMAL'] as Risk[]).map((risk) => ({ id: risk, label: riskLabels[risk] }))].map((item) => <button className={`rounded-full px-4 py-2 text-sm font-bold ${filter === item.id ? 'bg-brand-500 text-white' : 'bg-stone-100 text-stone-600'}`} key={item.id} aria-pressed={filter === item.id} onClick={() => setFilter(item.id)}>{item.label}</button>)}</div><label className="ml-auto flex min-w-64 flex-1 items-center gap-2 rounded-xl bg-stone-100 px-3 md:max-w-sm"><Icon className="text-stone-400" name="search" /><input className="h-11 min-w-0 flex-1 bg-transparent outline-none" aria-label="대상자 검색" placeholder="이름, 주소, 전화번호 검색" value={search} onChange={(event) => setSearch(event.target.value)} /></label></div>
            <div className="overflow-x-auto"><table className="w-full min-w-4xl text-left"><thead className="bg-stone-50 text-sm text-stone-500"><tr><th className="p-4">대상자 정보</th><th>위험 단계</th><th>위험 점수</th><th>최근 알림 응답</th><th>마지막 가전 활동</th><th>상세</th></tr></thead><tbody className="divide-y divide-stone-100">{visible.map((subject) => <tr className="hover:bg-stone-50" key={subject.subjectId}><td className="p-4"><strong>{subject.name} · {subject.age}세</strong><small className="block text-stone-500">{subject.address}</small></td><td><span className={`rounded-full px-3 py-1 text-xs font-bold ring-1 ${riskBadgeClass(subject.riskLevel)}`}>{riskLabels[subject.riskLevel]}</span></td><td className="font-bold">{subject.riskScore}점</td><td>{responseLabel(subject.latestAlert?.subjectResponse)}</td><td>{formatDateTime(subject.lastActivity?.occurredAt)}<small className="block text-stone-500">{subject.lastActivity?.applianceType}</small></td><td><Link className="font-semibold text-brand-700" to={`/staff/subjects/${subject.subjectId}`}>상세보기</Link></td></tr>)}</tbody></table></div>
            {!visible.length && <p className="py-12 text-center text-stone-500">{query.isLoading ? '대상자를 불러오는 중입니다.' : '조건에 맞는 대상자가 없습니다.'}</p>}<footer className="flex justify-between border-t border-stone-100 p-4 text-sm text-stone-500"><span>{visible.length}명 표시</span><span>최근 갱신 {query.dataUpdatedAt ? formatDateTime(new Date(query.dataUpdatedAt).toISOString()) : '기록 없음'}</span></footer></section>}
        </>}
        {feedback && <p className="fixed right-5 bottom-5 rounded-xl bg-stone-800 px-5 py-3 text-white shadow-lg" role="status">{feedback}</p>}
      </div>
    </div>
  </main>
}

import { useState } from 'react'
import type { FormEvent } from 'react'
import Icon from './Icon'
import type { IconName } from './Icon'
import { api, json, message, time, today } from './api'
import { responseLabel, riskLabels, riskTone } from './types'
import type { Risk } from './types'
import useSubjects from './useSubjects'
import SubjectDetail from './SubjectDetail'

const navigation: { key: string; label: string; icon: IconName }[] = [
  { key: 'overview', label: '대시보드', icon: 'grid' },
  { key: 'subjects', label: '대상자 관리', icon: 'users' },
  { key: 'alerts', label: '최근 알림', icon: 'bell' },
]

function Registration({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = Object.fromEntries(new FormData(event.currentTarget))
    setBusy(true); setError('')
    try { await api('/api/monitoring/subjects', json('POST', fields)); onDone() }
    catch (cause) { setError(message(cause)) }
    finally { setBusy(false) }
  }
  return <section className="mvp-card"><div className="mvp-row"><h2><Icon name="users" />대상자 등록</h2><button className="secondary" onClick={onCancel} disabled={busy}>닫기</button></div>
    <p>등록된 가구 ID를 사용해 담당 대상자를 연결합니다.</p>
    <form className="care-form form-grid" onSubmit={(event) => void submit(event)}>
      <label>이름<input name="name" required maxLength={50} /></label>
      <label>생년월일<input name="birthDate" type="date" required max={today()} /></label>
      <label>전화번호<input name="phone" type="tel" required pattern="0[0-9]{8,10}" placeholder="01012345678" /></label>
      <label>가구 ID<input name="householdId" required maxLength={10} /></label>
      <label>주소<input name="address" required maxLength={255} /></label>
      <label>상세 주소<input name="addressDetail" maxLength={100} /></label>
      <label className="full-width">담당자 메모<textarea name="managerMemo" maxLength={1000} rows={3} /></label>
      {error && <p className="error-box full-width" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? '등록 중…' : '대상자 등록'}</button>
    </form>
  </section>
}

export default function StaffDashboard({ onLogout }: { onLogout: () => void }) {
  const { subjects, error, streamError, connection, loading, notice, setNotice, updated, refresh } = useSubjects()
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [registering, setRegistering] = useState(false)
  const [feedback, setFeedback] = useState('')
  const routeId = /^\/staff\/subjects\/([^/]+)\/?$/.exec(window.location.pathname)?.[1]
  const requested = new URLSearchParams(window.location.search).get('view')
  const view = navigation.some((item) => item.key === requested) ? requested : 'overview'
  const selected = subjects?.find((subject) => subject.subjectId === routeId)
  const visible = subjects?.filter((subject) => `${subject.name} ${subject.address} ${subject.phone}`.toLowerCase().includes(search.trim().toLowerCase()) && (filter === 'ALL' || subject.riskLevel === filter)).sort((a, b) => b.riskScore - a.riskScore) ?? []
  const alerts = subjects?.filter((subject) => subject.latestAlert).sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt)) ?? []
  const title = routeId ? '대상자 상세' : navigation.find((item) => item.key === view)?.label
  const metrics = [
    { label: '담당 대상자', value: subjects?.length, icon: 'users' as IconName, tone: '' },
    ...(['DANGER', 'WARNING', 'NORMAL'] as Risk[]).map((risk, index) => ({ label: riskLabels[risk], value: subjects?.filter((subject) => subject.riskLevel === risk).length, icon: 'shield' as IconName, tone: ['coral', 'amber', ''][index] })),
  ]
  return <main className="mvp mvp-staff">
    <aside className="staff-sidebar"><a className="mvp-brand" href="/"><span className="brand-symbol"><Icon name="shield" /></span>On:마음</a>
      <nav aria-label="담당자 메뉴">{navigation.map((item) => <a key={item.key} aria-label={item.label} className={(routeId ? item.key === 'subjects' : view === item.key) ? 'selected' : ''} href={`/staff?view=${item.key}`}><Icon name={item.icon} /><span>{item.label}</span></a>)}</nav>
      <div className="sidebar-bottom"><span className="staff-avatar"><Icon name="shield" /></span><div><strong>복지담당자</strong><small>함께 지키는 안심 일상</small></div></div>
      <button className="text-button" onClick={onLogout}>로그아웃</button>
    </aside>
    <div className="staff-workspace"><header className="staff-topbar"><strong>{title}</strong><div><span className={`connection ${connection}`}><i />{connection === 'connected' ? '실시간 연결됨' : connection === 'connecting' ? '실시간 연결 중' : '실시간 재연결 중'}</span><button className="secondary" disabled={loading} onClick={() => void refresh()}>{loading ? '갱신 중…' : '새로고침'}</button></div></header>
      <div className="mvp-dashboard">
        {error && <p className="error-box" role="alert">{error} {subjects ? '이전에 조회한 정보를 표시합니다.' : '대상자 정보를 불러오지 못했습니다.'}</p>}
        {connection === 'disconnected' && <p className="error-box" role="status">실시간 연결이 끊겨 재연결 중입니다. {streamError}</p>}
        {notice && <aside className="mvp-alert" role="status"><Icon name="bell" /><div><strong>{subjects?.find((subject) => subject.subjectId === notice.subjectId)?.name ?? '대상자'}님의 상태가 갱신되었습니다.</strong><p>{notice.lastDetection?.description ?? responseLabel(notice.latestAlert?.subjectResponse)}</p></div><a className="mvp-button" href={`/staff/subjects/${notice.subjectId}`}>상세보기</a><button className="icon-button" aria-label="알림 닫기" onClick={() => setNotice(null)}><Icon name="close" /></button></aside>}
        {routeId ? <><a className="mvp-back" href="/staff?view=subjects"><Icon name="back" />대상자 목록</a>{selected ? <SubjectDetail subject={selected} /> : <section className="mvp-card empty-state">{subjects ? '담당 대상자를 찾을 수 없습니다.' : error ? '대상자 정보를 조회할 수 없습니다.' : '대상자 정보를 불러오는 중입니다.'}</section>}</> : <>
          <div className="mvp-title"><div><h1>{view === 'overview' ? '오늘의 돌봄 현황' : title}</h1><span>작은 신호도 놓치지 않도록, 오늘의 일상을 살펴보세요.</span></div>{view !== 'alerts' && <button onClick={() => setRegistering(!registering)}><Icon name="users" />대상자 등록</button>}</div>
          {registering && <Registration onCancel={() => setRegistering(false)} onDone={() => { setRegistering(false); setFeedback('대상자를 등록했습니다.'); void refresh() }} />}
          {view === 'overview' && <section className="mvp-summary" aria-label="대상자 현황">{metrics.map((metric) => <section key={metric.label}><div><span>{metric.label}</span><strong>{metric.value ?? '—'}<small>명</small></strong></div><span className={`summary-icon ${metric.tone}`}><Icon name={metric.icon} /></span></section>)}</section>}
          {view === 'alerts' ? <section className="mvp-card"><h2><Icon name="bell" />대상자별 최근 알림</h2><p>각 대상자의 가장 최근 알림입니다. 전체 기록은 대상자 상세에서 확인할 수 있습니다.</p>
            {alerts.map((subject) => <article className="mvp-incident-row" key={subject.subjectId}><span className="person-avatar">{subject.name.slice(0, 1)}</span><div><strong>{subject.name} · {responseLabel(subject.latestAlert?.subjectResponse)}</strong><p>응답 시각 {time(subject.latestAlert?.subjectResponse.respondedAt)}</p></div><a className="detail-button" href={`/staff/subjects/${subject.subjectId}`}>상세보기<Icon name="arrow" /></a></article>)}
            {!alerts.length && <div className="empty-state">{subjects ? '조회된 최근 알림이 없습니다.' : error ? '알림을 조회할 수 없습니다.' : '알림을 불러오는 중입니다.'}</div>}
          </section> : <section className="mvp-card subject-list"><div className="list-toolbar"><div className="table-tabs">{[{ id: 'ALL', label: '전체 대상자' }, ...(['DANGER', 'WARNING', 'NORMAL'] as Risk[]).map((risk) => ({ id: risk, label: riskLabels[risk] }))].map((item) => <button key={item.id} className={filter === item.id ? 'active' : ''} aria-pressed={filter === item.id} onClick={() => setFilter(item.id)}>{item.label}</button>)}</div><label className="search-box"><Icon name="search" /><input aria-label="대상자 검색" placeholder="이름, 주소, 전화번호 검색" value={search} onChange={(event) => setSearch(event.target.value)} /></label></div>
            <div className="mvp-table-scroll"><table><thead><tr><th>대상자 정보</th><th>위험 단계</th><th>위험 점수</th><th>최근 알림 응답</th><th>마지막 가전 활동</th><th>상세</th></tr></thead><tbody>{visible.map((subject) => <tr key={subject.subjectId}><td><div className="table-person"><span className="person-avatar">{subject.name.slice(0, 1)}</span><div><strong>{subject.name} · {subject.age}세</strong><small>{subject.address}</small></div></div></td><td><span className={`mvp-pill ${riskTone(subject.riskLevel)}`}>{riskLabels[subject.riskLevel] ?? '확인 필요'}</span></td><td><strong className="mvp-score">{subject.riskScore}<small>점</small></strong></td><td>{responseLabel(subject.latestAlert?.subjectResponse)}</td><td className="measurement-cell">{time(subject.lastActivity?.occurredAt)}<small>{subject.lastActivity?.applianceType}</small></td><td><a className="detail-button" href={`/staff/subjects/${subject.subjectId}`} aria-label={`${subject.name} 상세보기`}>상세보기<Icon name="arrow" /></a></td></tr>)}</tbody></table></div>
            {!visible.length && <div className="empty-state"><Icon name="users" /><p>{subjects ? '조건에 맞는 대상자가 없습니다.' : error ? '대상자를 조회할 수 없습니다.' : '대상자를 불러오는 중입니다.'}</p></div>}
            <div className="list-footer"><span>{subjects ? `${visible.length}명 표시` : '조회 대기'}</span><span>최근 갱신 {time(updated)}</span></div>
          </section>}
        </>}
        {feedback && <p className="mvp-feedback" role="status">{feedback}</p>}
      </div>
    </div>
  </main>
}

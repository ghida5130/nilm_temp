import { useEffect, useState } from 'react'
import { api, message, time, today } from './api'
import Icon from './Icon'
import { responseLabel, riskLabels, riskTone } from './types'
import type { Events, Power, Subject } from './types'

function EventHistory({ subjectId, version }: { subjectId: string; version: number }) {
  const [data, setData] = useState<Events | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    void api<Events>(`/api/monitoring/subjects/${subjectId}/events?size=20`, { signal: controller.signal }).then((result) => { setData(result); setError('') }).catch((cause) => { if (!controller.signal.aborted) setError(message(cause)) })
    return () => controller.abort()
  }, [subjectId, version, retry])
  async function more() {
    if (!data?.pagination.nextCursor) return
    setBusy(true)
    try {
      const result = await api<Events>(`/api/monitoring/subjects/${subjectId}/events?size=20&cursor=${encodeURIComponent(data.pagination.nextCursor)}`)
      setData((current) => current ? { ...result, events: [...new Map([...current.events, ...result.events].map((item) => [item.eventId, item])).values()] } : result)
      setError('')
    } catch (cause) { setError(message(cause)) }
    finally { setBusy(false) }
  }
  return <section className="mvp-card"><div className="mvp-row"><h2><Icon name="history" />최근 7일 이상 징후 기록</h2><button className="secondary" onClick={() => setRetry((value) => value + 1)}>새로고침</button></div>
    {error && <p className="error-box" role="alert">{error}</p>}
    {data?.events.map((event) => <article className="event-row" key={event.eventId}><span className={`mvp-pill ${riskTone(event.riskLevel)}`}>{riskLabels[event.riskLevel]} · {event.riskScore}점</span><div><strong>{event.description}</strong><p>{time(event.occurredAt)}</p><span>{responseLabel(event.alert?.subjectResponse)}{event.alert && ` · 담당자 ${({ UNCONFIRMED: '미확인', ACKNOWLEDGED: '확인', RESOLVED: '조치 완료' } as Record<string, string>)[event.alert.managerStatus] ?? event.alert.managerStatus}`}</span></div></article>)}
    {!data?.events.length && <div className="empty-state">{data ? '최근 7일간 기록된 이상 징후가 없습니다.' : error ? '기록을 불러오지 못했습니다.' : '기록을 불러오는 중입니다.'}</div>}
    {data?.pagination.hasNext && <button className="secondary" onClick={() => void more()} disabled={busy}>{busy ? '조회 중…' : '기록 더 보기'}</button>}
  </section>
}

function PowerUsage({ subjectId, version }: { subjectId: string; version: number }) {
  const [date, setDate] = useState(today)
  const [result, setResult] = useState<{ date: string; data?: Power; error?: string } | null>(null)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    if (!date) return
    const controller = new AbortController()
    void api<Power>(`/api/monitoring/subjects/${subjectId}/power-usage?date=${date}`, { signal: controller.signal }).then((data) => setResult({ date, data })).catch((cause) => { if (!controller.signal.aborted) setResult({ date, error: message(cause) }) })
    return () => controller.abort()
  }, [subjectId, date, version, retry])
  const data = result?.date === date ? result.data : undefined
  const error = result?.date === date ? result.error : undefined
  const maximum = Math.max(1, ...data?.hourlyUsage.map((bucket) => bucket.usage ?? 0) ?? [])
  return <section className="mvp-card"><div className="mvp-row"><h2><Icon name="device" />하루 전력 사용량</h2><label className="date-picker">조회일<input type="date" value={date} max={today()} onChange={(event) => setDate(event.target.value)} /></label></div>
    {error ? <p className="error-box" role="alert">{error} <button className="secondary" onClick={() => setRetry((value) => value + 1)}>다시 시도</button></p> : data ? <>
      <p className="power-total">{data.totalUsage.toLocaleString()} <small>{data.unit}</small><span>하루 누적 사용량 · 한국 시간 기준</span></p>
      <div className="power-chart" role="img" aria-label={`${date} 시간별 전력 사용량. 총 ${data.totalUsage} ${data.unit}. 아래 표에서 정확한 값 확인 가능.`}>{data.hourlyUsage.map((bucket) => <div className="power-column" key={bucket.hour} title={`${bucket.hour}시: ${bucket.status === 'NOT_YET' ? '집계 전' : `${bucket.usage ?? '—'} ${data.unit}`}`}><div className="bar-track"><span className={bucket.status.toLowerCase()} style={{ height: `${Math.max(0, (bucket.usage ?? 0) / maximum * 100)}%` }} /></div><small>{bucket.hour % 3 === 0 ? `${bucket.hour}시` : ''}</small></div>)}</div>
      <p className="card-note">진한 막대는 집계 완료, 연한 막대는 집계 중입니다.</p>
      <details><summary>시간별 수치 보기</summary><div className="mvp-table-scroll"><table><thead><tr><th>시간</th><th>사용량 ({data.unit})</th><th>집계 상태</th></tr></thead><tbody>{data.hourlyUsage.map((bucket) => <tr key={bucket.hour}><td>{bucket.hour}시</td><td>{bucket.status === 'NOT_YET' ? '—' : bucket.usage ?? '—'}</td><td>{{ COMPLETE: '완료', PARTIAL: '집계 중', NOT_YET: '집계 전' }[bucket.status]}</td></tr>)}</tbody></table></div></details>
      {data.appliances.length > 0 && <dl>{data.appliances.map((appliance) => <div key={appliance.applianceType}><dt>{appliance.applianceType}</dt><dd>{appliance.totalUsage.toLocaleString()} {data.unit}</dd></div>)}</dl>}
      <p className="card-note">마지막 집계 {time(data.updatedAt)}</p>
    </> : <div className="empty-state">{date ? '전력 사용량을 불러오는 중입니다.' : '조회일을 선택해 주세요.'}</div>}
  </section>
}

export default function SubjectDetail({ subject }: { subject: Subject }) {
  const scores = subject.riskTrend?.dailyScores ?? []
  const maximum = Math.max(100, ...scores.map((point) => point.score))
  const points = scores.map((point, index) => `${30 + index * 540 / Math.max(1, scores.length - 1)},${150 - point.score / maximum * 125}`).join(' ')
  return <>
    <div className="mvp-title"><div className="detail-identity"><span className="person-avatar large">{subject.name.slice(0, 1)}</span><div><h1>{subject.name}<span className="name-suffix">님</span></h1><span>{subject.age}세 · {subject.address}</span></div></div><span className={`mvp-pill ${riskTone(subject.riskLevel)}`}>{riskLabels[subject.riskLevel]}</span></div>
    <div className="mvp-detail-grid"><section className="mvp-card"><h2><Icon name="shield" />현재 상태</h2><dl><div><dt>위험 점수</dt><dd>{subject.riskScore}점</dd></div><div><dt>최근 가전 활동</dt><dd>{subject.lastActivity?.applianceType ?? '기록 없음'}</dd></div><div><dt>활동 시각</dt><dd>{time(subject.lastActivity?.occurredAt)}</dd></div><div><dt>상태 갱신</dt><dd>{time(subject.updatedAt)}</dd></div></dl></section>
      <section className="mvp-card"><h2><Icon name="phone" />연락 및 알림 응답</h2><dl><div><dt>전화번호</dt><dd>{subject.phone ? <a href={`tel:${subject.phone.replace(/[^+\d]/g, '')}`}>{subject.phone}</a> : '등록되지 않음'}</dd></div><div><dt>최근 응답</dt><dd>{responseLabel(subject.latestAlert?.subjectResponse)}</dd></div><div><dt>응답 시각</dt><dd>{time(subject.latestAlert?.subjectResponse.respondedAt)}</dd></div></dl></section></div>
    <section className="mvp-card"><h2><Icon name="trend" />일별 위험 점수 추이</h2>{scores.length ? <><svg className="trend-chart" viewBox="0 0 600 190" role="img" aria-label={scores.map((point) => `${point.date} ${point.score}점`).join(', ')}><path d="M30 25H570M30 87H570M30 150H570" stroke="#edf1f3" /><polyline points={points} fill="none" stroke="#1b806b" strokeWidth="3" />{scores.map((point, index) => <g key={point.date}><circle cx={30 + index * 540 / Math.max(1, scores.length - 1)} cy={150 - point.score / maximum * 125} r="4" fill="#1b806b" /><text x={30 + index * 540 / Math.max(1, scores.length - 1)} y="176" textAnchor="middle">{point.date.slice(5)}</text></g>)}</svg><details><summary>날짜별 점수 보기</summary><dl>{scores.map((point) => <div key={point.date}><dt>{point.date}</dt><dd>{point.score}점</dd></div>)}</dl></details></> : <p>아직 기록된 점수 추이가 없습니다.</p>}</section>
    <PowerUsage key={`power-${subject.subjectId}`} subjectId={subject.subjectId} version={subject.version} />
    <EventHistory key={`events-${subject.subjectId}`} subjectId={subject.subjectId} version={subject.version} />
  </>
}

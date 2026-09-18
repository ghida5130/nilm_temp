import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { clearSession, hasSession, message, saveSession } from './api'
import type { Tokens } from './api'
import Icon from './Icon'
import StaffDashboard from './StaffDashboard'
import UserDashboard from './UserDashboard'
import './Mvp.css'
import './RootApp.css'
import './App.css'

function Login({ staff, onLogin }: { staff: boolean; onLogin: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true); setError('')
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: String(form.get('email')).trim(), password: form.get('password') }),
      })
      if (!response.ok) throw new Error(response.status === 400 || response.status === 401 ? '이메일과 비밀번호를 확인해 주세요.' : `로그인할 수 없습니다. (${response.status})`)
      const tokens = await response.json() as Tokens
      if (!tokens.accessToken || !tokens.refreshToken) throw new Error('로그인 응답을 확인할 수 없습니다.')
      saveSession(tokens); onLogin()
    } catch (cause) { setError(message(cause)) }
    finally { setBusy(false) }
  }
  return <main className="mvp login-page"><section className="mvp-card login-card">
    <a className="mvp-brand" href="/"><span className="brand-symbol"><Icon name="shield" /></span>On:마음</a>
    <p className="eyebrow">일상을 잇는 안심 돌봄</p><h1>{staff ? '복지담당자' : '복지대상자'} 로그인</h1><p>등록된 계정으로 로그인해 주세요.</p>
    <form className="care-form" onSubmit={(event) => void submit(event)}>
      <label>이메일<input name="email" type="email" autoComplete="username" required /></label>
      <label>비밀번호<input name="password" type="password" autoComplete="current-password" required /></label>
      {error && <p className="error-box" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? '로그인 중…' : '로그인'}<Icon name="arrow" /></button>
    </form><a className="back-link" href="/">서비스 선택으로 돌아가기</a>
  </section></main>
}

export default function App() {
  const [loggedIn, setLoggedIn] = useState(hasSession)
  const path = window.location.pathname
  const staff = path === '/staff' || path.startsWith('/staff/')
  const user = path === '/user' || path.startsWith('/user/') || (path === '/' && new URLSearchParams(window.location.search).has('notificationId'))
  useEffect(() => {
    document.title = `On:마음 | ${staff ? '복지담당자' : user ? '안심 돌봄' : '서비스 선택'}`
    const expired = () => setLoggedIn(false)
    window.addEventListener('session-expired', expired)
    return () => window.removeEventListener('session-expired', expired)
  }, [staff, user])
  function logout() { clearSession(); setLoggedIn(false) }
  if (staff || user) {
    if (!loggedIn) return <Login staff={staff} onLogin={() => setLoggedIn(true)} />
    return staff ? <StaffDashboard onLogout={logout} /> : <UserDashboard onLogout={logout} />
  }
  return <main className="role-home"><div className="role-home__panel">
    <a className="role-home__brand" href="/"><span><Icon name="shield" /></span><strong>On:마음</strong></a>
    <div className="role-home__intro"><p>안심 돌봄 서비스</p><h1>당신의 일상에<br />따뜻한 안심을 더해요.</h1><span>이용하실 서비스를 선택해 주세요.</span></div>
    <div className="role-home__choices"><a href="/staff"><span className="role-home__choice-icon role-home__choice-icon--staff"><Icon name="users" /></span><div><strong>복지담당자</strong><small>담당 대상자의 상태와 위험 신호를 확인해요.</small></div><Icon name="arrow" /></a>
      <a href="/user"><span className="role-home__choice-icon"><Icon name="home" /></span><div><strong>복지대상자</strong><small>외출을 알리고 담당자와 연결해요.</small></div><Icon name="arrow" /></a></div>
  </div></main>
}

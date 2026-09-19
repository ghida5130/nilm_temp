import { useState } from 'react'
import '../styles/user.css'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { login } from '../api/auth'
import { getApiErrorMessage } from '../api/client'
import { saveSession } from '../api/tokenStorage'
import Brand from '../components/common/Brand'
import Icon from '../components/common/Icon'

export default function LoginPage({ role }: { role: 'staff' | 'user' }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    try {
      saveSession(await login(String(form.get('email')).trim(), String(form.get('password'))))
    } catch (cause) {
      setError(getApiErrorMessage(cause))
    } finally {
      setBusy(false)
    }
  }
  return <main className={`grid min-h-screen place-items-center p-5 ${role === 'user' ? 'user-interface user-login' : 'bg-stone-100'}`}>
    <section className={role === 'user' ? 'user-card w-full max-w-md' : 'w-full max-w-md rounded-3xl border border-stone-200 bg-white p-7 shadow-xl md:p-9'}>
      <Brand /><p className="mt-10 text-sm font-bold text-brand-700">일상을 잇는 안심 돌봄</p><h1 className="mt-2 text-3xl font-extrabold text-stone-800">{role === 'staff' ? '복지담당자' : '복지대상자'} 로그인</h1><p className="mt-2 text-stone-500">등록된 계정으로 로그인해 주세요.</p>
      <form className="mt-8 grid gap-5" onSubmit={(event) => void submit(event)}>
        <label className="grid gap-2 font-semibold text-stone-700">이메일<input className="h-13 rounded-xl border border-stone-300 px-4 outline-none focus:border-brand-500 focus:ring-3 focus:ring-brand-100" name="email" type="email" autoComplete="username" required /></label>
        <label className="grid gap-2 font-semibold text-stone-700">비밀번호<input className="h-13 rounded-xl border border-stone-300 px-4 outline-none focus:border-brand-500 focus:ring-3 focus:ring-brand-100" name="password" type="password" autoComplete="current-password" required /></label>
        {error && <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700" role="alert">{error}</p>}
        <button className="flex h-13 items-center justify-center gap-2 rounded-xl bg-brand-500 font-bold text-brand-900 transition hover:bg-brand-400 disabled:bg-stone-300" disabled={busy}>{busy ? '로그인 중…' : '로그인하기'}<Icon name="arrow" /></button>
      </form>
      <Link className="mt-6 block text-center font-semibold text-brand-700 hover:underline" to="/">서비스 선택으로 돌아가기</Link>
    </section>
  </main>
}

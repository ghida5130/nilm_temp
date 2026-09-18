export type Tokens = { accessToken: string; refreshToken: string; expiresIn: number }
const ACCESS = 'onmaeum.accessToken'
const REFRESH = 'onmaeum.refreshToken'
let generation = 0
let refreshing: Promise<void> | null = null

export function hasSession() { return Boolean(sessionStorage.getItem(ACCESS)) }
export function saveSession(tokens: Tokens) {
  sessionStorage.setItem(ACCESS, tokens.accessToken)
  sessionStorage.setItem(REFRESH, tokens.refreshToken)
}
export function clearSession() {
  generation++
  sessionStorage.removeItem(ACCESS)
  sessionStorage.removeItem(REFRESH)
}

async function errorMessage(response: Response) {
  const fallback = response.status === 401 ? '로그인이 필요하거나 로그인 시간이 만료되었습니다.'
    : response.status === 403 ? '이 화면에 접근할 권한이 없습니다.'
      : `요청을 처리하지 못했습니다. (${response.status})`
  try {
    const body = await response.json()
    return typeof body.message === 'string' ? body.message : typeof body.detail === 'string' ? body.detail : fallback
  } catch { return fallback }
}

export async function authenticatedFetch(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = sessionStorage.getItem(ACCESS)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  if (response.status === 401 && retry && sessionStorage.getItem(REFRESH)) {
    if (!refreshing) {
      const current = generation
      refreshing = (async () => {
        const result = await fetch('/api/auth/refresh', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: sessionStorage.getItem(REFRESH) }),
        })
        if (!result.ok) throw new Error(await errorMessage(result))
        const tokens: Tokens = await result.json()
        if (current !== generation) throw new Error('로그인이 종료되었습니다.')
        saveSession(tokens)
      })().finally(() => { refreshing = null })
    }
    try { await refreshing } catch (error) {
      clearSession()
      window.dispatchEvent(new Event('session-expired'))
      throw error
    }
    return authenticatedFetch(path, init, false)
  }
  if (!response.ok) throw new Error(await errorMessage(response))
  return response
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await authenticatedFetch(path, init)
  return response.status === 204 || response.headers.get('content-length') === '0'
    ? undefined as T : await response.text().then((text) => text ? JSON.parse(text) as T : undefined as T)
}
export function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}
export const message = (error: unknown) => error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.'
export const time = (value?: string | null) => value && Number.isFinite(Date.parse(value))
  ? new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false }) : '기록 없음'
export const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())

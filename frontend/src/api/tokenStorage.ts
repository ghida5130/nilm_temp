import type { AuthTokens } from '../types/auth'

const ACCESS_TOKEN_KEY = 'onmaeum.accessToken'
const REFRESH_TOKEN_KEY = 'onmaeum.refreshToken'
export const SESSION_EVENT = 'onmaeum:session-changed'
let generation = 0

function notifySessionChanged() {
  window.dispatchEvent(new Event(SESSION_EVENT))
}

export function getAccessToken() {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY)
}

export function hasSession() {
  return Boolean(getAccessToken())
}

export function getSessionGeneration() {
  return generation
}

export function saveSession(tokens: AuthTokens) {
  const wasActive = hasSession()
  sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  if (!wasActive) notifySessionChanged()
}

export function clearSession() {
  const wasActive = hasSession()
  generation += 1
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
  if (wasActive) notifySessionChanged()
}

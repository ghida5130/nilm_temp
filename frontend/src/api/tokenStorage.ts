import type { AuthTokens } from "../types/auth";

const ACCESS_TOKEN_KEY = "onmaeum.accessToken";
const REFRESH_TOKEN_KEY = "onmaeum.refreshToken";
export const SESSION_EVENT = "onmaeum:session-changed";
let generation = 0;

function hasPersistentSession() {
  return Boolean(localStorage.getItem(ACCESS_TOKEN_KEY));
}

function getStoredToken(key: string) {
  return localStorage.getItem(key) ?? sessionStorage.getItem(key);
}

function notifySessionChanged() {
  window.dispatchEvent(new Event(SESSION_EVENT));
}

export function getAccessToken() {
  return getStoredToken(ACCESS_TOKEN_KEY);
}

export function getRefreshToken() {
  return getStoredToken(REFRESH_TOKEN_KEY);
}

export function hasSession() {
  return Boolean(getAccessToken());
}

export function getSessionGeneration() {
  return generation;
}

export function saveSession(tokens: AuthTokens, persistent = hasPersistentSession()) {
  const wasActive = hasSession();
  const storage = persistent ? localStorage : sessionStorage;
  const previousStorage = persistent ? sessionStorage : localStorage;
  previousStorage.removeItem(ACCESS_TOKEN_KEY);
  previousStorage.removeItem(REFRESH_TOKEN_KEY);
  storage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  storage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  if (!wasActive) notifySessionChanged();
}

export function clearSession() {
  const wasActive = hasSession();
  generation += 1;
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  if (wasActive) notifySessionChanged();
}

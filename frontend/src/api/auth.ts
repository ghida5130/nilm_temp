import type { AuthTokens } from '../types/auth'
import { publicApi } from './client'

export async function login(email: string, password: string) {
  const { data } = await publicApi.post<AuthTokens>('/auth/login', { email, password })
  return data
}

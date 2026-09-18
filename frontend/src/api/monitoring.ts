import type { Events, MyDashboard, Power, Subject, SubjectRegistration } from '../types/monitoring'
import { request } from './client'

export const getSubjects = () => request<{ subjects: Subject[] }>({ url: '/monitoring/dashboard' })
export const getMyDashboard = () => request<MyDashboard>({ url: '/monitoring/my-dashboard' })
export const registerSubject = (subject: SubjectRegistration) => request<void>({ url: '/monitoring/subjects', method: 'POST', data: subject })
export const updateAwayMode = (data: { enabled: boolean; startsAt?: string; endsAt?: string }) => request<void>({ url: '/monitoring/my-dashboard/away-mode', method: 'PUT', data })
export const answerNotification = (notificationId: string, answer: 'yes' | 'no') => request<void>({
  url: `/monitoring/notifications/${notificationId}/responses`, method: 'PUT',
  data: { answer, source: 'user', respondedAt: new Date().toISOString() },
})
export const getSubjectEvents = (subjectId: string, cursor?: string) => request<Events>({
  url: `/monitoring/subjects/${subjectId}/events`, params: { size: 20, cursor },
})
export const getPowerUsage = (subjectId: string, date: string) => request<Power>({
  url: `/monitoring/subjects/${subjectId}/power-usage`, params: { date },
})

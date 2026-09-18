import { useEffect, useState } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { answerNotification, getMyDashboard, getPowerUsage, getSubjectEvents, getSubjects, registerSubject, updateAwayMode } from '../api/monitoring'
import { getApiErrorMessage } from '../api/client'
import { readSubjectStream } from '../api/stream'
import type { StatusEvent, SubjectRegistration } from '../types/monitoring'

export const monitoringKeys = {
  subjects: ['monitoring', 'subjects'] as const,
  myDashboard: ['monitoring', 'my-dashboard'] as const,
  events: (subjectId: string) => ['monitoring', 'subjects', subjectId, 'events'] as const,
  power: (subjectId: string, date: string) => ['monitoring', 'subjects', subjectId, 'power', date] as const,
}

export function useSubjectsQuery() {
  return useQuery({ queryKey: monitoringKeys.subjects, queryFn: getSubjects, refetchInterval: 60_000 })
}

export function useMyDashboardQuery() {
  return useQuery({ queryKey: monitoringKeys.myDashboard, queryFn: getMyDashboard, refetchInterval: 30_000 })
}

export function useRegisterSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (subject: SubjectRegistration) => registerSubject(subject),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: monitoringKeys.subjects }),
  })
}

export function useAwayMode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateAwayMode,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: monitoringKeys.myDashboard }),
  })
}

export function useNotificationResponse() {
  return useMutation({ mutationFn: ({ notificationId, answer }: { notificationId: string; answer: 'yes' | 'no' }) => answerNotification(notificationId, answer) })
}

export function useSubjectEvents(subjectId: string, version: number) {
  return useInfiniteQuery({
    queryKey: [...monitoringKeys.events(subjectId), version],
    queryFn: ({ pageParam }) => getSubjectEvents(subjectId, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  })
}

export function usePowerUsage(subjectId: string, date: string, version: number) {
  return useQuery({
    queryKey: [...monitoringKeys.power(subjectId, date), version],
    queryFn: () => getPowerUsage(subjectId, date),
    enabled: Boolean(date),
  })
}

export function useSubjectStream() {
  const queryClient = useQueryClient()
  const [connection, setConnection] = useState<'connecting' | 'connected' | 'disconnected'>('connecting')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState<StatusEvent | null>(null)
  useEffect(() => {
    let active = true
    let retries = 0
    let timer: number | undefined
    let controller: AbortController | undefined
    const connect = async () => {
      controller = new AbortController()
      try {
        await readSubjectStream(controller.signal, () => {
          if (!active) return
          retries = 0
          setConnection('connected')
          setError('')
          void queryClient.invalidateQueries({ queryKey: monitoringKeys.subjects })
        }, (eventName, payload) => {
          if (!active || eventName !== 'subject-status') return
          const event = payload as StatusEvent
          queryClient.setQueryData<{ subjects: import('../types/monitoring').Subject[] }>(monitoringKeys.subjects, (current) => current ? {
            subjects: current.subjects.map((subject) => subject.subjectId === event.subjectId && event.version > subject.version ? { ...subject, ...event } : subject),
          } : current)
          if (event.riskLevel === 'DANGER' || event.trigger === 'SUBJECT_RESPONSE') setNotice(event)
        })
      } catch (cause) {
        if (active) setError(getApiErrorMessage(cause))
      }
      if (active) {
        setConnection('disconnected')
        timer = window.setTimeout(() => void connect(), Math.min(30_000, 1_000 * 2 ** Math.min(retries++, 5)))
      }
    }
    void connect()
    return () => {
      active = false
      controller?.abort()
      window.clearTimeout(timer)
    }
  }, [queryClient])
  return { connection, error, notice, setNotice }
}

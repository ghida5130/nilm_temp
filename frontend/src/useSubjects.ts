import { useCallback, useEffect, useRef, useState } from 'react'
import { api, message } from './api'
import { readStream } from './stream'
import type { StatusEvent, Subject } from './types'

export default function useSubjects() {
  const [subjects, setSubjects] = useState<Subject[] | null>(null)
  const [error, setError] = useState('')
  const [streamError, setStreamError] = useState('')
  const [connection, setConnection] = useState('connecting')
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState<StatusEvent | null>(null)
  const [updated, setUpdated] = useState<string | null>(null)
  const latest = useRef(new Map<string, StatusEvent>())
  const requestId = useRef(0)
  const refreshController = useRef<AbortController | null>(null)
  const refresh = useCallback(async () => {
    const id = ++requestId.current
    refreshController.current?.abort()
    const controller = new AbortController()
    refreshController.current = controller
    setLoading(true)
    try {
      const data = await api<{ subjects: Subject[] }>('/api/monitoring/dashboard', { signal: controller.signal })
      if (id !== requestId.current || controller.signal.aborted) return
      setSubjects(data.subjects.map((subject) => {
        const event = latest.current.get(subject.subjectId)
        return event && event.version > subject.version ? { ...subject, ...event } : subject
      }))
      setUpdated(new Date().toISOString()); setError('')
    } catch (cause) { if (!controller.signal.aborted && id === requestId.current) setError(message(cause)) }
    finally { if (id === requestId.current && !controller.signal.aborted) setLoading(false) }
  }, [])
  useEffect(() => {
    let active = true
    let retries = 0
    let timer: number | undefined
    let controller: AbortController
    const connect = async () => {
      controller = new AbortController()
      try {
        await readStream(controller.signal, () => {
          if (!active) return
          retries = 0; setConnection('connected'); setStreamError(''); void refresh()
        }, (eventName, payload) => {
          if (!active || eventName !== 'subject-status') return
          const event = payload as StatusEvent
          if (typeof event.subjectId !== 'string' || !Number.isFinite(event.version)) throw new Error('잘못된 상태 데이터')
          const previous = latest.current.get(event.subjectId)
          if (previous && previous.version >= event.version) return
          latest.current.set(event.subjectId, event)
          setSubjects((current) => current?.map((subject) => subject.subjectId === event.subjectId && event.version > subject.version ? { ...subject, ...event } : subject) ?? null)
          setUpdated(new Date().toISOString())
          if (event.riskLevel === 'DANGER' || event.trigger === 'SUBJECT_RESPONSE') setNotice(event)
        })
      } catch (cause) { if (active) setStreamError(message(cause)) }
      if (active) {
        setConnection('disconnected')
        timer = window.setTimeout(() => void connect(), Math.min(30000, 1000 * 2 ** Math.min(retries++, 5)))
      }
    }
    const initial = window.setTimeout(() => { void refresh(); void connect() }, 0)
    const interval = window.setInterval(() => { if (document.visibilityState === 'visible') void refresh() }, 60000)
    const visible = () => { if (document.visibilityState === 'visible') void refresh() }
    document.addEventListener('visibilitychange', visible)
    return () => {
      active = false; controller?.abort(); refreshController.current?.abort()
      window.clearTimeout(initial); window.clearTimeout(timer); window.clearInterval(interval); document.removeEventListener('visibilitychange', visible)
    }
  }, [refresh])
  return { subjects, error, streamError, connection, loading, notice, setNotice, updated, refresh }
}

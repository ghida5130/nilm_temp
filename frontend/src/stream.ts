import { authenticatedFetch } from './api.ts'

export function parseEvent(block: string): { event: string; data: string } | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  return data.length ? { event, data: data.join('\n') } : null
}

export async function readStream(signal: AbortSignal, onOpen: () => void, onEvent: (event: string, data: unknown) => void) {
  const response = await authenticatedFetch('/api/monitoring/stream', { signal, headers: { Accept: 'text/event-stream' } })
  if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('실시간 응답을 확인할 수 없습니다.')
  onOpen()
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      let boundary: number
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const parsed = parseEvent(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        if (parsed) {
          try { onEvent(parsed.event, JSON.parse(parsed.data)) }
          catch { throw new Error('실시간 데이터 형식을 확인할 수 없습니다.') }
        }
      }
    }
  } finally { await reader.cancel().catch(() => undefined); reader.releaseLock() }
}

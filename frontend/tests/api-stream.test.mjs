import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { api, clearSession, saveSession } from '../src/api.ts'
import { parseEvent, readStream } from '../src/stream.ts'

const originalFetch = globalThis.fetch
const storage = new Map()
globalThis.sessionStorage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: (key) => storage.delete(key),
}
globalThis.window = new EventTarget()
afterEach(() => { globalThis.fetch = originalFetch; clearSession() })

test('SSE 주석과 여러 줄 데이터 구분', () => {
  assert.equal(parseEvent(': heartbeat'), null)
  assert.deepEqual(parseEvent(': comment\nevent: subject-status\ndata: {\ndata: "version": 2}'), {
    event: 'subject-status', data: '{\n"version": 2}',
  })
})

test('인증 헤더와 청크 경계의 CRLF, 복수 SSE 이벤트 처리', async () => {
  saveSession({ accessToken: 'test-access', refreshToken: 'test-refresh', expiresIn: 60 })
  const encoder = new TextEncoder()
  globalThis.fetch = async (path, init) => {
    assert.equal(path, '/api/monitoring/stream')
    assert.equal(init.headers.get('Authorization'), 'Bearer test-access')
    return new Response(new ReadableStream({ start(controller) {
      for (const chunk of [': connected\r', '\n\r', '\nevent: subject-status\r\ndata: {"subjectId":"1","version":2}\r', '\n\r\nevent: subject-status\ndata: {"subjectId":"1","version":3}\n\n']) controller.enqueue(encoder.encode(chunk))
      controller.close()
    } }), { headers: { 'Content-Type': 'text/event-stream' } })
  }
  let opened = false
  const received = []
  await readStream(new AbortController().signal, () => { opened = true }, (event, data) => received.push([event, data.version]))
  assert.equal(opened, true)
  assert.deepEqual(received, [['subject-status', 2], ['subject-status', 3]])
})

test('HTML 오류 응답을 실시간 연결 성공으로 처리하지 않음', async () => {
  globalThis.fetch = async () => new Response('<html />', { headers: { 'Content-Type': 'text/html' } })
  await assert.rejects(readStream(new AbortController().signal, () => assert.fail('연결 성공으로 처리됨'), () => {}), /실시간 응답/)
})

test('동시 401 요청은 토큰 갱신 한 번 후 각각 재시도', async () => {
  saveSession({ accessToken: 'old', refreshToken: 'refresh', expiresIn: 60 })
  let refreshCount = 0
  globalThis.fetch = async (path, init) => {
    if (path === '/api/auth/refresh') {
      refreshCount++
      await new Promise((resolve) => setTimeout(resolve, 10))
      return Response.json({ accessToken: 'new', refreshToken: 'next', expiresIn: 60 })
    }
    return init.headers.get('Authorization') === 'Bearer new'
      ? Response.json({ ok: true }) : new Response(null, { status: 401 })
  }
  assert.deepEqual(await Promise.all([api('/one'), api('/two')]), [{ ok: true }, { ok: true }])
  assert.equal(refreshCount, 1)
})

test('본문 없는 등록 및 외출 설정 응답 처리', async () => {
  globalThis.fetch = async () => new Response(null, { status: 204 })
  assert.equal(await api('/away'), undefined)
  globalThis.fetch = async () => new Response(null, { status: 201 })
  assert.equal(await api('/subjects'), undefined)
})

test('로그아웃 중 완료된 토큰 갱신으로 세션이 복구되지 않음', async () => {
  saveSession({ accessToken: 'old', refreshToken: 'refresh', expiresIn: 60 })
  globalThis.fetch = async (path) => {
    if (path === '/api/auth/refresh') {
      clearSession()
      return Response.json({ accessToken: 'new', refreshToken: 'next', expiresIn: 60 })
    }
    return new Response(null, { status: 401 })
  }
  await assert.rejects(api('/dashboard'), /로그인이 종료/)
  assert.equal(storage.size, 0)
})

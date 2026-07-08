import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { RealtimeClient, type RecordChangedEvent } from './RealtimeClient'

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  static OPEN = 1
  readyState = 0
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null

  url: string

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.readyState = 3
    this.onclose?.()
  }

  open() {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }
}

function makeClient(events: RecordChangedEvent[]) {
  return new RealtimeClient({
    urlFactory: vi.fn(async () => `wss://x/ws/realtime?token=t${FakeWebSocket.instances.length}`),
    onEvent: (e) => events.push(e),
    webSocketFactory: (url) => new FakeWebSocket(url) as unknown as WebSocket,
    baseReconnectDelayMs: 10,
  })
}

beforeEach(() => {
  FakeWebSocket.instances = []
  vi.useFakeTimers()
})
afterEach(() => vi.useRealTimers())

describe('RealtimeClient', () => {
  it('resubscribes every registered collection on open', async () => {
    const client = makeClient([])
    client.subscribe('orders')
    client.subscribe('customers')
    await client.connect()
    const ws = FakeWebSocket.instances[0]
    ws.open()

    expect(ws.sent.map((s) => JSON.parse(s))).toEqual([
      { action: 'subscribe', collection: 'orders' },
      { action: 'subscribe', collection: 'customers' },
    ])
    client.close()
  })

  it('delivers record.changed events and ignores other frames', async () => {
    const events: RecordChangedEvent[] = []
    const client = makeClient(events)
    client.subscribe('orders')
    await client.connect()
    const ws = FakeWebSocket.instances[0]
    ws.open()

    ws.onmessage?.({ data: JSON.stringify({ action: 'subscribed', collection: 'orders' }) })
    ws.onmessage?.({ data: 'not-json' })
    ws.onmessage?.({
      data: JSON.stringify({
        event: 'record.changed',
        collection: 'orders',
        changeType: 'UPDATE',
        recordId: 'r1',
      }),
    })

    expect(events).toHaveLength(1)
    expect(events[0].collection).toBe('orders')
    client.close()
  })

  it('reconnects with a fresh URL after a close and resubscribes', async () => {
    const client = makeClient([])
    client.subscribe('orders')
    await client.connect()
    FakeWebSocket.instances[0].open()
    FakeWebSocket.instances[0].close()

    await vi.advanceTimersByTimeAsync(50)
    expect(FakeWebSocket.instances).toHaveLength(2)
    // urlFactory ran again → different token per connect
    expect(FakeWebSocket.instances[1].url).not.toBe(FakeWebSocket.instances[0].url)
    FakeWebSocket.instances[1].open()
    expect(FakeWebSocket.instances[1].sent.map((s) => JSON.parse(s))).toEqual([
      { action: 'subscribe', collection: 'orders' },
    ])
    client.close()
  })

  it('stops reconnecting after close()', async () => {
    const client = makeClient([])
    await client.connect()
    client.close()
    await vi.advanceTimersByTimeAsync(1000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })
})

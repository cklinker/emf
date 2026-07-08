/**
 * Minimal client for the gateway's /ws/realtime socket (app-surfacing slice 4).
 *
 * Server contract (RealtimeWebSocketHandler):
 * - connect wss://…/ws/realtime?token=<jwt> (close 4000 policy violation, 4001 token expired)
 * - send {"action":"subscribe"|"unsubscribe","collection":"<name>"} (max 50 subs/session)
 * - recv {"event":"record.changed","collection","changeType","recordId","data"|null,"timestamp"}
 *
 * INVALIDATION-ONLY RULE (authoritative, see conventions.md): pushed `data` is never
 * applied to caches — the server does no per-subscriber FLS, so consumers only invalidate
 * queries and refetch through the authorized JSON:API path.
 */

export interface RecordChangedEvent {
  event: 'record.changed'
  collection: string
  changeType: 'CREATE' | 'UPDATE' | 'DELETE'
  recordId: string
  timestamp?: string
}

export interface RealtimeClientOptions {
  /** Returns a fresh connect URL (fetches a current token) — called on every (re)connect. */
  urlFactory: () => Promise<string>
  onEvent: (event: RecordChangedEvent) => void
  /** Injectable for tests. */
  webSocketFactory?: (url: string) => WebSocket
  /** Base reconnect delay in ms (doubles per attempt, capped at 30s). */
  baseReconnectDelayMs?: number
}

const MAX_RECONNECT_DELAY_MS = 30_000
export const MAX_SUBSCRIPTIONS = 50

export class RealtimeClient {
  private readonly options: Required<Pick<RealtimeClientOptions, 'urlFactory' | 'onEvent'>> &
    RealtimeClientOptions
  private socket: WebSocket | null = null
  private subscriptions = new Set<string>()
  private reconnectAttempts = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private closed = false

  constructor(options: RealtimeClientOptions) {
    this.options = options
  }

  /** Idempotent: registers the collection and sends subscribe when the socket is open. */
  subscribe(collection: string): void {
    if (this.subscriptions.has(collection)) return
    if (this.subscriptions.size >= MAX_SUBSCRIPTIONS) return
    this.subscriptions.add(collection)
    this.send({ action: 'subscribe', collection })
  }

  unsubscribe(collection: string): void {
    if (!this.subscriptions.delete(collection)) return
    this.send({ action: 'unsubscribe', collection })
  }

  async connect(): Promise<void> {
    if (this.closed || this.socket) return
    let url: string
    try {
      url = await this.options.urlFactory()
    } catch {
      this.scheduleReconnect()
      return
    }
    const factory = this.options.webSocketFactory ?? ((u: string) => new WebSocket(u))
    let socket: WebSocket
    try {
      socket = factory(url)
    } catch {
      this.scheduleReconnect()
      return
    }
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempts = 0
      // Re-establish every registered subscription on each (re)connect.
      for (const collection of this.subscriptions) {
        socket.send(JSON.stringify({ action: 'subscribe', collection }))
      }
    }
    socket.onmessage = (message: MessageEvent) => {
      try {
        const parsed = JSON.parse(String(message.data))
        if (parsed?.event === 'record.changed' && typeof parsed.collection === 'string') {
          this.options.onEvent(parsed as RecordChangedEvent)
        }
      } catch {
        // Non-JSON or unknown frame — ignore.
      }
    }
    socket.onclose = () => {
      this.socket = null
      // 4001 (token expired) needs no special casing: urlFactory fetches a fresh
      // token on every reconnect.
      this.scheduleReconnect()
    }
    socket.onerror = () => {
      // onclose follows; nothing to do here.
    }
  }

  close(): void {
    this.closed = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.socket?.close()
    this.socket = null
  }

  private send(payload: Record<string, string>): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload))
    }
  }

  private scheduleReconnect(): void {
    if (this.closed || this.reconnectTimer) return
    const base = this.options.baseReconnectDelayMs ?? 1000
    const delay = Math.min(base * 2 ** this.reconnectAttempts, MAX_RECONNECT_DELAY_MS)
    this.reconnectAttempts += 1
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      void this.connect()
    }, delay)
  }
}

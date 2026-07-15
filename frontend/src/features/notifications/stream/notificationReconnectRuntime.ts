import { createRecentNotificationIdCache } from '@/features/notifications/stream/recentNotificationIdCache'
import { getNotificationReconnectDelay } from '@/features/notifications/stream/notificationStreamStateModel'

const RECENT_NOTIFICATION_ID_LIMIT = 200
const MAX_RECONNECT_DELAY_MS = 60000

function defaultIsBrowserOnline(): boolean {
  return typeof navigator === 'undefined' ? true : navigator.onLine
}

export class NotificationStreamRuntime {
  readonly state = {
    streamAbortController: null as AbortController | null,
    reconnectTimer: null as ReturnType<typeof setTimeout> | null,
    isConnecting: false,
    closedManually: false,
    reconnectAttempt: 0,
    reconnectWhenOnline: false,
    onlineListenerAttached: false,
    recentNotificationIds: createRecentNotificationIdCache(RECENT_NOTIFICATION_ID_LIMIT),
  }

  private reconnectCallback: (() => void) | null = null
  private isBrowserOnlineProvider = defaultIsBrowserOnline

  reset() {
    this.state.closedManually = true
    this.clearReconnectTimer()
    this.abortStream()
    this.state.isConnecting = false
    this.state.reconnectAttempt = 0
    this.state.reconnectWhenOnline = false
    this.detachOnlineReconnectListener()
    this.reconnectCallback = null
    this.state.recentNotificationIds.clear()
    this.isBrowserOnlineProvider = defaultIsBrowserOnline
  }

  setOnlineProvider(provider?: () => boolean) {
    this.isBrowserOnlineProvider = provider ?? defaultIsBrowserOnline
  }

  isBrowserOnline() {
    return this.isBrowserOnlineProvider()
  }

  setReconnectCallback(callback: () => void) {
    this.reconnectCallback = callback
  }

  reconnect() {
    this.reconnectCallback?.()
  }

  clearReconnectTimer() {
    if (!this.state.reconnectTimer) return
    clearTimeout(this.state.reconnectTimer)
    this.state.reconnectTimer = null
  }

  abortStream() {
    if (!this.state.streamAbortController) return
    this.state.streamAbortController.abort()
    this.state.streamAbortController = null
  }

  attachOnlineReconnectListener() {
    if (typeof window === 'undefined' || this.state.onlineListenerAttached) return
    window.addEventListener('online', this.handleBrowserOnline)
    this.state.onlineListenerAttached = true
  }

  detachOnlineReconnectListener() {
    if (typeof window === 'undefined' || !this.state.onlineListenerAttached) return
    window.removeEventListener('online', this.handleBrowserOnline)
    this.state.onlineListenerAttached = false
  }

  getBackoffDelay(baseDelayMs: number) {
    return getNotificationReconnectDelay(
      baseDelayMs,
      this.state.reconnectAttempt,
      MAX_RECONNECT_DELAY_MS,
    )
  }

  scheduleReconnect(delayMs: number) {
    if (this.state.closedManually) return

    if (!this.isBrowserOnline()) {
      this.state.reconnectWhenOnline = true
      this.attachOnlineReconnectListener()
      return
    }

    this.detachOnlineReconnectListener()
    this.clearReconnectTimer()

    const backoffDelayMs = this.getBackoffDelay(delayMs)
    this.state.reconnectAttempt += 1
    this.state.reconnectTimer = setTimeout(() => {
      this.state.reconnectTimer = null
      this.reconnect()
    }, backoffDelayMs)
  }

  handleBrowserOnline = () => {
    if (this.state.closedManually || !this.state.reconnectWhenOnline) return
    this.state.reconnectWhenOnline = false
    this.scheduleReconnect(0)
  }
}

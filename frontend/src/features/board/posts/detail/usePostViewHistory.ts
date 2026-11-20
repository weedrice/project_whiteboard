import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import { API_ERROR_CODES } from '@/api/errorCodes'
import { extractErrorCode } from '@/utils/errorHandler'
import { postApi, type ViewHistoryPayload } from '@/api/post'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'

const FLUSH_INTERVAL_MS = 30_000
const MAX_REQUEST_DURATION_MS = 86_400_000

function createViewSessionId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // getRandomValues is also available on HTTP development origins.
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}

interface TrackingState {
  postId: string | number
  viewSessionId: string
  confirmedDurationMs: number
  pendingDurationMs: number
  pendingCommentIds: Set<number>
  confirmedCommentId: number | null
  rejectedCommentIds: Set<number>
  activeStartedAt: number | null
  inFlight: boolean
  finalFlushRequested: boolean
  controller: AbortController | null
  sessionGeneration: number
  userId: string | number
}

interface PostViewHistoryOptions {
  postId: Ref<string | number>
  enabled: Ref<boolean>
  initialLastReadCommentId: Ref<number | null | undefined>
  sessionGeneration: Ref<number>
  userId: Ref<string | number | null | undefined>
}

export function usePostViewHistory(options: PostViewHistoryOptions) {
  const states = new Map<string, TrackingState>()
  let currentKey: string | null = null
  let disposed = false
  let flushTimer: ReturnType<typeof setInterval> | null = null
  let windowFocused = document.hasFocus()

  const stateKey = (
    postId: string | number,
    sessionGeneration = options.sessionGeneration.value,
    userId = options.userId.value,
  ) => `${sessionGeneration}:${String(userId ?? 'anonymous')}:${String(postId)}`
  const now = () => performance.now()

  function resolveState(postId: string | number, initialLastReadCommentId?: number | null) {
    const sessionGeneration = options.sessionGeneration.value
    const userId = options.userId.value
    if (userId == null) return null
    const key = stateKey(postId, sessionGeneration, userId)
    const existing = states.get(key)
    if (existing) {
      if (existing.confirmedCommentId == null && existing.pendingCommentIds.size === 0 && initialLastReadCommentId != null) {
        existing.confirmedCommentId = initialLastReadCommentId
      }
      return existing
    }

    const state: TrackingState = {
      postId,
      viewSessionId: createViewSessionId(),
      confirmedDurationMs: 0,
      pendingDurationMs: 0,
      pendingCommentIds: new Set(),
      confirmedCommentId: initialLastReadCommentId ?? null,
      rejectedCommentIds: new Set(),
      activeStartedAt: null,
      inFlight: false,
      finalFlushRequested: false,
      controller: null,
      sessionGeneration,
      userId,
    }
    states.set(key, state)
    return state
  }

  function currentState() {
    return currentKey ? states.get(currentKey) ?? null : null
  }

  function canAccrue() {
    return Boolean(
      !disposed
      && options.enabled.value
      && currentKey
      && document.visibilityState === 'visible'
      && windowFocused,
    )
  }

  function accrue(state: TrackingState) {
    if (state.activeStartedAt == null) return
    state.pendingDurationMs += Math.max(0, now() - state.activeStartedAt)
    state.activeStartedAt = null
  }

  function startAccruing() {
    const state = currentState()
    if (!state || state.activeStartedAt != null || !canAccrue()) return
    state.activeStartedAt = now()
  }

  function stopAccruing() {
    const state = currentState()
    if (state) accrue(state)
  }

  function isCurrentSession(state: TrackingState) {
    return state.sessionGeneration === options.sessionGeneration.value
      && String(state.userId) === String(options.userId.value ?? '')
  }

  function finishDisposal() {
    if (!disposed || [...states.values()].some((state) => state.inFlight)) return
    stopSessionBoundary()
    states.clear()
  }

  async function flushState(state: TrackingState) {
    if (state.inFlight || !isCurrentSession(state)) return
    state.finalFlushRequested = false

    if (state === currentState()) {
      accrue(state)
      startAccruing()
    }

    const durationMs = Math.min(Math.floor(state.pendingDurationMs), MAX_REQUEST_DURATION_MS)
    const sessionDurationMs = state.confirmedDurationMs + durationMs
    let sentCommentId: number | undefined
    for (const commentId of state.pendingCommentIds) {
      if (sentCommentId === undefined || commentId > sentCommentId) sentCommentId = commentId
    }
    if (durationMs <= 0 && sentCommentId === undefined) return

    state.pendingDurationMs -= durationMs
    state.inFlight = true
    const controller = new AbortController()
    state.controller = controller
    const durationPayload: ViewHistoryPayload = {
      durationMs,
      viewSessionId: state.viewSessionId,
      sessionDurationMs,
    }

    const send = (payload: ViewHistoryPayload) => postApi.updateViewHistory(state.postId, payload, {
      expectedSession: { sessionGeneration: state.sessionGeneration, userId: state.userId },
      signal: controller.signal,
      skipAuthRefresh: true,
      skipGlobalErrorHandler: true,
    })
    let acknowledged = false
    let retryingWithoutCursor = false
    try {
      if (sentCommentId !== undefined) {
        try {
          await send({ ...durationPayload, lastReadCommentId: sentCommentId })
          if (controller.signal.aborted || !isCurrentSession(state)) return
          state.confirmedCommentId = sentCommentId
          for (const candidates of [state.pendingCommentIds, state.rejectedCommentIds]) {
            for (const commentId of candidates) {
              if (commentId <= sentCommentId) candidates.delete(commentId)
            }
          }
        } catch (error) {
          if (controller.signal.aborted || !isCurrentSession(state)) return
          if (!isAxiosError(error) || error.response?.status !== 400
            || extractErrorCode(error) !== API_ERROR_CODES.INVALID_INPUT_VALUE) throw error

          // A rejected comment must not block valid progress below it.
          state.rejectedCommentIds.add(sentCommentId)
          state.pendingCommentIds.delete(sentCommentId)
          if (durationMs > 0) {
            retryingWithoutCursor = true
            await send(durationPayload)
          }
        }
      } else {
        await send(durationPayload)
      }
      if (controller.signal.aborted || !isCurrentSession(state)) return
      acknowledged = true
      state.confirmedDurationMs = sessionDurationMs
    } catch {
      // The server may have committed the request. Keep its duration unconfirmed until acknowledged.
      if (!controller.signal.aborted && isCurrentSession(state)) {
        if (retryingWithoutCursor && Math.floor(state.pendingDurationMs) === 0
          && state.pendingCommentIds.size === 0) {
          state.finalFlushRequested = false
        }
        state.pendingDurationMs += durationMs
      }
    } finally {
      state.inFlight = false
      if (state.controller === controller) state.controller = null
      if (state.finalFlushRequested || (disposed && acknowledged)) {
        state.finalFlushRequested = false
        if (
          !controller.signal.aborted
          && isCurrentSession(state)
          && (Math.floor(state.pendingDurationMs) > 0 || state.pendingCommentIds.size > 0)
        ) {
          void flushState(state)
        }
      }
      finishDisposal()
    }
  }

  function flushAll() {
    states.forEach((state) => {
      void flushState(state)
    })
  }

  function handleActivityChange() {
    if (canAccrue()) {
      startAccruing()
      return
    }

    stopAccruing()
    flushAll()
  }

  function handleFocus() {
    windowFocused = true
    handleActivityChange()
  }

  function handleBlur() {
    windowFocused = false
    handleActivityChange()
  }

  function setLastReadCommentId(commentId: number) {
    const state = currentState()
    if (!state || !Number.isFinite(commentId) || commentId <= (state.confirmedCommentId ?? 0)
      || state.rejectedCommentIds.has(commentId)) return
    state.pendingCommentIds.add(commentId)
  }

  watch(
    [
      options.postId,
      options.enabled,
      options.initialLastReadCommentId,
      options.sessionGeneration,
      options.userId,
    ],
    ([postId, enabled, initialLastReadCommentId, sessionGeneration, userId]) => {
      const nextKey = userId == null ? null : stateKey(postId, sessionGeneration, userId)
      if (currentKey && currentKey !== nextKey) {
        stopAccruing()
        flushAll()
      }

      if (!enabled || !postId || userId == null) {
        stopAccruing()
        flushAll()
        currentKey = null
        return
      }

      const state = resolveState(postId, initialLastReadCommentId)
      if (!state) {
        currentKey = null
        return
      }
      currentKey = stateKey(state.postId)
      startAccruing()
    },
    { immediate: true },
  )

  onMounted(() => {
    document.addEventListener('visibilitychange', handleActivityChange)
    window.addEventListener('focus', handleFocus)
    window.addEventListener('blur', handleBlur)
    flushTimer = setInterval(() => {
      flushAll()
    }, FLUSH_INTERVAL_MS)
    startAccruing()
  })

  const stopSessionBoundary = subscribeAuthSessionBoundary(() => {
    currentKey = null
    states.forEach((state) => state.controller?.abort())
    states.clear()
    finishDisposal()
  })

  onBeforeUnmount(() => {
    disposed = true
    stopAccruing()
    currentKey = null
    states.forEach((state) => { state.finalFlushRequested = state.inFlight })
    flushAll()
    document.removeEventListener('visibilitychange', handleActivityChange)
    window.removeEventListener('focus', handleFocus)
    window.removeEventListener('blur', handleBlur)
    if (flushTimer) {
      clearInterval(flushTimer)
      flushTimer = null
    }
    finishDisposal()
  })

  return {
    setLastReadCommentId,
    flush: flushAll,
  }
}

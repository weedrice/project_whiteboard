import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import { postApi, type ViewHistoryPayload } from '@/api/post'

const FLUSH_INTERVAL_MS = 30_000

interface TrackingState {
  postId: string | number
  pendingDurationMs: number
  lastReadCommentId: number | null
  lastSentCommentId: number | null
  activeStartedAt: number | null
  inFlight: boolean
}

interface PostViewHistoryOptions {
  postId: Ref<string | number>
  enabled: Ref<boolean>
  initialLastReadCommentId: Ref<number | null | undefined>
}

export function usePostViewHistory(options: PostViewHistoryOptions) {
  const states = new Map<string, TrackingState>()
  let currentKey: string | null = null
  let flushTimer: ReturnType<typeof setInterval> | null = null
  let windowFocused = document.hasFocus()

  const stateKey = (postId: string | number) => String(postId)
  const now = () => performance.now()

  function resolveState(postId: string | number, initialLastReadCommentId?: number | null) {
    const key = stateKey(postId)
    const existing = states.get(key)
    if (existing) {
      if (existing.lastReadCommentId == null && initialLastReadCommentId != null) {
        existing.lastReadCommentId = initialLastReadCommentId
        existing.lastSentCommentId = initialLastReadCommentId
      }
      return existing
    }

    const state: TrackingState = {
      postId,
      pendingDurationMs: 0,
      lastReadCommentId: initialLastReadCommentId ?? null,
      lastSentCommentId: initialLastReadCommentId ?? null,
      activeStartedAt: null,
      inFlight: false,
    }
    states.set(key, state)
    return state
  }

  function currentState() {
    return currentKey ? states.get(currentKey) ?? null : null
  }

  function canAccrue() {
    return Boolean(
      options.enabled.value
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

  async function flushState(state: TrackingState) {
    if (state.inFlight) return

    if (state === currentState()) {
      accrue(state)
      startAccruing()
    }

    const durationMs = Math.floor(state.pendingDurationMs)
    const sentCommentId = state.lastReadCommentId
    const commentChanged = sentCommentId !== state.lastSentCommentId
    if (durationMs <= 0 && !commentChanged) return

    state.pendingDurationMs -= durationMs
    state.inFlight = true
    const payload: ViewHistoryPayload = {
      durationMs,
      ...(sentCommentId != null ? { lastReadCommentId: sentCommentId } : {}),
    }

    try {
      await postApi.updateViewHistory(state.postId, payload, { skipGlobalErrorHandler: true })
      state.lastSentCommentId = sentCommentId
    } catch {
      state.pendingDurationMs += durationMs
    } finally {
      state.inFlight = false
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
    if (!state || !Number.isFinite(commentId) || commentId <= 0) return
    state.lastReadCommentId = commentId
  }

  watch(
    [options.postId, options.enabled, options.initialLastReadCommentId],
    ([postId, enabled, initialLastReadCommentId], [previousPostId]) => {
      if (currentKey && String(previousPostId) !== String(postId)) {
        stopAccruing()
        flushAll()
      }

      if (!enabled || !postId) {
        stopAccruing()
        flushAll()
        currentKey = null
        return
      }

      const state = resolveState(postId, initialLastReadCommentId)
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

  onBeforeUnmount(() => {
    stopAccruing()
    flushAll()
    document.removeEventListener('visibilitychange', handleActivityChange)
    window.removeEventListener('focus', handleFocus)
    window.removeEventListener('blur', handleBlur)
    if (flushTimer) {
      clearInterval(flushTimer)
      flushTimer = null
    }
  })

  return {
    setLastReadCommentId,
    flush: flushAll,
  }
}

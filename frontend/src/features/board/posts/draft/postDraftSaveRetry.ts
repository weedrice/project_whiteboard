import { ref } from 'vue'
import { getRetryAfterMs } from '@/api/retryAfter'

const SAVE_RETRY_BASE_DELAY_MS = 1_000
export const SAVE_RETRY_MAX_DELAY_MS = 30_000
export const SAVE_RETRY_MAX_ATTEMPTS = 5

interface DraftSaveRetryControllerOptions {
  canRetry: () => boolean
  retry: () => Promise<unknown>
  isOnline?: () => boolean
  resolveRetryAfterMs?: (error?: unknown) => number | null | undefined
  random?: () => number
  now?: () => number
  onRetryError?: (error: unknown) => void
  onExhausted?: (attempts: number) => void
}

export function getDraftSaveRetryDelay(
  attempt: number,
  random: () => number = Math.random,
): number {
  const exponentialDelay = Math.min(
    SAVE_RETRY_BASE_DELAY_MS * 2 ** Math.max(0, attempt - 1),
    SAVE_RETRY_MAX_DELAY_MS,
  )
  const jitter = 0.8 + random() * 0.4
  return Math.min(Math.round(exponentialDelay * jitter), SAVE_RETRY_MAX_DELAY_MS)
}

export function createDraftSaveRetryController({
  canRetry,
  retry,
  isOnline = () => typeof navigator === 'undefined' || navigator.onLine,
  resolveRetryAfterMs = getRetryAfterMs,
  random = Math.random,
  now = Date.now,
  onRetryError,
  onExhausted,
}: DraftSaveRetryControllerOptions) {
  const attempt = ref(0)
  const scheduled = ref(false)
  const exhausted = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let dueAt: number | null = null

  const clear = (resetAttempt = true) => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    dueAt = null
    scheduled.value = false
    if (resetAttempt) {
      attempt.value = 0
      exhausted.value = false
    }
  }

  const armTimer = () => {
    if (dueAt == null) return
    const remainingDelayMs = Math.max(dueAt - now(), 0)
    timer = setTimeout(() => {
      timer = null
      if (!isOnline()) {
        attempt.value = Math.max(0, attempt.value - 1)
        return
      }
      if (dueAt != null && dueAt > now()) {
        armTimer()
        return
      }
      scheduled.value = false
      dueAt = null
      void retry().catch((error: unknown) => {
        onRetryError?.(error)
      })
    }, Math.min(remainingDelayMs, SAVE_RETRY_MAX_DELAY_MS))
  }

  const schedule = (error?: unknown) => {
    if (timer
      || attempt.value >= SAVE_RETRY_MAX_ATTEMPTS
      || !canRetry()) {
      if (attempt.value >= SAVE_RETRY_MAX_ATTEMPTS && !exhausted.value) {
        exhausted.value = true
        onExhausted?.(attempt.value)
      }
      return
    }

    if (dueAt == null) {
      const retryAfterMs = resolveRetryAfterMs(error)
      const delayMs = retryAfterMs ?? getDraftSaveRetryDelay(attempt.value + 1, random)
      dueAt = now() + delayMs
    }
    scheduled.value = true
    if (!isOnline()) return

    attempt.value++
    armTimer()
  }

  const pauseForOffline = () => {
    if (!timer) return
    clearTimeout(timer)
    timer = null
    attempt.value = Math.max(0, attempt.value - 1)
    scheduled.value = true
  }

  return {
    attempt,
    scheduled,
    exhausted,
    clear,
    schedule,
    pauseForOffline,
  }
}

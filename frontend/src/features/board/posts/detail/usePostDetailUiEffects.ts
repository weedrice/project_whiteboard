import { ref } from 'vue'

export function usePostDetailUiEffects() {
  const isBlurred = ref(false)
  const timeLeft = ref(5)

  let blurTimer: ReturnType<typeof setInterval> | null = null
  let composerFocusTimer: ReturnType<typeof setTimeout> | null = null
  const imageLoadTimeouts = new Map<ReturnType<typeof setTimeout>, () => void>()
  let isDisposed = false

  function clearBlurTimer() {
    if (blurTimer) {
      clearInterval(blurTimer)
      blurTimer = null
    }
  }

  function startBlurTimer() {
    clearBlurTimer()
    blurTimer = setInterval(() => {
      timeLeft.value -= 1
      if (timeLeft.value <= 0) {
        revealSpoiler()
      }
    }, 1000)
  }

  function revealSpoiler() {
    isBlurred.value = false
    clearBlurTimer()
  }

  function markPostDetailUiMounted() {
    isDisposed = false
  }

  function isPostDetailUiDisposed() {
    return isDisposed
  }

  function clearComposerFocusTimer() {
    if (composerFocusTimer) {
      clearTimeout(composerFocusTimer)
      composerFocusTimer = null
    }
  }

  function clearImageLoadTimeoutTimers() {
    imageLoadTimeouts.forEach((resolve, timer) => {
      clearTimeout(timer)
      resolve()
    })
    imageLoadTimeouts.clear()
  }

  function scheduleComposerFocus(composer: HTMLElement) {
    clearComposerFocusTimer()
    composerFocusTimer = setTimeout(() => {
      composerFocusTimer = null
      if (isDisposed) return
      const textarea = composer.querySelector('textarea') as HTMLTextAreaElement | null
      textarea?.focus()
    }, 250)
  }

  function trackImageLoadTimeout(resolve: () => void, timeoutMs: number) {
    const resolveTimeout = () => {
      imageLoadTimeouts.delete(timer)
      resolve()
    }
    const timer = setTimeout(resolveTimeout, timeoutMs)
    imageLoadTimeouts.set(timer, resolveTimeout)
  }

  function disposePostDetailUiEffects() {
    isDisposed = true
    clearBlurTimer()
    clearComposerFocusTimer()
    clearImageLoadTimeoutTimers()
  }

  return {
    isBlurred,
    timeLeft,
    markPostDetailUiMounted,
    isPostDetailUiDisposed,
    startBlurTimer,
    clearBlurTimer,
    revealSpoiler,
    scheduleComposerFocus,
    trackImageLoadTimeout,
    disposePostDetailUiEffects
  }
}

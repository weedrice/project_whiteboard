import { ref } from 'vue'
import { isNarrowViewport } from '@/utils/browserEnv'

export function usePostDetailUiEffects() {
  const isBlurred = ref(false)
  const timeLeft = ref(5)
  const isLikeAnimating = ref(false)
  const isBookmarkAnimating = ref(false)
  const showCopyHint = ref(false)
  const showComposerCta = ref(false)

  let blurTimer: ReturnType<typeof setInterval> | null = null
  let likeAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let bookmarkAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let copyHintTimer: ReturnType<typeof setTimeout> | null = null
  let composerFocusTimer: ReturnType<typeof setTimeout> | null = null
  const imageLoadTimeouts = new Map<ReturnType<typeof setTimeout>, () => void>()
  let composerObserver: IntersectionObserver | null = null
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

  function triggerLikeAnimation() {
    isLikeAnimating.value = true
    if (likeAnimationTimer) clearTimeout(likeAnimationTimer)
    likeAnimationTimer = setTimeout(() => {
      isLikeAnimating.value = false
      likeAnimationTimer = null
    }, 300)
  }

  function triggerBookmarkAnimation() {
    isBookmarkAnimating.value = true
    if (bookmarkAnimationTimer) clearTimeout(bookmarkAnimationTimer)
    bookmarkAnimationTimer = setTimeout(() => {
      isBookmarkAnimating.value = false
      bookmarkAnimationTimer = null
    }, 300)
  }

  function showTemporaryCopyHint() {
    showCopyHint.value = true
    if (copyHintTimer) clearTimeout(copyHintTimer)
    copyHintTimer = setTimeout(() => {
      showCopyHint.value = false
      copyHintTimer = null
    }, 1500)
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

  function setupComposerObserver() {
    if (isDisposed) return

    if (composerObserver) {
      composerObserver.disconnect()
      composerObserver = null
    }

    if (!isNarrowViewport(640)) {
      showComposerCta.value = false
      return
    }

    const composer = document.getElementById('comment-composer')
    if (!composer) {
      showComposerCta.value = false
      return
    }

    composerObserver = new IntersectionObserver(([entry]) => {
      showComposerCta.value = !entry.isIntersecting
    }, {
      threshold: 0,
      rootMargin: '0px 0px -18% 0px'
    })

    composerObserver.observe(composer)
  }

  function disposePostDetailUiEffects() {
    isDisposed = true
    clearBlurTimer()
    clearComposerFocusTimer()
    clearImageLoadTimeoutTimers()
    if (composerObserver) {
      composerObserver.disconnect()
      composerObserver = null
    }
    if (likeAnimationTimer) {
      clearTimeout(likeAnimationTimer)
      likeAnimationTimer = null
    }
    if (bookmarkAnimationTimer) {
      clearTimeout(bookmarkAnimationTimer)
      bookmarkAnimationTimer = null
    }
    if (copyHintTimer) {
      clearTimeout(copyHintTimer)
      copyHintTimer = null
    }
  }

  return {
    isBlurred,
    timeLeft,
    isLikeAnimating,
    isBookmarkAnimating,
    showCopyHint,
    showComposerCta,
    markPostDetailUiMounted,
    isPostDetailUiDisposed,
    startBlurTimer,
    clearBlurTimer,
    revealSpoiler,
    triggerLikeAnimation,
    triggerBookmarkAnimation,
    showTemporaryCopyHint,
    scheduleComposerFocus,
    trackImageLoadTimeout,
    setupComposerObserver,
    disposePostDetailUiEffects
  }
}

import { ref } from 'vue'

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
  let composerObserver: IntersectionObserver | null = null

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

  function setupComposerObserver() {
    if (composerObserver) {
      composerObserver.disconnect()
      composerObserver = null
    }

    if (typeof window === 'undefined' || window.innerWidth >= 640) {
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
    clearBlurTimer()
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
    startBlurTimer,
    clearBlurTimer,
    revealSpoiler,
    triggerLikeAnimation,
    triggerBookmarkAnimation,
    showTemporaryCopyHint,
    setupComposerObserver,
    disposePostDetailUiEffects
  }
}

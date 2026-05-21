import { onUnmounted, ref } from 'vue'

export function useSpoilerReveal() {
  const isBlurred = ref(false)
  const timeLeft = ref(5)
  let blurTimer: ReturnType<typeof setInterval> | null = null

  function clearBlurTimer() {
    if (blurTimer) {
      clearInterval(blurTimer)
      blurTimer = null
    }
  }

  function revealSpoiler() {
    isBlurred.value = false
    clearBlurTimer()
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

  function syncSpoilerState(isSpoiler: boolean) {
    if (isSpoiler) {
      isBlurred.value = true
      timeLeft.value = 5
      startBlurTimer()
      return
    }

    isBlurred.value = false
    clearBlurTimer()
  }

  onUnmounted(clearBlurTimer)

  return {
    isBlurred,
    timeLeft,
    revealSpoiler,
    syncSpoilerState,
  }
}

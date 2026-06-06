interface ViewportListenerOptions {
  onStop?: () => void
}

export function useViewportListeners(
  onViewportChange: () => void,
  options: ViewportListenerOptions = {}
) {
  let isListening = false

  function startListening() {
    if (typeof window === 'undefined' || isListening) return
    window.addEventListener('resize', onViewportChange)
    window.addEventListener('scroll', onViewportChange, true)
    isListening = true
  }

  function stopListening() {
    if (typeof window === 'undefined' || !isListening) return
    window.removeEventListener('resize', onViewportChange)
    window.removeEventListener('scroll', onViewportChange, true)
    isListening = false
    options.onStop?.()
  }

  return {
    startListening,
    stopListening,
  }
}

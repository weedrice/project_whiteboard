import { useEventListener } from '@/composables/useEventListener'

interface ViewportListenerOptions {
  onStop?: () => void
}

export function useViewportListeners(
  onViewportChange: () => void,
  options: ViewportListenerOptions = {}
) {
  let isListening = false
  const windowTarget = () => typeof window === 'undefined' ? null : window

  const resizeListener = useEventListener(windowTarget, 'resize', onViewportChange, undefined, {
    autoStart: false,
  })
  const scrollListener = useEventListener(windowTarget, 'scroll', onViewportChange, true, {
    autoStart: false,
  })

  function startListening() {
    if (typeof window === 'undefined' || isListening) return
    resizeListener.start()
    scrollListener.start()
    isListening = true
  }

  function stopListening() {
    if (typeof window === 'undefined' || !isListening) return
    resizeListener.stop()
    scrollListener.stop()
    isListening = false
    options.onStop?.()
  }

  return {
    startListening,
    stopListening,
  }
}

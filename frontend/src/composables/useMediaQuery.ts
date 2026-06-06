import { onMounted, onUnmounted, ref } from 'vue'

export const mobileViewportMediaQuery = '(max-width: 639px)'
export const mobileViewportMaxWidth = 639

type MediaQueryChangeHandler = (matches: boolean) => void

function createMediaQuery(query: string) {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(query)
    : null
}

export function isMobileViewportFallback() {
  return typeof window !== 'undefined' && window.innerWidth <= mobileViewportMaxWidth
}

export function useMediaQuery(
  query: string,
  fallback = false,
  onChange?: MediaQueryChangeHandler
) {
  const mediaQuery = createMediaQuery(query)
  const matches = ref(mediaQuery?.matches ?? fallback)

  const updateMatches = (event?: MediaQueryListEvent) => {
    const nextMatches = event?.matches ?? mediaQuery?.matches ?? fallback
    matches.value = nextMatches
    onChange?.(nextMatches)
  }

  onMounted(() => {
    updateMatches()
    mediaQuery?.addEventListener('change', updateMatches)
  })

  onUnmounted(() => {
    mediaQuery?.removeEventListener('change', updateMatches)
  })

  return matches
}

export function useMobileViewport(onChange?: MediaQueryChangeHandler) {
  return useMediaQuery(mobileViewportMediaQuery, isMobileViewportFallback(), onChange)
}

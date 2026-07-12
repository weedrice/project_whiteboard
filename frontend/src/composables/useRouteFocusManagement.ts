import { nextTick, ref, watch, type Ref } from 'vue'

interface RouteFocusSource {
  path: string
  hash: string
}

const focusRouteTarget = (target: HTMLElement) => {
  const hadTabindex = target.hasAttribute('tabindex')
  if (!hadTabindex) target.setAttribute('tabindex', '-1')
  target.focus({ preventScroll: true })
  if (!hadTabindex) {
    target.addEventListener('blur', () => target.removeAttribute('tabindex'), { once: true })
  }
}

const getHashTarget = (hash: string): HTMLElement | null => {
  if (!hash) return null

  try {
    return document.getElementById(decodeURIComponent(hash.slice(1)))
  } catch {
    return document.getElementById(hash.slice(1))
  }
}

export function useRouteFocusManagement(route: RouteFocusSource): { routeAnnouncement: Ref<string> } {
  const routeAnnouncement = ref('')

  watch(
    () => [route.path, route.hash] as const,
    async ([, hash], previous) => {
      if (!previous) return
      await nextTick()

      const hashTarget = getHashTarget(hash)
      const heading = document.querySelector<HTMLElement>('h1')
      const target = hashTarget ?? heading

      if (target) focusRouteTarget(target)
      routeAnnouncement.value = heading?.textContent?.trim() || document.title
    },
  )

  return { routeAnnouncement }
}

export interface StandaloneNavigator extends Navigator {
  standalone?: boolean
}

export function isStandaloneDisplayMode(
  targetWindow: Pick<Window, 'matchMedia'> = window,
  targetNavigator: StandaloneNavigator = navigator as StandaloneNavigator,
): boolean {
  return targetWindow.matchMedia('(display-mode: standalone)').matches
    || targetNavigator.standalone === true
}

export function applyStandaloneDisplayModeClass(): () => void {
  const media = window.matchMedia('(display-mode: standalone)')
  const syncClass = () => {
    document.documentElement.classList.toggle(
      'nv-standalone',
      media.matches || (navigator as StandaloneNavigator).standalone === true,
    )
  }

  syncClass()
  media.addEventListener?.('change', syncClass)
  return () => media.removeEventListener?.('change', syncClass)
}

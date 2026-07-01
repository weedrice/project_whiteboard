export function findPostDetailElementByHash(hash: string): HTMLElement | null {
  if (!hash.startsWith('#')) return null

  const rawId = hash.slice(1)
  if (!rawId) return null

  try {
    return document.getElementById(decodeURIComponent(rawId))
  } catch {
    return document.getElementById(rawId)
  }
}

export function getPostDetailScrollTop(target: HTMLElement, headerOffset = 96): number {
  return target.getBoundingClientRect().top + window.scrollY - headerOffset
}

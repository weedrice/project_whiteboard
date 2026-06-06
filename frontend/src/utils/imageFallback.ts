export const DEFAULT_EMOTICON_IMAGE_URL = '/images/default-emoticon.png'

export function setImageFallback(event: Event, fallbackUrl: string): void {
  const image = event.target as HTMLImageElement | null

  if (!image || image.tagName !== 'IMG' || image.dataset.fallbackApplied === 'true') {
    return
  }

  image.dataset.fallbackApplied = 'true'
  image.removeAttribute('srcset')
  image.src = fallbackUrl
}

export function applyImageFallback(event: Event, fallbackUrl = DEFAULT_EMOTICON_IMAGE_URL): void {
  setImageFallback(event, fallbackUrl)
}

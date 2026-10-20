const transparentImage = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='90'/%3E\")"
// Keep these SVG colors aligned with the light/dark --nv-ink-soft tokens.
const errorColors = { light: '#3a3a3a', dark: '#c8c7c2' }
function errorIcon(color: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="none" stroke="${color}" stroke-width="1.5" d="M5 3h14v18H5zM8 15l3-3 5 5M15 7v4m0 2v1"/></svg>`
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`
}
const errorImage = errorIcon(errorColors.light)
const darkErrorImage = errorIcon(errorColors.dark)

// Shared by Vue images, sanitized HTML and the isolated HTML iframe.
export const IMAGE_STATE_CSS = `
img[data-image-state="loading"], img[data-image-state="error"] {
  content: var(--nv-image-placeholder, ${transparentImage}) !important;
  color: transparent !important;
  background-color: var(--nv-surface-muted, var(--surface-0, #e5e7eb));
}
img[data-image-state="loading"] {
  background-image: linear-gradient(100deg, transparent 20%, color-mix(in srgb, var(--nv-ink-soft, #888) 12%, transparent) 50%, transparent 80%);
  background-size: 200% 100%;
  animation: nv-image-loading 1.5s ease-in-out infinite;
  cursor: progress;
}
img[data-image-state="loading"][data-image-offscreen] {
  animation-play-state: paused;
}
img[data-image-state="error"] {
  background-image: var(--nv-image-error-symbol, ${errorImage});
  background-position: center;
  background-repeat: no-repeat;
  background-size: var(--nv-image-error-size, min(60%, 36px));
  cursor: default;
}
.dark img[data-image-state="error"] {
  background-image: var(--nv-image-error-symbol-dark, ${darkErrorImage});
}
@media (prefers-color-scheme: dark) {
  :root:not([data-nv-image-theme="manual"]) img[data-image-state="error"] {
    background-image: var(--nv-image-error-symbol-dark, ${darkErrorImage});
  }
}
@keyframes nv-image-loading {
  from { background-position: 100% 0; }
  to { background-position: -100% 0; }
}
@media (prefers-reduced-motion: reduce) {
  img[data-image-state="loading"] { animation: none; }
}
`

export function installImageStateStyles(document: Document): void {
  if (document.querySelector('style[data-nv-image-states]')) return
  // App themes follow the root class; isolated HTML follows its system media query.
  document.documentElement.setAttribute('data-nv-image-theme', 'manual')
  const style = document.createElement('style')
  style.setAttribute('data-nv-image-states', '')
  style.textContent = IMAGE_STATE_CSS
  document.head.append(style)
}

interface ImageVisibilityObserver {
  observer: IntersectionObserver
  images: Set<HTMLImageElement>
}

const imageVisibilityObservers = new WeakMap<Document, ImageVisibilityObserver>()

export function observeLoadingImage(image: HTMLImageElement): () => void {
  const document = image.ownerDocument
  const view = document.defaultView
  if (!view?.IntersectionObserver) return () => undefined
  let shared = imageVisibilityObservers.get(document)
  if (!shared) {
    const images = new Set<HTMLImageElement>()
    const observer = new view.IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        const target = entry.target as HTMLImageElement
        if (!images.has(target) || target.dataset.imageState !== 'loading') return
        if (entry.isIntersecting) target.removeAttribute('data-image-offscreen')
        else target.setAttribute('data-image-offscreen', '')
      })
    }, { rootMargin: '200px 0px' })
    shared = { observer, images }
    imageVisibilityObservers.set(document, shared)
  }
  const registration = shared
  registration.images.add(image)
  // Start paused, including detached beforeMount nodes, until visibility is known.
  image.setAttribute('data-image-offscreen', '')
  registration.observer.observe(image)
  return () => {
    registration.images.delete(image)
    registration.observer.unobserve(image)
    image.removeAttribute('data-image-offscreen')
    if (registration.images.size === 0) {
      registration.observer.disconnect()
      if (imageVisibilityObservers.get(document) === registration) imageVisibilityObservers.delete(document)
    }
  }
}

export function prepareImagePresentation(image: HTMLImageElement): void {
  image.dataset.imageState = 'loading'
  image.setAttribute('aria-busy', 'true')
  const width = Number(image.getAttribute('width'))
  const height = Number(image.getAttribute('height'))
  if (width > 0 && height > 0 && Number.isFinite(width) && Number.isFinite(height)) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"/>`
    image.style.setProperty('--nv-image-placeholder', `url("data:image/svg+xml,${encodeURIComponent(svg)}")`)
  } else {
    image.style.removeProperty('--nv-image-placeholder')
  }
}

interface PendingImageError {
  image: HTMLImageElement
  label: string
  source: string
  active: boolean
  showLabel?: boolean
}

interface ImageErrorBatch {
  pending: Map<HTMLImageElement, PendingImageError>
  observed: Map<HTMLImageElement, PendingImageError>
  observer: ResizeObserver | null
  frame: number | null
}

const imageErrorBatches = new WeakMap<Document, ImageErrorBatch>()

function imageErrorSource(image: HTMLImageElement): string {
  return ['src', 'srcset', 'sizes'].map((name) => image.getAttribute(name) ?? '').join('\n')
}

function applyImageError(image: HTMLImageElement, label: string, showLabel: boolean): void {
  if (!showLabel) {
    image.style.removeProperty('--nv-image-error-symbol')
    image.style.removeProperty('--nv-image-error-symbol-dark')
    image.style.removeProperty('--nv-image-error-size')
    return
  }
  const text = label.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  for (const [suffix, color] of [['', errorColors.light], ['-dark', errorColors.dark]]) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 64"><path fill="none" stroke="${color}" stroke-width="1.5" d="M133 4h14v24h-14zM136 22l3-3 5 5M143 8v5m0 2v1"/><text x="140" y="54" text-anchor="middle" fill="${color}" font-family="sans-serif" font-size="14">${text}</text></svg>`
    image.style.setProperty(`--nv-image-error-symbol${suffix}`, `url("data:image/svg+xml,${encodeURIComponent(svg)}")`)
  }
  image.style.setProperty('--nv-image-error-size', 'min(90%, 280px) auto')
}

function scheduleImageErrors(document: Document, queue: ImageErrorBatch): void {
  const view = document.defaultView
  if (!view || queue.frame !== null || queue.pending.size === 0) return
  queue.frame = view.requestAnimationFrame(() => {
    queue.frame = null
    const requests = Array.from(queue.pending.values())
    queue.pending.clear()
    const isCurrent = (entry: PendingImageError) => entry.active
      && entry.image.isConnected && entry.image.ownerDocument === document
      && entry.image.dataset.imageState === 'error'
      && imageErrorSource(entry.image) === entry.source
    // Finish every geometry read before changing styles on any image.
    const measured = requests.filter(isCurrent).map((entry) => ({
      entry,
      showLabel: entry.image.clientWidth >= 180 && entry.image.clientHeight >= 80,
    }))
    measured.forEach(({ entry, showLabel }) => {
      if (isCurrent(entry) && entry.showLabel !== showLabel) {
        entry.showLabel = showLabel
        applyImageError(entry.image, entry.label, showLabel)
      }
    })
  })
}

export function prepareImageError(image: HTMLImageElement, label: string): () => void {
  image.style.removeProperty('--nv-image-error-symbol')
  image.style.removeProperty('--nv-image-error-symbol-dark')
  image.style.removeProperty('--nv-image-error-size')
  const document = image.ownerDocument
  const view = document.defaultView
  if (!label || !view) return () => undefined

  let batch = imageErrorBatches.get(document)
  if (!batch) {
    batch = { pending: new Map(), observed: new Map(), observer: null, frame: null }
    imageErrorBatches.set(document, batch)
  }
  const queue = batch
  const request: PendingImageError = { image, label, source: imageErrorSource(image), active: true }
  queue.pending.set(image, request)
  if (view.ResizeObserver) {
    if (!queue.observer) {
      const observer = new view.ResizeObserver((entries) => {
        if (queue.observer !== observer) return
        entries.forEach(({ target }) => {
          const current = queue.observed.get(target as HTMLImageElement)
          if (current?.active) queue.pending.set(current.image, current)
        })
        scheduleImageErrors(document, queue)
      })
      queue.observer = observer
    }
    queue.observed.set(image, request)
    queue.observer.observe(image)
  }
  scheduleImageErrors(document, queue)

  return () => {
    request.active = false
    if (queue.pending.get(image) === request) queue.pending.delete(image)
    if (queue.observed.get(image) === request) {
      queue.observed.delete(image)
      queue.observer?.unobserve(image)
      if (queue.observed.size === 0) {
        queue.observer?.disconnect()
        queue.observer = null
      }
    }
    if (queue.pending.size === 0 && queue.frame !== null) {
      view.cancelAnimationFrame(queue.frame)
      queue.frame = null
    }
  }
}

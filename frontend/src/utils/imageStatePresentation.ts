const transparentImage = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='90'/%3E\")"
const errorImage = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='none' stroke='%23888' stroke-width='1.5' d='M5 3h14v18H5zM8 15l3-3 5 5M15 7v4m0 2v1'/%3E%3C/svg%3E\")"

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
img[data-image-state="error"] {
  background-image: var(--nv-image-error-symbol, ${errorImage});
  background-position: center;
  background-repeat: no-repeat;
  background-size: var(--nv-image-error-size, min(60%, 36px));
  cursor: default;
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
  const style = document.createElement('style')
  style.setAttribute('data-nv-image-states', '')
  style.textContent = IMAGE_STATE_CSS
  document.head.append(style)
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
}

interface ImageErrorBatch {
  pending: Map<HTMLImageElement, PendingImageError>
  frame: number | null
}

const imageErrorBatches = new WeakMap<Document, ImageErrorBatch>()

function imageErrorSource(image: HTMLImageElement): string {
  return ['src', 'srcset', 'sizes'].map((name) => image.getAttribute(name) ?? '').join('\n')
}

function applyImageError(image: HTMLImageElement, label: string, showLabel: boolean): void {
  if (!showLabel) return
  const text = label.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 64"><path fill="none" stroke="#888" stroke-width="1.5" d="M133 4h14v24h-14zM136 22l3-3 5 5M143 8v5m0 2v1"/><text x="140" y="54" text-anchor="middle" fill="#888" font-family="sans-serif" font-size="14">${text}</text></svg>`
  image.style.setProperty('--nv-image-error-symbol', `url("data:image/svg+xml,${encodeURIComponent(svg)}")`)
  image.style.setProperty('--nv-image-error-size', 'min(90%, 280px) auto')
}

export function prepareImageError(image: HTMLImageElement, label: string): () => void {
  image.style.removeProperty('--nv-image-error-symbol')
  image.style.removeProperty('--nv-image-error-size')
  const document = image.ownerDocument
  const view = document.defaultView
  if (!label || !view) return () => undefined

  let batch = imageErrorBatches.get(document)
  if (!batch) {
    batch = { pending: new Map(), frame: null }
    imageErrorBatches.set(document, batch)
  }
  const queue = batch
  const request = { image, label, source: imageErrorSource(image), active: true }
  queue.pending.set(image, request)
  if (queue.frame === null) {
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
        if (isCurrent(entry)) applyImageError(entry.image, entry.label, showLabel)
      })
    })
  }

  return () => {
    request.active = false
    if (queue.pending.get(image) === request) queue.pending.delete(image)
    if (queue.pending.size === 0 && queue.frame !== null) {
      view.cancelAnimationFrame(queue.frame)
      queue.frame = null
    }
  }
}

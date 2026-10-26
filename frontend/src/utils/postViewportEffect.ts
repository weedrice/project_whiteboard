import { expandSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { getViewportHtmlValidationReason, getViewportImageElements, MAX_VIEWPORT_IMAGE_DATA_LENGTH, type ViewportHtmlValidationReason } from '@/utils/postViewportHtml'

export const VIEWPORT_EFFECT_DURATION = 30_000
export const MAX_VIEWPORT_IMAGES = 8
export const MAX_VIEWPORT_IMAGE_BYTES = 8 * 1024 * 1024

type ViewportImageValidationReason = 'tooManyImages' | 'imageSource' | 'imageType' | 'imageBytes' | 'imageDataTooLarge'
export type ViewportValidationReason = ViewportHtmlValidationReason | ViewportImageValidationReason | 'duplicateLayers'

export class ViewportImageValidationError extends Error {
  constructor(readonly reason: ViewportImageValidationReason) {
    super(reason)
  }
  override name = 'ViewportImageValidationError'
}

/** The marker is an inert authoring boundary, never host DOM content. */
export function extractViewportHtml(content: string): { html: string | null; invalid: boolean; reason?: ViewportValidationReason } {
  if (!content || typeof document === 'undefined') return { html: null, invalid: false }
  const source = expandSandboxedPostHtml(content) ?? content
  const template = document.createElement('template')
  template.innerHTML = source
  const layers = template.content.querySelectorAll('template[data-noviis-layer="viewport"]')
  if (!layers.length) return { html: null, invalid: false }
  const layer = layers[0]
  if (layers.length !== 1) return { html: null, invalid: true, reason: 'duplicateLayers' }
  if (!(layer instanceof HTMLTemplateElement)) return { html: null, invalid: true }
  const html = layer.innerHTML
  const reason = getViewportHtmlValidationReason(html, layer.content)
  if (reason) return { html: null, invalid: true, reason }
  try {
    validateViewportImages(getViewportImageElements(layer.content))
  } catch (error) {
    if (error instanceof ViewportImageValidationError) return { html: null, invalid: true, reason: error.reason }
    throw error
  }
  return { html, invalid: false }
}

/** Use the original uploaded file, preserving animated GIF/WebP frames. */
export async function hydrateViewportImages(
  html: string,
  download: (path: string) => Promise<Blob>,
  isCurrent: () => boolean,
): Promise<{ html: string; imageSources: Map<string, string> }> {
  const template = document.createElement('template')
  template.innerHTML = html
  const images = getViewportImageElements(template.content)
  const paths = validateViewportImages(images)
  const occurrences = new Map<string, number>()
  paths.forEach(path => occurrences.set(path, (occurrences.get(path) ?? 0) + 1))
  const resources = new Map<string, string>()
  const imageSources = new Map<string, string>()
  let bytes = 0
  let renderedDataLength = 0
  for (const [index, image] of images.entries()) {
    if (!isCurrent()) throw new Error('Stale viewport effect')
    const path = paths[index]!
    let imageKey = resources.get(path)
    if (!imageKey) {
      const blob = await download(path)
      if (!isCurrent()) throw new Error('Stale viewport effect')
      bytes += blob.size
      if (bytes > MAX_VIEWPORT_IMAGE_BYTES) throw new ViewportImageValidationError('imageBytes')
      if (!['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(blob.type.toLowerCase())) {
        throw new ViewportImageValidationError('imageType')
      }
      // Count each rendered occurrence before encoding, while deduplicating downloads.
      const dataLength = `data:${blob.type};base64,`.length + 4 * Math.ceil(blob.size / 3)
      renderedDataLength += dataLength * occurrences.get(path)!
      if (renderedDataLength > MAX_VIEWPORT_IMAGE_DATA_LENGTH) {
        throw new ViewportImageValidationError('imageDataTooLarge')
      }
      const dataUrl = await readRasterDataUrl(blob)
      if (!isCurrent()) throw new Error('Stale viewport effect')
      imageKey = `noviis-viewport-image:${resources.size}`
      resources.set(path, imageKey)
      imageSources.set(imageKey, dataUrl)
    }
    image.setAttribute('src', imageKey)
    image.removeAttribute('srcset')
    image.removeAttribute('data-server-src')
  }
  return { html: template.innerHTML, imageSources }
}

function validateViewportImages(images: HTMLImageElement[]): string[] {
  if (images.length > MAX_VIEWPORT_IMAGES) throw new ViewportImageValidationError('tooManyImages')
  return images.map(viewportImagePath)
}

function viewportImagePath(image: HTMLImageElement): string {
  const source = image.getAttribute('data-server-src') ?? image.getAttribute('src') ?? ''
  let url: URL
  try { url = new URL(source, window.location.origin) } catch {
    throw new ViewportImageValidationError('imageSource')
  }
  const id = url.pathname.match(/^\/(?:api\/v1\/)?files\/([1-9]\d*)$/)?.[1]
  const declaredId = image.getAttribute('data-file-id')
  if (url.origin !== window.location.origin || url.search || url.hash || !id
    || !Number.isSafeInteger(Number(id)) || (declaredId !== null && declaredId !== id)) {
    throw new ViewportImageValidationError('imageSource')
  }
  return `/files/${id}`
}

function readRasterDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error ?? new Error('Image read failed'))
    reader.readAsDataURL(blob)
  })
}

import { computed, onBeforeUnmount, ref, unref, type MaybeRefOrGetter } from 'vue'
import type { Router } from 'vue-router'
import { useEventListener } from '@/composables/useEventListener'
import {
  AUTHENTICATED_IMAGE_CHANGE_EVENT,
  AUTHENTICATED_IMAGES_RESET_EVENT,
  getAuthenticatedImageResource,
} from '@/utils/authenticatedImageLoader'

interface CodeCopyLabels {
  copy: string
  copied: string
  failed: string
}

interface UseRichContentInteractionsOptions {
  isDisabled: MaybeRefOrGetter<boolean>
  router: Router
  getCodeCopyLabels: () => CodeCopyLabels
  getImageOpenLabel: (alt: string, index: number) => string
}

export interface RichContentLightboxImage {
  id?: string
  src: string
  alt: string
  width?: number
  height?: number
  pending?: boolean
  failed?: boolean
  requestLoad?: (retry?: boolean) => void
  onLoaded?: () => void
}

function imageDimensions(image: HTMLImageElement): { width?: number; height?: number } {
  const hasNaturalSize = image.naturalWidth > 0 && image.naturalHeight > 0
  const width = hasNaturalSize ? image.naturalWidth : Number(image.getAttribute('width'))
  const height = hasNaturalSize ? image.naturalHeight : Number(image.getAttribute('height'))
  return Number.isFinite(width) && width > 0 && Number.isFinite(height) && height > 0
    ? { width, height }
    : {}
}

function resolveBoolean(value: MaybeRefOrGetter<boolean>): boolean {
  if (typeof value === 'function') {
    return Boolean((value as () => boolean)())
  }
  return Boolean(unref(value))
}

export function useRichContentInteractions(options: UseRichContentInteractionsOptions) {
  const articleRef = ref<HTMLElement | null>(null)
  const lightboxOpen = ref(false)
  const lightboxIndex = ref(0)
  const openedImages = ref<RichContentLightboxImage[]>([])
  const lightboxImages = computed(() => !articleRef.value || resolveBoolean(options.isDisabled) ? [] : openedImages.value)
  const pendingImages = new Set<HTMLImageElement>()
  const interactionLabels = new WeakMap<HTMLImageElement, string>()
  const loadCallbacks = new WeakMap<HTMLImageElement, { id: string; requestLoad: (retry?: boolean) => void }>()
  const nativeRecoveryCallbacks = new WeakMap<HTMLImageElement, { signature: string; src: string; generation: number; onLoaded: () => void }>()
  let galleryGeneration = 0
  let openedElements: HTMLImageElement[] = []
  let imageFrame: number | null = null

  function isLinkedImage(image: HTMLImageElement) {
    return Boolean(image.closest('a[href], a[data-authenticated-file-href]'))
  }

  function canOpenImage(image: HTMLImageElement) {
    if (image.dataset.imageState && !['loaded', 'error'].includes(image.dataset.imageState)) return false
    return Boolean(getAuthenticatedImageResource(image) || image.currentSrc || image.getAttribute('src')?.trim())
  }

  function prepareImageForInteraction(image: HTMLImageElement, index: number) {
    if (isLinkedImage(image) || !canOpenImage(image)) {
      if (isLinkedImage(image)) image.removeAttribute('tabindex')
      else image.tabIndex = -1
      image.removeAttribute('role')
      image.removeAttribute('aria-haspopup')
      if (isLinkedImage(image) && image.getAttribute('aria-label') === interactionLabels.get(image)) {
        image.removeAttribute('aria-label')
      }
      return
    }
    if (image.tabIndex === -1) image.removeAttribute('tabindex')
    if (!image.hasAttribute('tabindex')) image.tabIndex = 0
    if (!image.hasAttribute('role')) image.setAttribute('role', 'button')
    image.setAttribute('aria-haspopup', 'dialog')
    // Keep the original description and failure announcement supplied by the image controller.
    if (image.dataset.imageState === 'error' && image.hasAttribute('aria-label')) return
    const label = options.getImageOpenLabel(image.alt.trim(), index)
    interactionLabels.set(image, label)
    image.setAttribute('aria-label', label)
  }

  function closeGallery() {
    galleryGeneration += 1
    lightboxOpen.value = false
    if (openedImages.value.length > 0) openedImages.value = []
    openedElements = []
  }

  function nativeSourceSignature(image: HTMLImageElement) {
    return JSON.stringify(['src', 'srcset', 'sizes'].map(name => image.getAttribute(name)))
  }

  function nativeRecovery(image: HTMLImageElement, src: string) {
    const signature = nativeSourceSignature(image)
    const generation = galleryGeneration
    const cached = nativeRecoveryCallbacks.get(image)
    if (cached?.signature === signature && cached.src === src && cached.generation === generation) return cached.onLoaded
    const onLoaded = () => {
      if (!lightboxOpen.value || generation !== galleryGeneration || resolveBoolean(options.isDisabled)
        || !articleRef.value?.contains(image) || !openedElements.includes(image)
        || getAuthenticatedImageResource(image) || nativeSourceSignature(image) !== signature
        || (image.currentSrc || image.src) !== src || image.dataset.imageState !== 'error') return
      // Retry the actual original element without changing signed URLs or asserting a synthetic load.
      const attribute = image.getAttribute('srcset')?.trim() ? 'srcset' : 'src'
      const value = image.getAttribute(attribute)
      if (value) {
        image.removeAttribute(attribute)
        image.setAttribute(attribute, value)
      }
    }
    nativeRecoveryCallbacks.set(image, { signature, src, generation, onLoaded })
    return onLoaded
  }

  function galleryCandidates(images: HTMLImageElement[]) {
    return images.flatMap(element => {
      if (isLinkedImage(element)) return []
      const resource = getAuthenticatedImageResource(element)
      const nativeSrc = element.getAttribute('src')?.trim() ? element.src : ''
      const src = resource
        ? resource.src || (resource.failed ? nativeSrc : '')
        : element.currentSrc || nativeSrc
      if (!resource && !src) return []
      const image: RichContentLightboxImage = { src, alt: element.alt.trim(), ...imageDimensions(element) }
      if (!resource && element.dataset.imageState === 'error') image.onLoaded = nativeRecovery(element, src)
      if (resource) {
        const cached = loadCallbacks.get(element)
        let requestLoad = cached?.id === resource.id ? cached.requestLoad : undefined
        if (!requestLoad) {
          requestLoad = (retry = false) => {
            // A callback retained by an old gallery must never load a new owner's image.
            if (getAuthenticatedImageResource(element) !== resource || !articleRef.value?.contains(element)) return
            resource.requestLoad(retry)
            refreshOpenedImages()
          }
          loadCallbacks.set(element, { id: resource.id, requestLoad })
        }
        Object.assign(image, {
          id: resource.id,
          pending: resource.pending,
          failed: (resource.failed && !src) || (!resource.pending && element.dataset.imageState === 'error'),
          requestLoad,
        })
      }
      return [{ element, image }]
    })
  }

  function refreshOpenedImages(images?: HTMLImageElement[]) {
    if (!lightboxOpen.value) return
    if (!articleRef.value || resolveBoolean(options.isDisabled)) { closeGallery(); return }
    const selected = openedElements[lightboxIndex.value]
    const candidates = galleryCandidates(images ?? Array.from(articleRef.value.querySelectorAll<HTMLImageElement>('.nv-rich-content img')))
    const index = candidates.findIndex(candidate => candidate.element === selected)
    if (index < 0) { closeGallery(); return }
    const next = candidates.map(candidate => candidate.image)
    const changed = next.length !== openedImages.value.length || next.some((image, i) => {
      const previous = openedImages.value[i]
      return !previous || (Object.keys(image) as Array<keyof RichContentLightboxImage>)
        .some(key => image[key] !== previous[key])
        || Object.keys(image).length !== Object.keys(previous).length
    })
    openedElements = candidates.map(candidate => candidate.element)
    if (changed) openedImages.value = next
    if (lightboxIndex.value !== index) lightboxIndex.value = index
  }

  function cancelImagePreparation() {
    if (imageFrame !== null) window.cancelAnimationFrame(imageFrame)
    imageFrame = null
    pendingImages.clear()
  }

  function prepareImagesForInteraction() {
    cancelImagePreparation()
    if (!articleRef.value || resolveBoolean(options.isDisabled)) { closeGallery(); return }
    const images = Array.from(articleRef.value.querySelectorAll<HTMLImageElement>('.nv-rich-content img'))
    images.forEach(prepareImageForInteraction)
    refreshOpenedImages(images)
  }

  function scheduleImagePreparation(event: Event) {
    const image = event.target
    if (!(image instanceof HTMLImageElement) || !image.matches('.nv-rich-content img')
      || !articleRef.value?.contains(image) || resolveBoolean(options.isDisabled)) return
    pendingImages.add(image)
    if (imageFrame !== null) return
    imageFrame = window.requestAnimationFrame(() => {
      imageFrame = null
      const images = new Set(pendingImages)
      pendingImages.clear()
      if (!articleRef.value || resolveBoolean(options.isDisabled)) return
      // Enumerate once per frame to retain label indices after content changes.
      const candidates = Array.from(articleRef.value.querySelectorAll<HTMLImageElement>('.nv-rich-content img'))
      candidates.forEach((candidate, index) => {
        if (images.has(candidate)) prepareImageForInteraction(candidate, index)
      })
      refreshOpenedImages(candidates)
    })
  }

  useEventListener(articleRef, 'imagestatechange', scheduleImagePreparation)
  useEventListener(articleRef, AUTHENTICATED_IMAGE_CHANGE_EVENT, scheduleImagePreparation)
  useEventListener(articleRef, AUTHENTICATED_IMAGES_RESET_EVENT, closeGallery)
  onBeforeUnmount(cancelImagePreparation)

  function navigateToMention(target: EventTarget | null): boolean {
    const mention = (target as HTMLElement | null)?.closest?.('[data-mention-user-id]')
    if (!(mention instanceof HTMLElement)) return false

    const userId = mention.dataset.mentionUserId
    if (!userId) return false

    void options.router.push(`/user/${encodeURIComponent(userId)}`)
    return true
  }

  function copyCodeBlock(target: EventTarget | null): boolean {
    const button = (target as HTMLElement | null)?.closest?.('[data-code-copy-button]')
    if (!(button instanceof HTMLButtonElement)) return false

    const code = button.closest('.nv-code-block')?.querySelector('pre code')
    const codeText = code?.textContent ?? ''
    if (!codeText) return true

    const labels = options.getCodeCopyLabels()
    if (!navigator.clipboard?.writeText) {
      button.textContent = labels.failed
      return true
    }

    navigator.clipboard.writeText(codeText)
      .then(() => {
        const originalText = button.textContent || labels.copy
        button.textContent = labels.copied
        window.setTimeout(() => {
          button.textContent = originalText
        }, 1400)
      })
      .catch(() => {
        button.textContent = labels.failed
      })
    return true
  }

  function openImageLightbox(target: EventTarget | null): boolean {
    const image = (target as HTMLElement | null)?.closest?.('img')
    if (!(image instanceof HTMLImageElement)) return false
    if (isLinkedImage(image)) return false
    if (!canOpenImage(image)) return false

    galleryGeneration += 1
    const images = galleryCandidates(Array.from(articleRef.value?.querySelectorAll<HTMLImageElement>('.nv-rich-content img') ?? []))
    const index = images.findIndex((candidate) => candidate.element === image)
    if (index < 0) return false

    openedElements = images.map(candidate => candidate.element)
    openedImages.value = images.map(candidate => candidate.image)
    image.focus({ preventScroll: true })
    lightboxIndex.value = index
    lightboxOpen.value = true
    return true
  }

  function handleContentClick(event: MouseEvent) {
    if (resolveBoolean(options.isDisabled)) return
    if (navigateToMention(event.target)) return
    if (copyCodeBlock(event.target)) return
    openImageLightbox(event.target)
  }

  function handleContentKeydown(event: KeyboardEvent) {
    if (resolveBoolean(options.isDisabled)) return
    if (event.key === 'Enter' && navigateToMention(event.target)) {
      event.preventDefault()
      return
    }
    if ((event.key === 'Enter' || event.key === ' ') && openImageLightbox(event.target)) {
      event.preventDefault()
    }
  }

  return {
    articleRef,
    lightboxOpen,
    lightboxIndex,
    lightboxImages,
    prepareImagesForInteraction,
    handleContentClick,
    handleContentKeydown,
  }
}

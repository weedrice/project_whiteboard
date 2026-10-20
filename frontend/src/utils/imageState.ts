import { installImageStateStyles, observeLoadingImage, prepareImageError, prepareImagePresentation } from '@/utils/imageStatePresentation'

export interface ImageStateOptions {
  pending?: boolean
  failed?: boolean
}

export function createImageStateController(image: HTMLImageElement, errorLabel: () => string) {
  installImageStateStyles(image.ownerDocument)
  let options: ImageStateOptions = {}
  let source = ''
  let disposed = false
  let originalLabel = image.getAttribute('aria-label')
  let originalTitle = image.getAttribute('title')
  let errorLabelApplied = false
  let cancelErrorPresentation: (() => void) | null = null
  let stopLoadingObservation: (() => void) | null = null

  function signature() {
    return ['src', 'srcset', 'sizes'].map((name) => image.getAttribute(name) ?? '').join('\n')
  }

  function restoreLabels() {
    if (errorLabelApplied) {
      if (originalLabel === null) image.removeAttribute('aria-label')
      else image.setAttribute('aria-label', originalLabel)
      if (originalTitle === null) image.removeAttribute('title')
      else image.setAttribute('title', originalTitle)
      errorLabelApplied = false
    }
  }

  function setState(state: 'loading' | 'loaded' | 'error') {
    if (disposed) return
    cancelErrorPresentation?.()
    cancelErrorPresentation = null
    const changed = image.dataset.imageState !== state
    if (state !== 'error') restoreLabels()
    image.dataset.imageState = state
    if (state === 'loading') stopLoadingObservation ??= observeLoadingImage(image)
    else {
      stopLoadingObservation?.()
      stopLoadingObservation = null
    }
    if (state === 'loading') image.setAttribute('aria-busy', 'true')
    else image.removeAttribute('aria-busy')
    if (state === 'error') {
      if (!errorLabelApplied) {
        originalLabel = image.getAttribute('aria-label')
        originalTitle = image.getAttribute('title')
      }
      const label = errorLabel()
      cancelErrorPresentation = prepareImageError(image, label)
      image.setAttribute('aria-label', [image.alt, label].filter(Boolean).join(' — '))
      image.setAttribute('title', label)
      errorLabelApplied = true
    }
    if (changed) image.dispatchEvent(new Event('imagestatechange', { bubbles: true }))
  }

  function refresh(nextOptions = options) {
    options = nextOptions
    source = signature()
    const previousState = image.dataset.imageState
    prepareImagePresentation(image)
    if (previousState) image.dataset.imageState = previousState
    if (options.failed) setState('error')
    else if (options.pending) setState('loading')
    else if (!image.getAttribute('src') && !image.getAttribute('srcset')) {
      setState(image.hasAttribute('data-authenticated-file-src') ? 'loading' : 'error')
    }
    else if (image.complete) setState(image.naturalWidth > 0 ? 'loaded' : 'error')
    else setState('loading')
  }

  function onLoad() {
    if (signature() !== source) refresh()
    else if (!options.failed && !options.pending && image.complete && image.naturalWidth > 0) setState('loaded')
  }

  function onError() {
    // Existing avatar/icon fallback handlers can replace src in the same event.
    const failedSource = source
    queueMicrotask(() => {
      if (disposed) return
      if (signature() !== failedSource) refresh()
      else if (!options.pending) setState('error')
    })
  }

  image.addEventListener('load', onLoad)
  image.addEventListener('error', onError)
  const observer = new MutationObserver(() => refresh())
  observer.observe(image, { attributes: true, attributeFilter: ['src', 'srcset', 'sizes', 'width', 'height'] })
  refresh()

  return {
    refresh,
    fail: () => { options = { ...options, pending: false, failed: true }; setState('error') },
    destroy: () => {
      stopLoadingObservation?.()
      stopLoadingObservation = null
      cancelErrorPresentation?.()
      cancelErrorPresentation = null
      restoreLabels()
      disposed = true
      observer.disconnect()
      image.removeEventListener('load', onLoad)
      image.removeEventListener('error', onError)
    },
  }
}

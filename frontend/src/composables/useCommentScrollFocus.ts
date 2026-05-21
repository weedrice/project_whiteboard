import { nextTick, onMounted, onUnmounted, ref, type Ref } from 'vue'

interface UseCommentScrollFocusOptions {
  contentRef: Ref<HTMLElement | null>
  commentsRef: Ref<HTMLElement | null>
}

export function useCommentScrollFocus({
  contentRef,
  commentsRef,
}: UseCommentScrollFocusOptions) {
  const showComposerCta = ref(false)
  let composerFocusTimer: number | null = null
  const imageLoadTimeouts = new Map<number, () => void>()
  let composerObserver: IntersectionObserver | null = null
  let isDisposed = false

  function clearComposerFocusTimer() {
    if (composerFocusTimer) {
      window.clearTimeout(composerFocusTimer)
      composerFocusTimer = null
    }
  }

  function clearImageLoadTimeoutTimers() {
    imageLoadTimeouts.forEach((resolve, timer) => {
      window.clearTimeout(timer)
      resolve()
    })
    imageLoadTimeouts.clear()
  }

  function scrollToComments() {
    if (isDisposed) return

    const target = document.getElementById('comment-composer') || commentsRef.value
    if (!target) return

    const headerOffset = 96
    const elementPosition = target.getBoundingClientRect().top
    const offsetPosition = elementPosition + window.scrollY - headerOffset

    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    })
  }

  function scrollToCommentComposer() {
    if (isDisposed) return

    const composer = document.getElementById('comment-composer')
    if (!composer) {
      scrollToComments()
      return
    }

    composer.scrollIntoView({ behavior: 'smooth', block: 'start' })

    clearComposerFocusTimer()
    composerFocusTimer = window.setTimeout(() => {
      composerFocusTimer = null
      if (isDisposed) return
      const textarea = composer.querySelector('textarea') as HTMLTextAreaElement | null
      textarea?.focus()
    }, 250)
  }

  function waitForImagesInContent(): Promise<void> {
    if (isDisposed) return Promise.resolve()

    const container = contentRef.value
    if (!container) return Promise.resolve()

    const images = container.querySelectorAll<HTMLImageElement>('img')
    if (images.length === 0) return Promise.resolve()

    const imageLoadTimeout = 8000
    const promises = Array.from(images).map((image) => {
      if (image.complete) return Promise.resolve()
      if (image.loading === 'lazy') image.loading = 'eager'

      return Promise.race([
        new Promise<void>((resolve) => {
          image.onload = () => resolve()
          image.onerror = () => resolve()
        }),
        new Promise<void>((resolve) => {
          const resolveTimeout = () => {
            imageLoadTimeouts.delete(timer)
            resolve()
          }
          const timer = window.setTimeout(resolveTimeout, imageLoadTimeout)
          imageLoadTimeouts.set(timer, resolveTimeout)
        })
      ])
    })

    return Promise.all(promises).then(() => {})
  }

  function scrollToCommentsAfterImagesLoad() {
    waitForImagesInContent().then(() => {
      if (isDisposed) return
      nextTick(() => scrollToComments())
    })
  }

  function setupComposerObserver() {
    if (isDisposed) return

    if (composerObserver) {
      composerObserver.disconnect()
      composerObserver = null
    }

    if (typeof window === 'undefined' || window.innerWidth >= 640) {
      showComposerCta.value = false
      return
    }

    const composer = document.getElementById('comment-composer')
    if (!composer) {
      showComposerCta.value = false
      return
    }

    composerObserver = new IntersectionObserver(([entry]) => {
      showComposerCta.value = !entry.isIntersecting
    }, {
      threshold: 0,
      rootMargin: '0px 0px -18% 0px'
    })

    composerObserver.observe(composer)
  }

  function handleResize() {
    setupComposerObserver()
  }

  function disposeCommentScrollFocus() {
    isDisposed = true
    clearComposerFocusTimer()
    clearImageLoadTimeoutTimers()
    if (composerObserver) {
      composerObserver.disconnect()
      composerObserver = null
    }
  }

  onMounted(() => {
    isDisposed = false
  })
  onUnmounted(disposeCommentScrollFocus)

  return {
    showComposerCta,
    scrollToCommentComposer,
    scrollToComments,
    scrollToCommentsAfterImagesLoad,
    setupComposerObserver,
    handleResize,
  }
}

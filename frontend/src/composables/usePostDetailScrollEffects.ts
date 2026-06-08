import { nextTick, onMounted, onUnmounted, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import type { Post } from '@/types'
import type { PostDetailViewModel } from '@/composables/usePostDetailViewModel'
import { useEventListener } from '@/composables/useEventListener'
import { isInputFocused } from '@/utils/keyboard'

interface UsePostDetailScrollEffectsOptions {
  route: RouteLocationNormalizedLoaded
  router: Router
  post: Ref<Post | undefined>
  postView: ComputedRef<PostDetailViewModel | null>
  authStore: { isAuthenticated: boolean }
  canEdit: ComputedRef<boolean>
  isReportModalOpen: Ref<boolean>
  isBlurred: Ref<boolean>
  timeLeft: Ref<number>
  startBlurTimer: () => void
  clearBlurTimer: () => void
  markPostDetailUiMounted: () => void
  isPostDetailUiDisposed: () => boolean
  scheduleComposerFocus: (composer: HTMLElement) => void
  trackImageLoadTimeout: (resolve: () => void, timeoutMs: number) => void
  setupComposerObserver: () => void
  disposePostDetailUiEffects: () => void
  syncBoardListPageForDirectEntry: () => void
  buildEditRoute: () => string
  goToList: () => void
  handleBookmark: () => void
  handleShare: () => void
  handleCopyUrl: () => void
  handleLike: () => void
}

export function usePostDetailScrollEffects({
  route,
  router,
  post,
  postView,
  authStore,
  canEdit,
  isReportModalOpen,
  isBlurred,
  timeLeft,
  startBlurTimer,
  clearBlurTimer,
  markPostDetailUiMounted,
  isPostDetailUiDisposed,
  scheduleComposerFocus,
  trackImageLoadTimeout,
  setupComposerObserver,
  disposePostDetailUiEffects,
  syncBoardListPageForDirectEntry,
  buildEditRoute,
  goToList,
  handleBookmark,
  handleShare,
  handleCopyUrl,
  handleLike,
}: UsePostDetailScrollEffectsOptions) {
  const contentRef = ref<HTMLElement | null>(null)
  const commentsRef = ref<HTMLElement | null>(null)

  function scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function findElementByHash(hash: string): HTMLElement | null {
    if (!hash.startsWith('#')) return null

    const rawId = hash.slice(1)
    if (!rawId) return null

    try {
      return document.getElementById(decodeURIComponent(rawId))
    } catch {
      return document.getElementById(rawId)
    }
  }

  function scrollToCommentComposer() {
    if (isPostDetailUiDisposed()) return

    const composer = document.getElementById('comment-composer')
    if (!composer) {
      scrollToComments()
      return
    }

    composer.scrollIntoView({ behavior: 'smooth', block: 'start' })
    scheduleComposerFocus(composer)
  }

  function scrollToComments() {
    if (isPostDetailUiDisposed()) return

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

  function waitForImagesInContent(): Promise<void> {
    if (isPostDetailUiDisposed()) return Promise.resolve()

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
        new Promise<void>((resolve) => trackImageLoadTimeout(resolve, imageLoadTimeout))
      ])
    })

    return Promise.all(promises).then(() => {})
  }

  function scrollToCommentsAfterImagesLoad() {
    const expectedPostId = postView.value?.postId
    const expectedHash = route.hash

    waitForImagesInContent().then(() => {
      if (isPostDetailUiDisposed()) return
      if (expectedPostId !== postView.value?.postId) return
      if (expectedHash && route.hash !== expectedHash) return

      nextTick(() => {
        if (isPostDetailUiDisposed()) return
        if (expectedPostId !== postView.value?.postId) return
        scrollToComments()
      })
    })
  }

  function handleResize() {
    setupComposerObserver()
  }

  const handleKeyDown = (event: KeyboardEvent) => {
    const { key, shiftKey, ctrlKey, altKey, metaKey } = event

    if (ctrlKey || altKey || metaKey) return
    if (isInputFocused()) return
    if (isReportModalOpen.value) return

    if (shiftKey) {
      if (key === 'S') {
        if (authStore.isAuthenticated && postView.value) {
          event.preventDefault()
          handleBookmark()
        }
        return
      }
      if (key === 'Y') {
        event.preventDefault()
        handleShare()
      }
      return
    }

    switch (key) {
      case 'c':
        event.preventDefault()
        scrollToComments()
        break
      case 'u':
        event.preventDefault()
        goToList()
        break
      case 'l':
        if (authStore.isAuthenticated && postView.value) {
          event.preventDefault()
          handleLike()
        }
        break
      case 'y':
        event.preventDefault()
        handleCopyUrl()
        break
      case 'e':
        if (canEdit.value && postView.value) {
          event.preventDefault()
          router.push(buildEditRoute())
        }
        break
      case 'Escape':
        event.preventDefault()
        goToList()
        break
    }
  }

  watch(() => route.hash, (newHash) => {
    if (!newHash) return

    nextTick(() => {
      if (newHash === '#comments') {
        scrollToCommentsAfterImagesLoad()
        return
      }

      const element = findElementByHash(newHash)
      if (element) {
        element.scrollIntoView({ behavior: 'smooth' })
      }
    })
  })

  watch(post, (newPost, oldPost) => {
    if (!newPost) return

    syncBoardListPageForDirectEntry()

    if (newPost.isSpoiler) {
      isBlurred.value = true
      timeLeft.value = 5
      startBlurTimer()
    } else {
      isBlurred.value = false
      clearBlurTimer()
    }

    nextTick(() => setupComposerObserver())

    if (!oldPost || newPost.postId !== oldPost.postId) {
      const expectedPostId = newPost.postId
      const expectedRouteName = route.name
      nextTick(() => {
        if (isPostDetailUiDisposed()) return
        if (expectedPostId !== postView.value?.postId) return
        if (route.name !== expectedRouteName) return

        const hash = route.hash
        if (hash === '#comments') {
          scrollToCommentsAfterImagesLoad()
          return
        }

        window.scrollTo(0, 0)
        if (hash) {
          const element = findElementByHash(hash)
          if (element) {
            element.scrollIntoView({ behavior: 'smooth' })
          }
        }
      })
    }
  }, { immediate: true })

  onMounted(() => {
    markPostDetailUiMounted()
    nextTick(() => setupComposerObserver())
  })

  useEventListener(() => document, 'keydown', handleKeyDown)
  useEventListener(() => window, 'resize', handleResize)

  onUnmounted(() => {
    disposePostDetailUiEffects()
  })

  return {
    contentRef,
    commentsRef,
    scrollToTop,
    scrollToCommentComposer,
    scrollToComments,
  }
}

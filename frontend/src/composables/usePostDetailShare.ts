import { computed, onUnmounted, ref, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import type { Post } from '@/types'
import { getWindowOrigin, isNarrowViewport } from '@/utils/browserEnv'
import logger from '@/utils/logger'

interface UsePostDetailShareOptions {
  route: RouteLocationNormalizedLoaded
  post: Ref<Post | null | undefined>
  t: (key: string) => string
}

export function usePostDetailShare({
  route,
  post,
  t,
}: UsePostDetailShareOptions) {
  const toastStore = useToastStore()
  const showCopyHint = ref(false)
  let copyHintTimer: ReturnType<typeof setTimeout> | null = null
  let isDisposed = false

  function isAbortError(error: unknown) {
    return error instanceof DOMException
      ? error.name === 'AbortError'
      : !!error && typeof error === 'object' && 'name' in error && error.name === 'AbortError'
  }

  const currentUrl = computed(() => {
    const origin = getWindowOrigin('')
    return origin ? `${origin}${route.fullPath}` : ''
  })

  const compactUrl = computed(() => {
    if (!currentUrl.value) return ''

    try {
      const url = new URL(currentUrl.value)
      return `${url.host}${url.pathname}`
    } catch {
      return currentUrl.value
    }
  })

  function clearCopyHintTimer() {
    if (copyHintTimer) {
      clearTimeout(copyHintTimer)
      copyHintTimer = null
    }
  }

  function handleCopyUrl(showToast = true) {
    if (!currentUrl.value) return
    if (typeof navigator === 'undefined' || typeof navigator.clipboard?.writeText !== 'function') {
      toastStore.addToast(t('common.messages.processFailed'), 'error')
      return
    }

    navigator.clipboard.writeText(currentUrl.value).then(() => {
      if (isDisposed) return

      if (showToast) {
        toastStore.addToast(t('common.messages.urlCopied'), 'success')
        return
      }

      showCopyHint.value = true
      clearCopyHintTimer()
      copyHintTimer = setTimeout(() => {
        showCopyHint.value = false
        copyHintTimer = null
      }, 1500)
      ;(document.activeElement as HTMLElement | null)?.blur()
    }).catch((err) => {
      if (isDisposed) return

      logger.error('Failed to copy URL:', err)
      toastStore.addToast(t('common.messages.processFailed'), 'error')
    })
  }

  function onUrlBarClick() {
    if (isNarrowViewport(640)) {
      handleCopyUrl(false)
      return
    }

    handleCopyUrl(true)
  }

  function handleShare() {
    if (typeof navigator !== 'undefined' && typeof navigator.share === 'function' && post.value) {
      navigator.share({
        title: post.value.title,
        url: currentUrl.value
      }).catch((err) => {
        if (isDisposed || isAbortError(err)) {
          return
        }

        logger.error('Share failed:', err)
        toastStore.addToast(t('common.messages.processFailed'), 'error')
      })
      return
    }

    handleCopyUrl()
  }

  onUnmounted(() => {
    isDisposed = true
    clearCopyHintTimer()
  })

  return {
    currentUrl,
    compactUrl,
    showCopyHint,
    handleCopyUrl,
    onUrlBarClick,
    handleShare,
  }
}

import { computed, onUnmounted, ref, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { Post } from '@/types'
import logger from '@/utils/logger'

interface ToastStoreLike {
  addToast: (message: string, type?: 'info' | 'success' | 'error' | 'warning') => void
}

interface UsePostDetailShareOptions {
  route: RouteLocationNormalizedLoaded
  post: Ref<Post | null | undefined>
  toastStore: ToastStoreLike
  t: (key: string) => string
}

export function usePostDetailShare({
  route,
  post,
  toastStore,
  t,
}: UsePostDetailShareOptions) {
  const showCopyHint = ref(false)
  let copyHintTimer: ReturnType<typeof setTimeout> | null = null

  const translateOrFallback = (key: string, fallback: string) => {
    const translated = t(key)
    return translated === key ? fallback : translated
  }

  const currentUrl = computed(() => {
    if (typeof window === 'undefined') return ''
    return `${window.location.origin}${route.fullPath}`
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

    navigator.clipboard.writeText(currentUrl.value).then(() => {
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
      logger.error('Failed to copy URL:', err)
      toastStore.addToast(translateOrFallback('common.messages.processFailed', '주소 복사에 실패했습니다.'), 'error')
    })
  }

  function onUrlBarClick() {
    if (typeof window !== 'undefined' && window.innerWidth < 640) {
      handleCopyUrl(false)
      return
    }

    handleCopyUrl(true)
  }

  function handleShare() {
    if (navigator.share && post.value) {
      navigator.share({
        title: post.value.title,
        url: currentUrl.value
      }).catch((err) => {
        if (err.name !== 'AbortError') {
          logger.error('Share failed:', err)
          toastStore.addToast(translateOrFallback('common.messages.processFailed', '공유에 실패했습니다.'), 'error')
        }
      })
      return
    }

    handleCopyUrl()
  }

  onUnmounted(clearCopyHintTimer)

  return {
    currentUrl,
    compactUrl,
    showCopyHint,
    handleCopyUrl,
    onUrlBarClick,
    handleShare,
  }
}

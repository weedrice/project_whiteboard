<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  ArrowLeft,
  Bookmark,
  Clock,
  Copy,
  Eye,
  List,
  MessageSquare,
  MoreHorizontal,
  Share2,
  ThumbsUp,
  User
} from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import CommentList from '@/components/comment/CommentList.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useConfirm } from '@/composables/useConfirm'
import { usePost } from '@/composables/usePost'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { formatDate } from '@/utils/date'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { isInputFocused } from '@/utils/keyboard'
import logger from '@/utils/logger'
import { sanitizeQuillHtml } from '@/utils/sanitize'

interface TocItem {
  id: string
  text: string
  level: 2 | 3
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const {
  useDeletePost,
  useLikePost,
  usePostDetail,
  useReportPost,
  useScrapPost,
  useUnlikePost,
  useUnscrapPost
} = usePost()

const postId = computed(() => route.params.postId as string)
const {
  data: post,
  isLoading,
  error: postError
} = usePostDetail(postId, {
  meta: { errorMessage: false },
  requestConfig: { skipGlobalErrorHandler: true }
})

const postPageTitle = computed(() => {
  const postTitle = post.value?.title?.trim()
  const boardName = post.value?.board?.boardName?.trim()

  if (postTitle && boardName) {
    return `${postTitle} - ${boardName}`
  }
  return postTitle || 'Post'
})

const postDescription = computed(() => {
  if (!post.value?.contents) return 'Post content'
  const text = post.value.contents.replace(/<[^>]*>/g, '').trim().slice(0, 160)
  return text + (text.length >= 160 ? '...' : '')
})

const canonicalUrl = computed(() => {
  if (typeof window === 'undefined') return ''
  const normalizedPath = route.path.endsWith('/') ? route.path : `${route.path}/`
  return `${window.location.origin}${normalizedPath}`
})

const articleStructuredData = computed(() => {
  if (!post.value || !canonicalUrl.value) return ''

  return JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: post.value.title,
    datePublished: post.value.createdAt,
    dateModified: post.value.modifiedAt || post.value.createdAt,
    author: {
      '@type': 'Person',
      name: post.value.author.displayName
    },
    mainEntityOfPage: canonicalUrl.value,
    url: canonicalUrl.value
  })
})

useHead({
  titleTemplate: '%s',
  title: postPageTitle,
  link: [
    { rel: 'canonical', href: canonicalUrl }
  ],
  meta: [
    { name: 'description', content: postDescription },
    { property: 'og:title', content: computed(() => `${post.value?.title || 'Post'} - ${t('common.appName')}`) },
    { property: 'og:description', content: postDescription },
    { property: 'og:type', content: 'article' },
    { property: 'og:url', content: canonicalUrl }
  ],
  script: [
    {
      type: 'application/ld+json',
      textContent: articleStructuredData
    }
  ]
})

watch([() => route.name, postPageTitle], ([routeName, title]) => {
  if (routeName !== 'post-detail' || typeof document === 'undefined') {
    return
  }
  document.title = title
}, { immediate: true })

const { mutate: deleteMutate } = useDeletePost()
const { mutate: likeMutate } = useLikePost()
const { mutate: unlikeMutate } = useUnlikePost()
const { mutate: scrapMutate } = useScrapPost()
const { mutate: unscrapMutate } = useUnscrapPost()
const { mutate: reportMutate } = useReportPost()

const error = computed(() => {
  if (!postError.value) return ''
  if (isRestrictedResourceError(postError.value)) {
    return '접근 권한이 없는 게시글입니다.'
  }
  return t('board.postDetail.loadFailed')
})

const isAuthor = computed(() => {
  return !!authStore.user && !!post.value && authStore.user.userId === post.value.author.userId
})

const isAgentAuthor = computed(() => post.value?.author?.authorType === 'AGENT')
const isAdmin = computed(() => authStore.isAdmin)
const canEdit = computed(() => isAuthor.value && !!post.value)
const canDelete = computed(() => !!post.value && (isAuthor.value || isAdmin.value || !!post.value.board.isAdmin))
const canReport = computed(() => authStore.isAuthenticated && !isAuthor.value)

const processedContents = computed(() => {
  if (!post.value?.contents) return ''
  const normalizedContents = post.value.contents.replace(/<p>\s*<\/p>/gi, '<p><br></p>')
  const sanitized = sanitizeQuillHtml(normalizedContents)
  return sanitized.replace(/<img(?![^>]*\bloading=)([^>]+)>/gi, '<img loading="lazy"$1>')
})

const tocItems = ref<TocItem[]>([])
const isBlurred = ref(false)
const timeLeft = ref(5)
const isLikeAnimating = ref(false)
const isBookmarkAnimating = ref(false)
const showReportModal = ref(false)
const reportReason = ref('')
const showOverflowMenu = ref(false)
const showCopyHint = ref(false)
const showComposerCta = ref(false)

const titleRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const commentsRef = ref<HTMLElement | null>(null)
const overflowRef = ref<HTMLElement | null>(null)
const overflowButtonRef = ref<HTMLElement | null>(null)

let blurTimer: ReturnType<typeof setInterval> | null = null
let likeAnimationTimer: ReturnType<typeof setTimeout> | null = null
let bookmarkAnimationTimer: ReturnType<typeof setTimeout> | null = null
let copyHintTimer: ReturnType<typeof setTimeout> | null = null
let composerObserver: IntersectionObserver | null = null

const translateOrFallback = (key: string, fallback: string) => {
  const translated = t(key)
  return translated === key ? fallback : translated
}

const currentUrl = computed(() => {
  if (typeof window === 'undefined') return ''
  return `${window.location.origin}${route.fullPath}`
})

const hasToc = computed(() => tocItems.value.length > 0)

function slugifyHeading(text: string, index: number) {
  const slug = text
    .toLowerCase()
    .replace(/[^\w가-힣]+/g, '-')
    .replace(/^-+|-+$/g, '')

  return `post-heading-${index}-${slug || 'section'}`
}

function syncToc() {
  if (!contentRef.value) {
    tocItems.value = []
    return
  }

  const headings = Array.from(contentRef.value.querySelectorAll<HTMLElement>('h2, h3'))
  tocItems.value = headings
    .map((heading, index) => {
      const text = heading.textContent?.trim() || ''
      if (!text) return null

      const id = heading.id || slugifyHeading(text, index)
      heading.id = id

      return {
        id,
        text,
        level: heading.tagName === 'H2' ? 2 : 3
      } satisfies TocItem
    })
    .filter((item): item is TocItem => item !== null)
}

function clearBlurTimer() {
  if (blurTimer) {
    clearInterval(blurTimer)
    blurTimer = null
  }
}

function startBlurTimer() {
  clearBlurTimer()
  blurTimer = setInterval(() => {
    timeLeft.value -= 1
    if (timeLeft.value <= 0) {
      revealSpoiler()
    }
  }, 1000)
}

function revealSpoiler() {
  isBlurred.value = false
  clearBlurTimer()
}

function buildBoardListRoute(boardUrl: string) {
  return {
    path: `/board/${boardUrl}`,
    query: route.query
  }
}

function buildEditRoute() {
  if (!post.value) return '/'
  return `/board/${post.value.board.boardUrl}/post/${post.value.postId}/edit`
}

async function handleDelete() {
  const isConfirmed = await confirm(t('common.messages.confirmDelete'))
  if (!isConfirmed) return

  deleteMutate(route.params.postId as string | number, {
    onSuccess: () => {
      if (post.value?.board.boardUrl) {
        router.push(buildBoardListRoute(post.value.board.boardUrl))
      }
    },
    onError: (err) => {
      logger.error('Failed to delete post:', err)
    }
  })
}

function triggerLikeAnimation() {
  isLikeAnimating.value = true
  if (likeAnimationTimer) clearTimeout(likeAnimationTimer)
  likeAnimationTimer = setTimeout(() => {
    isLikeAnimating.value = false
    likeAnimationTimer = null
  }, 300)
}

function triggerBookmarkAnimation() {
  isBookmarkAnimating.value = true
  if (bookmarkAnimationTimer) clearTimeout(bookmarkAnimationTimer)
  bookmarkAnimationTimer = setTimeout(() => {
    isBookmarkAnimating.value = false
    bookmarkAnimationTimer = null
  }, 300)
}

async function handleLike() {
  if (!authStore.isAuthenticated || !post.value) return

  if (post.value.liked) {
    unlikeMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
    })
    return
  }

  triggerLikeAnimation()
  likeMutate(route.params.postId as string | number, {
    onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
  })
}

async function handleBookmark() {
  if (!authStore.isAuthenticated || !post.value) return

  if (post.value.scrapped) {
    unscrapMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
    })
    return
  }

  triggerBookmarkAnimation()
  scrapMutate(route.params.postId as string | number, {
    onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
  })
}

function openReportModal() {
  if (!canReport.value) return
  showReportModal.value = true
  reportReason.value = ''
  showOverflowMenu.value = false
}

async function submitReport() {
  if (!reportReason.value.trim()) {
    toastStore.addToast(t('board.postDetail.reportReasonRequired'), 'error')
    return
  }

  reportMutate({
    targetPostId: route.params.postId as string | number,
    reason: reportReason.value
  }, {
    onSuccess: () => {
      toastStore.addToast(t('board.postDetail.reportSuccess'), 'success')
      showReportModal.value = false
    },
    onError: (err) => {
      logger.error('Report failed:', err)
      toastStore.addToast(t('board.postDetail.reportFailed'), 'error')
    }
  })
}

function handleCopyUrl(showToast = true) {
  if (!currentUrl.value) return

  navigator.clipboard.writeText(currentUrl.value).then(() => {
    if (showToast) {
      toastStore.addToast(t('common.messages.urlCopied'), 'success')
      return
    }

    showCopyHint.value = true
    if (copyHintTimer) clearTimeout(copyHintTimer)
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
  if (typeof window !== 'undefined' && window.innerWidth >= 640) return
  handleCopyUrl(false)
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

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function scrollToHeading(id: string) {
  const target = document.getElementById(id)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function scrollToCommentComposer() {
  const composer = document.getElementById('comment-composer')
  if (!composer) {
    scrollToComments()
    return
  }

  composer.scrollIntoView({ behavior: 'smooth', block: 'start' })

  window.setTimeout(() => {
    const textarea = composer.querySelector('textarea') as HTMLTextAreaElement | null
    textarea?.focus()
  }, 250)
}

function scrollToComments() {
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
      new Promise<void>((resolve) => setTimeout(resolve, imageLoadTimeout))
    ])
  })

  return Promise.all(promises).then(() => {})
}

function scrollToCommentsAfterImagesLoad() {
  waitForImagesInContent().then(() => {
    nextTick(() => scrollToComments())
  })
}

function goToList() {
  const listElement = document.getElementById('board-post-list')
  if (listElement) {
    listElement.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }

  if (post.value?.board) {
    router.push(buildBoardListRoute(post.value.board.boardUrl))
    return
  }

  router.back()
}

function setupComposerObserver() {
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

function closeOverflowMenu() {
  showOverflowMenu.value = false
}

function toggleOverflowMenu() {
  showOverflowMenu.value = !showOverflowMenu.value
}

function handleDocumentClick(event: MouseEvent) {
  if (!showOverflowMenu.value) return

  const target = event.target as Node | null
  if (
    overflowRef.value?.contains(target) ||
    overflowButtonRef.value?.contains(target)
  ) {
    return
  }

  closeOverflowMenu()
}

function handleResize() {
  setupComposerObserver()
}

const handleKeyDown = (event: KeyboardEvent) => {
  const { key, shiftKey, ctrlKey, altKey, metaKey } = event

  if (ctrlKey || altKey || metaKey) return
  if (isInputFocused()) return

  if (shiftKey) {
    if (key === 'S') {
      if (authStore.isAuthenticated && post.value) {
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
      if (authStore.isAuthenticated && post.value) {
        event.preventDefault()
        handleLike()
      }
      break
    case 'y':
      event.preventDefault()
      handleCopyUrl()
      break
    case 'e':
      if (canEdit.value && post.value) {
        event.preventDefault()
        router.push(buildEditRoute())
      }
      break
    case 'Escape':
      event.preventDefault()
      if (showOverflowMenu.value) {
        closeOverflowMenu()
      } else {
        goToList()
      }
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

    const element = document.querySelector(newHash)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' })
    }
  })
})

watch(post, (newPost, oldPost) => {
  if (!newPost) return

  if (newPost.isSpoiler) {
    isBlurred.value = true
    timeLeft.value = 5
    startBlurTimer()
  } else {
    isBlurred.value = false
    clearBlurTimer()
  }

  nextTick(() => {
    syncToc()
    setupComposerObserver()
  })

  if (!oldPost || newPost.postId !== oldPost.postId) {
    nextTick(() => {
      const hash = route.hash
      if (hash === '#comments') {
        scrollToCommentsAfterImagesLoad()
        return
      }

      window.scrollTo(0, 0)
      if (hash) {
        const element = document.querySelector(hash)
        if (element) {
          element.scrollIntoView({ behavior: 'smooth' })
        }
      }
    })
  }
})

watch(processedContents, () => {
  nextTick(() => {
    syncToc()
  })
})

onMounted(() => {
  nextTick(() => {
    syncToc()
    setupComposerObserver()
  })

  document.addEventListener('keydown', handleKeyDown)
  document.addEventListener('click', handleDocumentClick)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  document.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('resize', handleResize)

  clearBlurTimer()
  if (composerObserver) {
    composerObserver.disconnect()
    composerObserver = null
  }
  if (likeAnimationTimer) {
    clearTimeout(likeAnimationTimer)
    likeAnimationTimer = null
  }
  if (bookmarkAnimationTimer) {
    clearTimeout(bookmarkAnimationTimer)
    bookmarkAnimationTimer = null
  }
  if (copyHintTimer) {
    clearTimeout(copyHintTimer)
    copyHintTimer = null
  }
})
</script>

<template>
  <div class="nv-post-shell">
    <BaseCard noPadding>
      <div v-if="isLoading" class="px-4 py-5 sm:px-6 sm:py-6">
        <div class="mb-4 flex items-center justify-between">
          <BaseSkeleton width="96px" height="36px" rounded="rounded-full" />
          <div class="flex gap-2">
            <BaseSkeleton width="40px" height="40px" rounded="rounded-full" />
            <BaseSkeleton width="40px" height="40px" rounded="rounded-full" />
          </div>
        </div>
        <div class="space-y-3">
          <BaseSkeleton width="72%" height="34px" />
          <div class="flex gap-3">
            <BaseSkeleton width="128px" height="18px" />
            <BaseSkeleton width="96px" height="18px" />
            <BaseSkeleton width="72px" height="18px" />
          </div>
        </div>
        <div class="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <div class="space-y-3">
            <BaseSkeleton width="100%" height="40px" rounded="rounded-2xl" />
            <BaseSkeleton width="100%" height="420px" rounded="rounded-[28px]" />
          </div>
          <div class="hidden gap-4 lg:grid">
            <BaseSkeleton width="100%" height="160px" rounded="rounded-[28px]" />
            <BaseSkeleton width="100%" height="220px" rounded="rounded-[28px]" />
          </div>
        </div>
      </div>

      <div v-else-if="error" class="px-4 py-12 text-center text-sm text-red-500 sm:px-6">
        {{ error }}
        <div class="mt-4">
          <BaseButton @click="router.back()" variant="ghost" size="sm">
            {{ $t('common.back') }}
          </BaseButton>
        </div>
      </div>

      <template v-else-if="post">
        <header class="nv-post-header px-4 py-4 sm:px-6 sm:py-5">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0 flex-1 space-y-4">
              <div class="flex items-center justify-between gap-2">
                <BaseButton
                  @click="router.push(buildBoardListRoute(post.board.boardUrl))"
                  variant="ghost"
                  size="sm"
                  class="nv-post-back-btn"
                >
                  <ArrowLeft class="h-4 w-4" />
                  <span class="hidden sm:inline">{{ $t('board.postDetail.toList') }}</span>
                </BaseButton>

                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="nv-post-icon-btn"
                    :aria-label="$t('common.share')"
                    @click="handleShare"
                  >
                    <Share2 class="h-4 w-4" />
                  </button>

                  <div class="relative">
                    <button
                      ref="overflowButtonRef"
                      type="button"
                      class="nv-post-icon-btn"
                      :aria-expanded="showOverflowMenu"
                      aria-haspopup="menu"
                      aria-label="더보기"
                      @click="toggleOverflowMenu"
                    >
                      <MoreHorizontal class="h-4 w-4" />
                    </button>

                    <div
                      v-if="showOverflowMenu"
                      ref="overflowRef"
                      class="nv-post-overflow"
                      role="menu"
                    >
                      <router-link
                        v-if="canEdit"
                        :to="buildEditRoute()"
                        class="nv-post-overflow-item"
                        role="menuitem"
                        @click="closeOverflowMenu"
                      >
                        {{ $t('common.edit') }}
                      </router-link>
                      <button
                        v-if="canDelete"
                        type="button"
                        class="nv-post-overflow-item"
                        role="menuitem"
                        @click="closeOverflowMenu(); handleDelete()"
                      >
                        {{ $t('common.delete') }}
                      </button>
                      <button
                        v-if="canReport"
                        type="button"
                        class="nv-post-overflow-item"
                        role="menuitem"
                        @click="openReportModal"
                      >
                        {{ $t('common.report') }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div class="space-y-3">
                <p class="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                  {{ post.board.boardName }}
                </p>
                <h1 ref="titleRef" class="text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-4xl">
                  {{ post.title }}
                </h1>
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                  <span class="inline-flex items-center gap-1.5">
                    <User class="h-4 w-4" />
                    <UserMenu :user-id="post.author.userId" :display-name="post.author.displayName" size="inherit" />
                    <span
                      v-if="isAgentAuthor"
                      class="rounded-full bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"
                    >
                      AGENT
                    </span>
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <Clock class="h-4 w-4" />
                    {{ formatDate(post.createdAt) }}
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <Eye class="h-4 w-4" />
                    {{ post.viewCount }}
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <MessageSquare class="h-4 w-4" />
                    {{ post.commentCount }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </header>

        <div class="grid gap-6 px-4 pb-4 pt-5 lg:grid-cols-[minmax(0,1fr)_18rem] lg:px-6 lg:pb-6">
          <article class="min-w-0 space-y-5">
            <div class="relative">
              <Transition name="fade">
                <span
                  v-if="showCopyHint"
                  class="absolute right-0 top-[-2rem] z-10 rounded-full bg-green-50 px-3 py-1 text-[11px] font-medium text-green-600 shadow-sm dark:bg-green-900/30 dark:text-green-400 sm:hidden"
                >
                  {{ $t('common.messages.urlCopied') }}
                </span>
              </Transition>

              <div
                class="nv-post-urlbar"
                role="button"
                tabindex="0"
                :aria-label="$t('common.copy')"
                @click="onUrlBarClick"
                @keydown.enter="onUrlBarClick"
                @keydown.space.prevent="onUrlBarClick"
              >
                <span class="truncate text-[11px] text-[var(--nv-muted)] sm:text-xs">{{ currentUrl }}</span>
                <div class="hidden h-3 w-px bg-[var(--nv-line)] sm:block"></div>
                <BaseButton
                  @click.stop="handleCopyUrl(true)"
                  variant="ghost"
                  size="sm"
                  class="hidden sm:!inline-flex sm:!px-2 sm:!py-1 sm:!text-xs"
                >
                  <Copy class="mr-1 h-3.5 w-3.5" />
                  {{ $t('common.copy') }}
                </BaseButton>
              </div>
            </div>

            <div class="nv-post-article relative overflow-hidden">
              <div
                ref="contentRef"
                class="ql-editor prose prose-sm max-w-none text-sm text-[var(--nv-ink)] sm:prose-base dark:prose-invert sm:text-base"
                :class="{ 'blur-md select-none': isBlurred }"
                v-html="processedContents"
              ></div>

              <div v-if="isBlurred" class="nv-post-spoiler">
                <div class="nv-post-spoiler-card">
                  <h3 class="text-lg font-semibold text-[var(--nv-ink)]">
                    {{ $t('board.postDetail.spoilerWarning') }}
                  </h3>
                  <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
                    {{ $t('board.postDetail.spoilerTimer', { time: timeLeft }) }}
                  </p>
                  <BaseButton @click="revealSpoiler" size="sm" class="mt-4">
                    {{ $t('board.postDetail.revealSpoiler') }}
                  </BaseButton>
                </div>
              </div>
            </div>

            <div
              v-if="post.tags && post.tags.length > 0"
              class="rounded-[28px] border border-[var(--nv-line)] bg-[color:var(--nv-surface)] px-4 py-4"
            >
              <PostTags :modelValue="post.tags" :readOnly="true" :boardUrl="post.board.boardUrl" />
            </div>

            <div class="hidden rounded-[28px] border border-[var(--nv-line)] bg-[color:var(--nv-surface)] px-4 py-4 sm:block">
              <div class="grid grid-cols-3 gap-2">
                <button
                  type="button"
                  class="nv-post-action-btn"
                  :class="{ 'is-active': post.liked }"
                  :disabled="!authStore.isAuthenticated"
                  @click="handleLike"
                >
                  <ThumbsUp class="h-5 w-5" :class="{ 'fill-current bounce-in': isLikeAnimating }" />
                  <span>{{ post.likeCount }}</span>
                </button>
                <button
                  type="button"
                  class="nv-post-action-btn"
                  :class="{ 'is-active is-bookmark': post.scrapped }"
                  :disabled="!authStore.isAuthenticated"
                  @click="handleBookmark"
                >
                  <Bookmark class="h-5 w-5" :class="{ 'fill-current bounce-in': isBookmarkAnimating }" />
                  <span>북마크</span>
                </button>
                <button type="button" class="nv-post-action-btn" @click="handleShare">
                  <Share2 class="h-5 w-5" />
                  <span>{{ $t('common.share') }}</span>
                </button>
              </div>
            </div>

            <section id="comments" ref="commentsRef" class="nv-post-comments">
              <CommentList :postId="post.postId" :boardUrl="post.board.boardUrl" />
            </section>
          </article>

          <aside class="hidden lg:block">
            <div class="sticky top-24 space-y-4">
              <section class="nv-post-sidebar-card">
                <h2 class="nv-post-sidebar-title">Quick Actions</h2>
                <div class="mt-3 grid gap-2">
                  <button type="button" class="nv-post-sidebar-btn" @click="scrollToComments">
                    <MessageSquare class="h-4 w-4" />
                    댓글로 이동
                  </button>
                  <button type="button" class="nv-post-sidebar-btn" @click="goToList">
                    <List class="h-4 w-4" />
                    목록으로
                  </button>
                  <button type="button" class="nv-post-sidebar-btn" @click="scrollToTop">
                    <ArrowLeft class="h-4 w-4 rotate-90" />
                    상단으로
                  </button>
                </div>
              </section>

              <section v-if="hasToc" class="nv-post-sidebar-card">
                <h2 class="nv-post-sidebar-title">Table of Contents</h2>
                <nav class="mt-3 space-y-1.5" aria-label="Post table of contents">
                  <button
                    v-for="item in tocItems"
                    :key="item.id"
                    type="button"
                    class="nv-post-toc-item"
                    :class="{ 'is-nested': item.level === 3 }"
                    @click="scrollToHeading(item.id)"
                  >
                    {{ item.text }}
                  </button>
                </nav>
              </section>
            </div>
          </aside>
        </div>
      </template>
    </BaseCard>

    <Transition name="slide-up">
      <button
        v-if="showComposerCta"
        type="button"
        class="nv-post-mobile-comment-cta sm:hidden"
        @click="scrollToCommentComposer"
      >
        댓글 작성창으로 이동
      </button>
    </Transition>

    <div v-if="post" class="nv-post-mobile-bar sm:hidden">
      <button
        type="button"
        class="nv-post-mobile-action"
        :class="{ 'is-active': post.liked }"
        :disabled="!authStore.isAuthenticated"
        @click="handleLike"
      >
        <ThumbsUp class="h-5 w-5" :class="{ 'fill-current bounce-in': isLikeAnimating }" />
        <span>{{ post.likeCount }}</span>
      </button>
      <button
        type="button"
        class="nv-post-mobile-action"
        :class="{ 'is-active is-bookmark': post.scrapped }"
        :disabled="!authStore.isAuthenticated"
        @click="handleBookmark"
      >
        <Bookmark class="h-5 w-5" :class="{ 'fill-current bounce-in': isBookmarkAnimating }" />
        <span>북마크</span>
      </button>
      <button type="button" class="nv-post-mobile-action" @click="scrollToComments">
        <MessageSquare class="h-5 w-5" />
        <span>댓글</span>
      </button>
      <button type="button" class="nv-post-mobile-action" @click="handleShare">
        <Share2 class="h-5 w-5" />
        <span>{{ $t('common.share') }}</span>
      </button>
    </div>

    <BaseModal :isOpen="showReportModal" :title="$t('common.report')" @close="showReportModal = false">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('report.target') }}
          </label>
          <div class="mt-1 text-sm font-medium text-gray-900 dark:text-white">
            {{ $t('common.post') }} | {{ post?.title }}
          </div>
        </div>
        <div>
          <BaseTextarea
            id="report-reason"
            v-model="reportReason"
            :label="$t('report.reason')"
            rows="4"
            :placeholder="$t('report.inputReason')"
          />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <BaseButton @click="showReportModal = false" variant="secondary" size="sm">
            {{ $t('common.cancel') }}
          </BaseButton>
          <BaseButton @click="submitReport" variant="danger" size="sm">
            {{ $t('common.submit') }}
          </BaseButton>
        </div>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.nv-post-shell {
  color: var(--nv-ink);
  position: relative;
}

.nv-post-header {
  border-bottom: 1px solid var(--nv-line);
}

.nv-post-back-btn {
  gap: 0.375rem;
}

.nv-post-icon-btn {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink-soft);
  display: inline-flex;
  height: 2.5rem;
  justify-content: center;
  transition: background-color 0.2s ease, color 0.2s ease;
  width: 2.5rem;
}

.nv-post-icon-btn:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-overflow {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.25rem;
  box-shadow: var(--nv-shadow-popup);
  min-width: 10rem;
  padding: 0.4rem;
  position: absolute;
  right: 0;
  top: calc(100% + 0.5rem);
  z-index: 30;
}

.nv-post-overflow-item {
  align-items: center;
  border-radius: 0.95rem;
  color: var(--nv-ink);
  display: flex;
  font-size: 0.9rem;
  justify-content: flex-start;
  padding: 0.7rem 0.9rem;
  transition: background-color 0.2s ease;
  width: 100%;
}

.nv-post-overflow-item:hover {
  background: var(--nv-surface-2);
}

.nv-post-urlbar {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  cursor: pointer;
  display: flex;
  gap: 0.75rem;
  max-width: 100%;
  min-width: 0;
  padding: 0.75rem 0.9rem;
}

.nv-post-article,
.nv-post-comments,
.nv-post-sidebar-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.75rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-post-article {
  padding: 1.15rem 1rem;
}

.nv-post-comments {
  padding: 1.15rem 1rem;
}

.nv-post-spoiler {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface) 45%, transparent);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 1.5rem;
  position: absolute;
}

.nv-post-spoiler-card {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.5rem;
  box-shadow: var(--nv-shadow-card);
  max-width: 22rem;
  padding: 1.5rem;
  text-align: center;
  width: 100%;
}

.nv-post-action-btn,
.nv-post-mobile-action,
.nv-post-sidebar-btn {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.1rem;
  color: var(--nv-ink-soft);
  display: inline-flex;
  gap: 0.55rem;
  justify-content: center;
  min-height: 3rem;
  padding: 0.8rem 1rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-post-action-btn:hover,
.nv-post-sidebar-btn:hover,
.nv-post-mobile-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-action-btn.is-active,
.nv-post-mobile-action.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-post-action-btn.is-bookmark,
.nv-post-mobile-action.is-bookmark {
  color: #bb7a00;
}

.nv-post-sidebar-card {
  padding: 1.15rem;
}

.nv-post-sidebar-title {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-post-sidebar-btn {
  justify-content: flex-start;
  width: 100%;
}

.nv-post-toc-item {
  color: var(--nv-ink-soft);
  display: block;
  font-size: 0.9rem;
  padding: 0.2rem 0;
  text-align: left;
  width: 100%;
}

.nv-post-toc-item:hover {
  color: var(--nv-accent);
}

.nv-post-toc-item.is-nested {
  padding-left: 1rem;
}

.nv-post-mobile-comment-cta {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  bottom: calc(9.5rem + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-ink);
  left: 50%;
  padding: 0.75rem 1rem;
  position: fixed;
  transform: translateX(-50%);
  z-index: 75;
}

.nv-post-mobile-bar {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface) 92%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.35rem;
  bottom: calc(5.75rem + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-popup);
  display: grid;
  gap: 0.5rem;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  left: 0.75rem;
  padding: 0.55rem;
  position: fixed;
  right: 0.75rem;
  z-index: 74;
}

.nv-post-mobile-action {
  flex-direction: column;
  gap: 0.25rem;
  min-height: 3.5rem;
  padding: 0.55rem 0.35rem;
}

.fade-enter-active,
.fade-leave-active,
.slide-up-enter-active,
.slide-up-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to,
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translate(-50%, 0.4rem);
}

.bounce-in {
  animation: bounce-in 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes bounce-in {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.3);
  }
  100% {
    transform: scale(1);
  }
}

@media (min-width: 640px) {
  .nv-post-article,
  .nv-post-comments {
    padding: 1.5rem;
  }
}

@media (max-width: 639px) {
  .nv-post-shell {
    padding-bottom: calc(12rem + env(safe-area-inset-bottom));
  }
}
</style>

<style>
.ql-editor ul {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.ql-editor ul ul {
  list-style-type: circle;
}

.ql-editor ol {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.ql-editor ol ol {
  list-style-type: lower-alpha;
}

.ql-editor li {
  display: list-item;
  margin: 0.25em 0;
}

.ql-editor .tiptap-video-wrapper {
  position: relative;
  padding-bottom: 56.25%;
  height: 0;
  overflow: hidden;
  max-width: 100%;
  margin: 0.75em 0;
}

.ql-editor .tiptap-video-wrapper iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
</style>

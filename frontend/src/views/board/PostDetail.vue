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
  Pencil,
  Share2,
  ThumbsUp,
  Trash2,
  User
} from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostDetailSkeleton from '@/components/board/PostDetailSkeleton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useConfirm } from '@/composables/useConfirm'
import { usePost } from '@/composables/usePost'
import { usePostDetailPermissions } from '@/composables/usePostDetailPermissions'
import { usePostDetailUiEffects } from '@/composables/usePostDetailUiEffects'
import { usePostDetailViewModel } from '@/composables/usePostDetailViewModel'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { formatDate } from '@/utils/date'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { isInputFocused } from '@/utils/keyboard'
import logger from '@/utils/logger'
import { sanitizeQuillHtml } from '@/utils/sanitize'
import { normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { applyImageFallback } from '@/utils/imageFallback'

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
const postView = usePostDetailViewModel(post)

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

const currentUserId = computed(() => authStore.user?.userId)
const isAuthenticated = computed(() => authStore.isAuthenticated)
const authIsAdmin = computed(() => authStore.isAdmin)
const {
  isAuthor,
  isAgentAuthor,
  canEdit,
  canDelete,
  canReport
} = usePostDetailPermissions(post, {
  currentUserId,
  isAuthenticated,
  isAdmin: authIsAdmin
})

const processedContents = computed(() => {
  if (!post.value?.contents) return ''
  const normalizedContents = normalizeLegacyFileUrls(post.value.contents)
    .replace(/<p>\s*<\/p>/gi, '<p><br></p>')
  const sanitized = sanitizeQuillHtml(normalizedContents)
  return sanitized.replace(/<img(?![^>]*\bloading=)([^>]+)>/gi, '<img loading="lazy"$1>')
})

const showReportModal = ref(false)
const reportReason = ref('')
const {
  isBlurred,
  timeLeft,
  isLikeAnimating,
  isBookmarkAnimating,
  showCopyHint,
  showComposerCta,
  markPostDetailUiMounted,
  isPostDetailUiDisposed,
  startBlurTimer,
  clearBlurTimer,
  revealSpoiler,
  triggerLikeAnimation,
  triggerBookmarkAnimation,
  showTemporaryCopyHint,
  scheduleComposerFocus,
  trackImageLoadTimeout,
  setupComposerObserver,
  disposePostDetailUiEffects
} = usePostDetailUiEffects()

const contentRef = ref<HTMLElement | null>(null)
const commentsRef = ref<HTMLElement | null>(null)

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

function buildBoardListRoute(boardUrl: string) {
  const { fromCreate, ...query } = route.query
  return {
    path: `/board/${boardUrl}`,
    query
  }
}

function handleTagClick(tag: string) {
  const boardUrl = post.value?.board.boardUrl
  if (!boardUrl) return

  router.push({
    path: `/board/${boardUrl}`,
    query: {
      q: tag,
      type: 'TAG'
    }
  })
}

function syncBoardListPageForDirectEntry() {
  const listPage = post.value?.boardListPage
  if (
    route.name !== 'post-detail'
    || typeof listPage !== 'number'
    || listPage <= 0
    || route.query.page
    || route.query.q
    || route.query.type
    || route.query.categoryId
    || route.query.concept
  ) {
    return
  }

  router.replace({
    path: route.path,
    hash: route.hash,
    query: {
      ...route.query,
      page: String(listPage + 1)
    }
  })
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

async function handleLike() {
  if (!authStore.isAuthenticated || !post.value) return

  if (postView.value?.liked) {
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

  if (postView.value?.scrapped) {
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

    showTemporaryCopyHint()
    ;(document.activeElement as HTMLElement | null)?.blur()
  }).catch((err) => {
    logger.error('Failed to copy URL:', err)
    toastStore.addToast(translateOrFallback('common.messages.processFailed', '二쇱냼 蹂듭궗???ㅽ뙣?덉뒿?덈떎.'), 'error')
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

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
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
  waitForImagesInContent().then(() => {
    if (isPostDetailUiDisposed()) return
    nextTick(() => scrollToComments())
  })
}

function goToList() {
  if (post.value?.board) {
    router.push(buildBoardListRoute(post.value.board.boardUrl))
    return
  }

  router.back()
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

    const element = document.querySelector(newHash)
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
}, { immediate: true })

onMounted(() => {
  markPostDetailUiMounted()
  nextTick(() => setupComposerObserver())

  document.addEventListener('keydown', handleKeyDown)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('resize', handleResize)

  disposePostDetailUiEffects()
})
</script>

<template>
  <div class="nv-post-shell">
    <BaseCard noPadding>
      <PostDetailSkeleton v-if="isLoading" />

      <div v-else-if="error" class="px-4 py-12 text-center text-sm text-red-500 sm:px-6">
        {{ error }}
        <div class="mt-4">
          <BaseButton @click="router.back()" variant="ghost" size="sm">
            {{ $t('common.back') }}
          </BaseButton>
        </div>
      </div>

      <template v-else-if="post && postView">
        <header class="nv-post-header px-4 py-4 sm:px-6 sm:py-5">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0 flex-1 space-y-4">
              <div class="flex items-center justify-between gap-2">
                <BaseButton
                  @click="router.push(buildBoardListRoute(postView.boardUrl))"
                  variant="ghost"
                  size="sm"
                  class="nv-post-back-btn"
                >
                  <ArrowLeft class="h-4 w-4" />
                  <span class="hidden sm:inline">{{ $t('board.postDetail.toList') }}</span>
                </BaseButton>

                <div
                  v-if="canEdit || canDelete"
                  class="flex min-w-0 items-center justify-end gap-2"
                >
                  <router-link
                    v-if="canEdit"
                    :to="buildEditRoute()"
                    class="nv-post-header-action"
                    :aria-label="$t('common.edit')"
                  >
                    <Pencil class="h-4 w-4" />
                    <span>{{ $t('common.edit') }}</span>
                  </router-link>
                  <button
                    v-if="canDelete"
                    type="button"
                    class="nv-post-header-action is-danger"
                    :aria-label="$t('common.delete')"
                    @click="handleDelete"
                  >
                    <Trash2 class="h-4 w-4" />
                    <span>{{ $t('common.delete') }}</span>
                  </button>
                </div>
              </div>

              <div class="space-y-3">
                <div class="nv-post-meta-strip">
                  <span>{{ postView.boardName }}</span>
                  <span>&middot;</span>
                  <span>{{ $t('common.post') }}</span>
                </div>
                <h1 class="text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-4xl">
                  {{ postView.title }}
                </h1>
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                  <span class="inline-flex items-center gap-1.5">
                    <User class="h-4 w-4" />
                    <UserMenu :user-id="postView.authorUserId" :display-name="postView.authorDisplayName" size="inherit" />
                    <span
                      v-if="isAgentAuthor"
                      class="rounded-full bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"
                    >
                      AGENT
                    </span>
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <Clock class="h-4 w-4" />
                    {{ formatDate(postView.createdAt) }}
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <Eye class="h-4 w-4" />
                    {{ postView.viewCount }}
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <MessageSquare class="h-4 w-4" />
                    {{ postView.commentCount }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </header>

        <div class="px-4 pt-5 lg:px-6">
          <article class="nv-post-content-stack min-w-0">
            <div class="nv-post-content-top">
              <Transition name="fade">
                <span
                  v-if="showCopyHint"
                  class="nv-post-copy-hint"
                >
                  {{ $t('common.messages.urlCopied') }}
                </span>
              </Transition>

              <button
                type="button"
                class="nv-post-url-chip"
                :title="currentUrl"
                :aria-label="$t('common.copy')"
                @click="onUrlBarClick"
                @keydown.enter="onUrlBarClick"
                @keydown.space.prevent="onUrlBarClick"
              >
                <Copy class="h-3.5 w-3.5 flex-shrink-0" />
                <span>{{ compactUrl }}</span>
              </button>
            </div>

            <div class="nv-post-article relative overflow-hidden">
              <div
                ref="contentRef"
                class="ql-editor nv-rich-content prose prose-sm max-w-none sm:prose-base dark:prose-invert"
                :class="{ 'blur-md select-none': isBlurred }"
                v-html="processedContents"
                @error.capture="applyImageFallback"
              ></div>

              <div v-if="isBlurred" class="nv-post-spoiler">
                <div class="nv-post-spoiler-card">
                  <h3 class="text-lg font-semibold text-[var(--nv-ink)]">
                    {{ $t('board.postDetail.spoilerWarning') }}
                  </h3>
                  <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
                    {{ $t('board.postDetail.spoilerTimer', { time: timeLeft }) }}
                  </p>
                  <div class="nv-post-spoiler-actions">
                    <BaseButton @click="revealSpoiler" size="sm">
                      {{ $t('board.postDetail.revealSpoiler') }}
                    </BaseButton>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-if="postView.tags.length > 0"
              class="nv-post-tags"
            >
              <p class="nv-post-section-label">{{ $t('board.postDetail.tags') }}</p>
              <PostTags
                :modelValue="postView.tags"
                :readOnly="true"
                :boardUrl="postView.boardUrl"
                compact
                @tag-click="handleTagClick"
              />
            </div>

            <div class="hidden sm:block">
              <div class="nv-post-reaction-row">
                <button
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle"
                  :class="{ 'is-active': postView.liked }"
                  :aria-label="$t('common.likes')"
                  :aria-pressed="postView.liked"
                  :title="$t('common.likes')"
                  :disabled="!authStore.isAuthenticated"
                  @click="handleLike"
                >
                  <ThumbsUp class="h-5 w-5" :class="{ 'fill-current bounce-in': isLikeAnimating }" />
                  <span class="nv-post-action-count">{{ postView.likeCount }}</span>
                </button>
                <button
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle"
                  :class="{ 'is-active is-bookmark': postView.scrapped }"
                  :aria-label="$t('board.postDetail.bookmark')"
                  :aria-pressed="postView.scrapped"
                  :title="$t('board.postDetail.bookmark')"
                  :disabled="!authStore.isAuthenticated"
                  @click="handleBookmark"
                >
                  <Bookmark class="h-5 w-5" :class="{ 'fill-current bounce-in': isBookmarkAnimating }" />
                </button>
                <button
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle"
                  :aria-label="$t('common.share')"
                  :title="$t('common.share')"
                  @click="handleShare"
                >
                  <Share2 class="h-5 w-5" />
                </button>
                <button
                  v-if="canReport"
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle is-report"
                  :aria-label="$t('common.report')"
                  :title="$t('common.report')"
                  @click="openReportModal"
                >
                  <AlertTriangle class="h-5 w-5" />
                </button>
              </div>
            </div>

            <section id="comments" ref="commentsRef" class="nv-post-comments">
              <CommentList :postId="postView.postId" :boardUrl="postView.boardUrl" />
            </section>
          </article>
        </div>
      </template>
    </BaseCard>

    <div v-if="postView" class="nv-post-board-actions hidden xl:flex" :aria-label="$t('board.postDetail.quickActions')">
      <button
        type="button"
        class="nv-post-board-action"
        :aria-label="$t('board.postDetail.comments')"
        :title="$t('board.postDetail.comments')"
        @click="scrollToComments"
      >
        <MessageSquare class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="nv-post-board-action"
        :aria-label="$t('board.postDetail.toList')"
        :title="$t('board.postDetail.toList')"
        @click="goToList"
      >
        <List class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="nv-post-board-action"
        :aria-label="$t('board.postDetail.scrollTop')"
        :title="$t('board.postDetail.scrollTop')"
        @click="scrollToTop"
      >
        <ArrowLeft class="h-4 w-4 rotate-90" />
      </button>
    </div>

    <Transition name="slide-up">
      <button
        v-if="showComposerCta"
        type="button"
        class="nv-post-mobile-comment-cta sm:hidden"
        @click="scrollToCommentComposer"
      >
        {{ $t('board.postDetail.focusComposer') }}
      </button>
    </Transition>

    <BaseModal :isOpen="showReportModal" :title="$t('common.report')" @close="showReportModal = false">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('report.target') }}
          </label>
          <div class="mt-1 text-sm font-medium text-gray-900 dark:text-white">
            {{ $t('common.post') }} | {{ postView?.title }}
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

.nv-post-meta-strip {
  align-items: center;
  color: var(--nv-muted);
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.nv-post-back-btn {
  gap: 0.375rem;
}

.nv-post-header-action {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink-soft);
  display: inline-flex;
  font-size: 0.85rem;
  font-weight: 600;
  gap: 0.35rem;
  min-height: 2.5rem;
  justify-content: center;
  padding: 0.55rem 0.8rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-header-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-header-action.is-danger {
  color: var(--nv-danger);
}

.nv-post-header-action.is-danger:hover {
  background: color-mix(in srgb, var(--nv-danger) 10%, var(--nv-surface));
  color: var(--nv-danger);
}

.nv-post-copy-hint {
  background: #ecfdf5;
  border-radius: 9999px;
  box-shadow: var(--nv-shadow-popup);
  color: #059669;
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.35rem 0.65rem;
  position: absolute;
  right: 0;
  top: calc(100% + 0.5rem);
  white-space: nowrap;
  z-index: 20;
}

.nv-post-content-top {
  display: flex;
  justify-content: flex-end;
  position: relative;
}

.nv-post-content-stack {
  display: grid;
  gap: 1.25rem;
}

.nv-post-url-chip {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 68%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  cursor: pointer;
  color: var(--nv-muted);
  display: inline-flex;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  gap: 0.35rem;
  letter-spacing: -0.02em;
  max-width: min(16rem, 42vw);
  min-width: 0;
  padding: 0.55rem 0.7rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-url-chip:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink-soft);
}

.nv-post-url-chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nv-post-comments {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 0;
  border-inline: 0;
  box-shadow: none;
  margin-inline: -1rem;
  width: calc(100% + 2rem);
}

.nv-post-comments :deep(> div:first-child) {
  margin-top: 0;
}

.nv-post-article {
  padding: 0.25rem 0;
}

.nv-post-comments {
  padding: 1.15rem 1rem;
}

.nv-post-tags {
  display: grid;
  gap: 0.55rem;
}

.nv-post-section-label {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.nv-post-reaction-row {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
  margin-top: 1rem;
}

.nv-post-spoiler {
  align-items: flex-start;
  background: color-mix(in srgb, var(--nv-surface) 45%, transparent);
  display: flex;
  inset: 0;
  justify-content: center;
  overflow-y: auto;
  padding: clamp(0.75rem, 4vh, 1.5rem);
  position: absolute;
}

.nv-post-spoiler-card {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.5rem;
  box-shadow: var(--nv-shadow-card);
  margin-block: auto;
  max-height: 100%;
  max-width: 22rem;
  overflow-y: auto;
  padding: clamp(1rem, 3vw, 1.5rem);
  text-align: center;
  width: 100%;
}

.nv-post-spoiler-actions {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}

.nv-post-action-btn,
.nv-post-board-action {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.1rem;
  color: var(--nv-ink-soft);
  display: inline-flex;
  gap: 0.55rem;
  justify-content: center;
  min-height: 3rem;
  min-width: 0;
  padding: 0.8rem 1rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-post-action-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nv-post-action-count {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 92%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink);
  display: inline-flex;
  font-size: 0.68rem;
  font-weight: 700;
  justify-content: center;
  min-width: 1.45rem;
  padding: 0.12rem 0.38rem;
  position: absolute;
  right: -0.35rem;
  top: -0.2rem;
}

.nv-post-action-btn:hover,
.nv-post-board-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-action-btn.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-post-action-btn.is-bookmark {
  color: #bb7a00;
}

.nv-post-action-btn.is-report {
  color: var(--nv-danger);
}

.nv-post-action-btn-circle {
  border-radius: 9999px;
  height: 3.1rem;
  justify-content: center;
  padding: 0;
  position: relative;
  width: 3.1rem;
}

.nv-post-board-actions {
  flex-direction: column;
  gap: 0.75rem;
  position: fixed;
  right: clamp(0.5rem, calc((100vw - 1120px) / 2 - 4.5rem), 3.5rem);
  top: 10rem;
  z-index: 25;
}

.nv-post-board-action {
  box-shadow: var(--nv-shadow-card);
  height: 2.9rem;
  justify-content: center;
  padding: 0;
  width: 2.9rem;
}

.nv-post-mobile-comment-cta {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  bottom: calc(1rem + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-ink);
  left: 50%;
  padding: 0.75rem 1rem;
  position: fixed;
  transform: translateX(-50%);
  z-index: 75;
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
  .nv-post-comments {
    padding: 1.5rem;
  }
}

@media (min-width: 1024px) {
  .nv-post-comments {
    margin-inline: -1.5rem;
    width: calc(100% + 3rem);
  }
}

@media (max-width: 639px) {
  .nv-post-url-chip {
    max-width: min(11rem, 48vw);
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

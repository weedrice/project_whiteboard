<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePost } from '@/composables/usePost'
import { useAuthStore } from '@/stores/auth'
import { User, Clock, ThumbsUp, MessageSquare, Eye, ArrowLeft, MoreHorizontal, Bookmark, AlertTriangle, Share2, Copy, ArrowUp, List } from 'lucide-vue-next'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostTags from '@/components/tag/PostTags.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { formatDate } from '@/utils/date'
import { sanitizeQuillHtml } from '@/utils/sanitize'
import { useHead } from '@unhead/vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const { usePostDetail, useDeletePost, useLikePost, useUnlikePost, useScrapPost, useUnscrapPost, useReportPost } = usePost()

const postId = computed(() => route.params.postId as string)
const { data: post, isLoading, error: postError } = usePostDetail(postId)

// SEO
useHead({
  title: computed(() => post.value?.title || 'Post'),
  meta: [
    {
      name: 'description', content: computed(() => {
        if (!post.value?.contents) return 'Post content'
        const text = post.value.contents.replace(/<[^>]*>/g, '').slice(0, 160)
        return text + (text.length >= 160 ? '...' : '')
      })
    },
    { property: 'og:title', content: computed(() => `${post.value?.title || 'Post'} | 노비스`) },
    {
      property: 'og:description', content: computed(() => {
        if (!post.value?.contents) return 'Post content'
        const text = post.value.contents.replace(/<[^>]*>/g, '').slice(0, 160)
        return text + (text.length >= 160 ? '...' : '')
      })
    },
    { property: 'og:type', content: 'article' }
  ]
})

const { mutate: deleteMutate } = useDeletePost()
const { mutate: likeMutate } = useLikePost()
const { mutate: unlikeMutate } = useUnlikePost()
const { mutate: scrapMutate } = useScrapPost()
const { mutate: unscrapMutate } = useUnscrapPost()
const { mutate: reportMutate } = useReportPost()

const error = computed(() => postError.value ? t('board.postDetail.loadFailed') : '')

// 조회수·최근 읽은 글은 usePostDetail의 getPost(incrementView: true) 한 번으로 처리됨. 별도 incrementView 호출 제거.

const isAuthor = computed(() => {
  return authStore.user && post.value && authStore.user.userId === post.value.author.userId
})

const processedContents = computed(() => {
  if (!post.value || !post.value.contents) return ''
  // Sanitize HTML to prevent XSS attacks
  const sanitized = sanitizeQuillHtml(post.value.contents)
  // Add loading="lazy" to all img tags that don't already have it
  return sanitized.replace(/<img(?![^>]*\bloading=)([^>]+)>/gi, '<img loading="lazy"$1>')
})

const isAdmin = computed(() => authStore.isAdmin)

const isBlurred = ref(false)
const blurTimer = ref<ReturnType<typeof setInterval> | null>(null)
const timeLeft = ref(5)

const isLikeAnimating = ref(false)
const likeAnimationTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const isScrapAnimating = ref(false)
const scrapAnimationTimer = ref<ReturnType<typeof setTimeout> | null>(null)

const titleRef = ref<HTMLElement | null>(null)

async function handleDelete() {
  const isConfirmed = await confirm(t('common.messages.confirmDelete'))
  if (!isConfirmed) return

  deleteMutate(route.params.postId as string | number, {
    onSuccess: () => {
      router.push(`/board/${post.value?.board.boardUrl}`)
    },
    onError: (err) => {
      logger.error('Failed to delete post:', err)
      toastStore.addToast(t('board.postDetail.deleteFailed'), 'error')
    }
  })
}

async function handleLike() {
  if (!authStore.isAuthenticated || !post.value) return
  if (post.value.liked) {
    unlikeMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
    })
  } else {
    // Optimistic animation
    isLikeAnimating.value = true
    // 이전 타이머 정리
    if (likeAnimationTimer.value) {
      clearTimeout(likeAnimationTimer.value)
    }
    likeAnimationTimer.value = setTimeout(() => {
      isLikeAnimating.value = false
      likeAnimationTimer.value = null
    }, 300)

    likeMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
    })
  }
}

async function handleScrap() {
  if (!authStore.isAuthenticated || !post.value) return
  if (post.value.scrapped) {
    unscrapMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
    })
  } else {
    // Optimistic animation
    isScrapAnimating.value = true
    // 이전 타이머 정리
    if (scrapAnimationTimer.value) {
      clearTimeout(scrapAnimationTimer.value)
    }
    scrapAnimationTimer.value = setTimeout(() => {
      isScrapAnimating.value = false
      scrapAnimationTimer.value = null
    }, 300)

    scrapMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
    })
  }
}

const showReportModal = ref(false)
const reportReason = ref('')

function handleReport() {
  if (!authStore.isAuthenticated) return
  showReportModal.value = true
  reportReason.value = ''
}
async function submitReport() {
  if (!reportReason.value.trim()) {
    toastStore.addToast(t('board.postDetail.reportReasonRequired'), 'error')
    return
  }

  reportMutate({ targetPostId: route.params.postId as string | number, reason: reportReason.value }, {
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

function startBlurTimer() {
  // 기존 타이머 정리
  if (blurTimer.value) {
    clearInterval(blurTimer.value)
  }
  blurTimer.value = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      revealSpoiler()
    }
  }, 1000)
}

function revealSpoiler() {
  isBlurred.value = false
  if (blurTimer.value) {
    clearInterval(blurTimer.value)
    blurTimer.value = null
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
    if (element) element.scrollIntoView({ behavior: 'smooth' })
  })
})

watch(post, (newPost, oldPost) => {
  if (newPost) {
    if (newPost.isSpoiler) {
      isBlurred.value = true
      timeLeft.value = 5
      startBlurTimer()
    }

    // Only scroll if it's a new post (different ID) or initial load
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
          if (element) element.scrollIntoView({ behavior: 'smooth' })
        }
      })
    }
  }
})

const currentUrl = computed(() => window.location.origin + route.fullPath)

const showCopyHint = ref(false)
let copyHintTimer: ReturnType<typeof setTimeout> | null = null

function handleCopyUrl(showToast = true) {
  navigator.clipboard.writeText(currentUrl.value).then(() => {
    if (showToast) {
      toastStore.addToast(t('common.messages.urlCopied'), 'success')
    } else {
      showCopyHint.value = true
      if (copyHintTimer) clearTimeout(copyHintTimer)
      copyHintTimer = setTimeout(() => {
        showCopyHint.value = false
        copyHintTimer = null
      }, 1500)
      ;(document.activeElement as HTMLElement)?.blur()
    }
  }).catch(err => {
    logger.error('Failed to copy URL:', err)
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
      url: currentUrl.value,
    }).catch(err => {
      if (err.name !== 'AbortError') logger.error('Share failed:', err)
    })
  } else {
    handleCopyUrl()
  }
}

const showFloatingNav = ref(false)
const commentsRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)

let observer: IntersectionObserver | null = null

function setupObserver() {
  if (observer) observer.disconnect()

  if (titleRef.value) {
    // 모바일에서는 플로팅 버튼을 더 일찍 표시 (rootMargin으로 감지 영역 축소)
    const isMobile = typeof window !== 'undefined' && window.innerWidth < 640
    const rootMargin = isMobile ? '0px 0px -45% 0px' : '0px'
    observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        showFloatingNav.value = !entry.isIntersecting
      })
    }, {
      threshold: 0,
      rootMargin
    })
    observer.observe(titleRef.value)
  }
}

watch(() => post.value, () => {
  nextTick(() => {
    setupObserver()
  })
})

// 모바일 리사이즈 시 플로팅 네비 감지 영역 갱신
function onResize() {
  if (titleRef.value && observer) {
    observer.disconnect()
    setupObserver()
  }
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/** 본문(contentRef) 내 이미지 로드 완료 후 resolve. lazy 이미지는 즉시 로드 시작하도록 트리거. */
function waitForImagesInContent(): Promise<void> {
  const container = contentRef.value
  if (!container) return Promise.resolve()

  const imgs = container.querySelectorAll<HTMLImageElement>('img')
  if (imgs.length === 0) return Promise.resolve()

  const IMAGE_LOAD_TIMEOUT_MS = 8000
  const promises = Array.from(imgs).map((img) => {
    if (img.complete) return Promise.resolve()
    if (img.loading === 'lazy') img.loading = 'eager'
    return Promise.race([
      new Promise<void>((resolve) => {
        img.onload = () => resolve()
        img.onerror = () => resolve()
      }),
      new Promise<void>((resolve) => setTimeout(resolve, IMAGE_LOAD_TIMEOUT_MS))
    ])
  })
  return Promise.all(promises).then(() => {})
}

function scrollToComments() {
  if (commentsRef.value) {
    const headerOffset = 100 // Adjust based on sticky header height if any
    const elementPosition = commentsRef.value.getBoundingClientRect().top
    const offsetPosition = elementPosition + window.pageYOffset - headerOffset

    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    })
  }
}

/** 이미지 로드까지 기다린 뒤 댓글 영역으로 스크롤 (전체 글 높이 확정 후 정확한 위치) */
function scrollToCommentsAfterImagesLoad() {
  waitForImagesInContent().then(() => {
    nextTick(() => scrollToComments())
  })
}

function goToList() {
  const listEl = document.getElementById('board-post-list')
  if (listEl) {
    listEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else if (post.value?.board) {
    router.push(`/board/${post.value.board.boardUrl}`)
  } else {
    router.back()
  }
}

// 입력 필드 확인
const isInputFocused = (): boolean => {
  const activeElement = document.activeElement
  if (!activeElement) return false
  const tagName = activeElement.tagName.toLowerCase()
  if (tagName === 'input' || tagName === 'textarea' || tagName === 'select') return true
  if (activeElement.getAttribute('contenteditable') === 'true') return true
  if (activeElement.closest('.ql-editor')) return true
  return false
}

// 키보드 단축키 핸들러
const handleKeyDown = (event: KeyboardEvent) => {
  const { key, shiftKey, ctrlKey, altKey, metaKey } = event

  if (ctrlKey || altKey || metaKey) return
  if (isInputFocused()) return

  // Shift 조합
  if (shiftKey) {
    if (key === 'S') {
      // 스크랩 (S와 충돌하므로 Shift 사용)
      if (authStore.isAuthenticated && post.value) {
        event.preventDefault()
        handleScrap()
      }
      return
    }
    if (key === 'Y') {
      // 공유
      event.preventDefault()
      handleShare()
      return
    }
    return
  }

  switch (key) {
    case 'c':
      // 댓글 영역으로 이동 (플로팅 네비 함수 재사용)
      event.preventDefault()
      scrollToComments()
      break

    case 'u':
      // 목록으로 이동 (플로팅 네비 함수 재사용)
      event.preventDefault()
      goToList()
      break

    case 'l':
      // 좋아요
      if (authStore.isAuthenticated && post.value) {
        event.preventDefault()
        handleLike()
      }
      break

    case 'y':
      // URL 복사
      event.preventDefault()
      handleCopyUrl()
      break

    case 'e':
      // 수정 (작성자만)
      if (isAuthor.value && post.value) {
        event.preventDefault()
        router.push(`/board/${post.value.board.boardUrl}/post/${post.value.postId}/edit`)
      }
      break

    case 'Escape':
      // 목록으로 이동
      event.preventDefault()
      goToList()
      break
  }
}

onMounted(() => {
  setupObserver()
  document.addEventListener('keydown', handleKeyDown)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  // Observer 정리
  if (observer) {
    observer.disconnect()
    observer = null
  }
  // 모든 타이머 정리
  if (blurTimer.value) {
    clearInterval(blurTimer.value)
    blurTimer.value = null
  }
  if (likeAnimationTimer.value) {
    clearTimeout(likeAnimationTimer.value)
    likeAnimationTimer.value = null
  }
  if (scrapAnimationTimer.value) {
    clearTimeout(scrapAnimationTimer.value)
    scrapAnimationTimer.value = null
  }
  if (copyHintTimer) {
    clearTimeout(copyHintTimer)
    copyHintTimer = null
  }
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <BaseCard noPadding>
    <div v-if="isLoading" class="px-3 py-4 sm:px-6 sm:py-5">
      <!-- Header Skeleton -->
      <div class="flex items-center justify-between mb-3 sm:mb-4">
        <BaseSkeleton width="80px" height="24px" />
        <div class="flex gap-1.5 sm:space-x-2">
          <BaseSkeleton width="60px" height="32px" />
          <BaseSkeleton width="60px" height="32px" />
        </div>
      </div>
      <div class="mt-3 sm:mt-4 space-y-2 sm:space-y-3">
        <BaseSkeleton width="70%" height="28px" />
        <div class="flex gap-3 sm:space-x-4">
          <BaseSkeleton width="100px" height="20px" />
          <BaseSkeleton width="120px" height="20px" />
          <BaseSkeleton width="60px" height="20px" />
        </div>
      </div>
      <div class="mt-6 sm:mt-8 space-y-3 sm:space-y-4">
        <BaseSkeleton width="100%" height="20px" />
        <BaseSkeleton width="100%" height="20px" />
        <BaseSkeleton width="90%" height="20px" />
        <BaseSkeleton width="95%" height="20px" />
        <BaseSkeleton width="80%" height="20px" />
      </div>
    </div>

    <div v-else-if="error" class="text-center py-8 sm:py-10 text-sm sm:text-base text-red-500 px-3">
      {{ error }}
      <div class="mt-3 sm:mt-4">
        <BaseButton @click="router.back()" variant="ghost" size="sm">
          {{ $t('common.back') }}
        </BaseButton>
      </div>
    </div>

    <div v-else-if="post">
      <!-- Header -->
      <div class="px-3 py-4 sm:px-6 sm:py-5 border-b border-gray-200 dark:border-gray-700">
        <div class="flex items-center justify-between gap-2">
          <BaseButton @click="router.push(`/board/${post.board.boardUrl}`)" variant="ghost" size="sm">
            <ArrowLeft class="hidden sm:inline-block h-4 w-4 mr-1" />
            {{ $t('board.postDetail.toList') }}
          </BaseButton>

          <div class="flex gap-1.5 sm:space-x-2">
            <router-link v-if="isAuthor" :to="`/board/${post.board.boardUrl}/post/${post.postId}/edit`"
              class="inline-flex items-center justify-center px-2 py-1.5 sm:px-3 sm:py-1.5 border border-gray-300 dark:border-gray-600 shadow-sm text-xs sm:text-sm font-medium rounded-md leading-4 text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200">
              {{ $t('common.edit') }}
            </router-link>
            <BaseButton v-if="isAuthor || isAdmin || post.board.isAdmin" @click="handleDelete" variant="danger"
              size="sm">
              {{ $t('common.delete') }}
            </BaseButton>
          </div>
        </div>

        <div class="mt-3 sm:mt-4">
          <h1 class="text-lg sm:text-2xl font-bold text-gray-900 dark:text-white break-words" ref="titleRef">{{ post.title }}</h1>
          <div class="mt-1.5 sm:mt-2 flex flex-wrap items-center text-xs sm:text-sm text-gray-500 dark:text-gray-400 gap-x-3 gap-y-1 sm:space-x-4 sm:gap-0">
            <div class="flex items-center text-inherit">
              <User class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 flex-shrink-0" />
              <UserMenu :user-id="post.author.userId" :display-name="post.author.displayName" size="inherit" />
            </div>
            <div class="flex items-center">
              <Clock class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 flex-shrink-0" />
              {{ formatDate(post.createdAt) }}
            </div>
            <div class="flex items-center">
              <Eye class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 flex-shrink-0" />
              {{ post.viewCount }}
            </div>
            <div class="flex items-center">
              <MessageSquare class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 flex-shrink-0" />
              {{ post.commentCount }}
            </div>
          </div>
        </div>
      </div>


      <!-- Content (ref for waiting images before scroll-to-comments) -->
      <div ref="contentRef"
        class="px-3 py-4 sm:px-6 sm:py-5 min-h-[160px] sm:min-h-[200px] prose prose-sm sm:prose-base dark:prose-invert max-w-none relative text-gray-900 dark:text-gray-100 text-sm sm:text-base">

        <!-- URL Copy (모바일: 주소 탭 시 복사 + 작은 메시지, 데스크톱: 복사 버튼) -->
        <div class="flex justify-end mb-3 sm:mb-4 not-prose relative">
          <Transition name="fade">
            <span v-if="showCopyHint"
              class="absolute right-0 -top-6 sm:hidden text-[10px] text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/30 px-2 py-1 rounded shadow-sm whitespace-nowrap z-10">
              {{ $t('common.messages.urlCopied') }}
            </span>
          </Transition>
          <div
            class="flex items-center gap-1 sm:space-x-2 bg-gray-100 dark:bg-gray-700 rounded-md px-2 py-1 sm:px-3 sm:py-1.5 max-w-full min-w-0 cursor-pointer sm:cursor-default active:bg-gray-200 dark:active:bg-gray-600 sm:active:bg-transparent transition-colors"
            role="button"
            tabindex="0"
            :aria-label="$t('common.messages.urlCopied')"
            @click="onUrlBarClick"
            @keydown.enter="onUrlBarClick"
            @keydown.space.prevent="onUrlBarClick"
          >
            <span class="text-[9px] sm:text-[10px] text-gray-500 dark:text-gray-400 select-all truncate">{{ currentUrl }}</span>
            <div class="hidden sm:block h-3 w-px bg-gray-300 dark:bg-gray-600 flex-shrink-0"></div>
            <BaseButton @click.stop="handleCopyUrl(true)" variant="ghost" size="sm" class="hidden sm:!inline-flex !px-2 !py-1 !text-[10px] sm:!text-xs flex-shrink-0">
              {{ $t('common.copy') }}
            </BaseButton>
          </div>
        </div>

        <div v-html="processedContents" class="ql-editor transition-all duration-500"
          :class="{ 'blur-md select-none': isBlurred }"></div>

        <!-- Spoiler Overlay -->
        <div v-if="isBlurred"
          class="absolute inset-0 flex flex-col items-center justify-center z-10 bg-white/50 dark:bg-black/50 px-3">
          <div
            class="bg-white dark:bg-gray-800 p-4 sm:p-6 rounded-lg shadow-lg text-center border border-gray-200 dark:border-gray-700 max-w-full flex flex-col items-center">
            <h3 class="text-base sm:text-lg font-bold text-gray-900 dark:text-white mb-1.5 sm:mb-2">{{ $t('board.postDetail.spoilerWarning') }}
            </h3>
            <p class="text-sm sm:text-base text-gray-600 dark:text-gray-300 mb-3 sm:mb-4">{{ $t('board.postDetail.spoilerTimer', { time: timeLeft })
            }}</p>
            <div class="flex justify-center w-full">
            <BaseButton @click="revealSpoiler" variant="primary" size="sm">
              {{ $t('board.postDetail.revealSpoiler') }}
            </BaseButton>
            </div>
          </div>
        </div>
      </div>

      <!-- Tags -->
      <div v-if="post.tags && post.tags.length > 0"
        class="px-3 py-3 sm:px-6 sm:py-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
        <PostTags :modelValue="post.tags" :readOnly="true" :boardUrl="post.board.boardUrl" />
      </div>

      <!-- Stats & Actions -->
      <div
        class="px-3 py-3 sm:px-6 sm:py-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex items-center justify-center gap-4 sm:gap-0 sm:space-x-8 transition-colors duration-200">
        <BaseButton @click="handleLike" :disabled="!authStore.isAuthenticated" variant="ghost"
          class="flex flex-col items-center group cursor-pointer h-auto py-1.5 sm:py-2"
          :class="{ 'text-indigo-600 dark:text-indigo-400': post.liked, 'text-gray-500 dark:text-gray-400': !post.liked, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated }">
          <div class="p-1.5 sm:p-2 rounded-full group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 transition-colors">
            <ThumbsUp class="h-5 w-5 sm:h-6 sm:w-6" :class="{ 'fill-current': post.liked, 'bounce-in': isLikeAnimating }" />
          </div>
          <span class="text-xs sm:text-sm font-medium mt-0.5 sm:mt-1">{{ post.likeCount }}</span>
        </BaseButton>

        <BaseButton @click="handleScrap" :disabled="!authStore.isAuthenticated" variant="ghost"
          class="flex flex-col items-center group cursor-pointer h-auto py-1.5 sm:py-2"
          :class="{ 'text-yellow-500': post.scrapped, 'text-gray-500 dark:text-gray-400': !post.scrapped, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated }">
          <div class="p-1.5 sm:p-2 rounded-full group-hover:bg-yellow-50 dark:group-hover:bg-yellow-900/30 transition-colors">
            <Bookmark class="h-5 w-5 sm:h-6 sm:w-6" :class="{ 'fill-current': post.scrapped, 'bounce-in': isScrapAnimating }" />
          </div>
          <span class="text-xs sm:text-sm font-medium mt-0.5 sm:mt-1">{{ $t('common.scrap') }}</span>
        </BaseButton>

        <BaseButton @click="handleShare" variant="ghost"
          class="flex flex-col items-center group cursor-pointer text-gray-500 dark:text-gray-400 h-auto py-1.5 sm:py-2">
          <div class="p-1.5 sm:p-2 rounded-full group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 transition-colors">
            <Share2 class="h-5 w-5 sm:h-6 sm:w-6" />
          </div>
          <span class="text-xs sm:text-sm font-medium mt-0.5 sm:mt-1">{{ $t('common.share') }}</span>
        </BaseButton>

        <BaseButton v-if="authStore.isAuthenticated && !isAuthor" @click="handleReport" variant="ghost"
          class="flex flex-col items-center group cursor-pointer text-gray-500 dark:text-gray-400 h-auto py-1.5 sm:py-2">
          <div class="p-1.5 sm:p-2 rounded-full group-hover:bg-red-50 dark:group-hover:bg-red-900/30 transition-colors">
            <AlertTriangle class="h-5 w-5 sm:h-6 sm:w-6" />
          </div>
          <span class="text-xs sm:text-sm font-medium mt-0.5 sm:mt-1">{{ $t('common.report') }}</span>
        </BaseButton>
      </div>

      <!-- Comments Section (id for hash scroll from feed comment button) -->
      <div id="comments" ref="commentsRef" class="border-t border-gray-200 dark:border-gray-700 mt-6 sm:mt-8 px-3 py-4 sm:p-4">
        <CommentList :postId="post.postId" :boardUrl="post.board.boardUrl" />
      </div>

      <!-- Floating Navigation -->
      <Transition name="slide-fade">
        <div v-show="showFloatingNav" class="fixed right-4 sm:right-8 top-1/2 -translate-y-1/2 flex flex-col gap-1.5 sm:gap-2 z-50">

          <button @click="scrollToTop"
            class="p-2 sm:p-3 bg-white dark:bg-gray-800 rounded-full shadow-lg border border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-gray-50 dark:hover:bg-gray-700 transition-all duration-200 group"
            :title="$t('common.top')">
            <ArrowUp class="w-4 h-4 sm:w-5 sm:h-5" />
          </button>

          <button @click="scrollToComments"
            class="p-2 sm:p-3 bg-white dark:bg-gray-800 rounded-full shadow-lg border border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-gray-50 dark:hover:bg-gray-700 transition-all duration-200 group"
            :title="$t('board.postDetail.comments')">
            <MessageSquare class="w-4 h-4 sm:w-5 sm:h-5" />
          </button>

          <button @click="goToList"
            class="p-2 sm:p-3 bg-white dark:bg-gray-800 rounded-full shadow-lg border border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-gray-50 dark:hover:bg-gray-700 transition-all duration-200 group"
            :title="$t('board.postDetail.toList')">
            <List class="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
        </div>
      </Transition>

      <!-- Report Modal -->
      <BaseModal :isOpen="showReportModal" :title="$t('common.report')" @close="showReportModal = false">
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('report.target') }}
            </label>
            <div class="mt-1 text-sm text-gray-900 dark:text-white font-medium">
              {{ $t('common.post') }} | {{ post.title }}
            </div>
          </div>
          <div>
            <BaseTextarea id="report-reason" v-model="reportReason" :label="$t('report.reason')" rows="4"
              :placeholder="$t('report.inputReason')" />
          </div>
        </div>
        <template #footer>
          <div class="flex justify-end gap-2 sm:space-x-3">
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
  </BaseCard>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
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

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(20px);
}
</style>

<!-- 본문 스타일: 리스트(ul/ol) + TipTap 비디오 embed -->
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

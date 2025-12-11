<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePost } from '@/composables/usePost'
import { useAuthStore } from '@/stores/auth'
import { User, Clock, ThumbsUp, MessageSquare, Eye, ArrowLeft, MoreHorizontal, Bookmark, AlertTriangle, Share2, Copy } from 'lucide-vue-next'
import BaseModal from '@/components/common/BaseModal.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostTags from '@/components/tag/PostTags.vue'
import UserMenu from '@/components/common/UserMenu.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const { usePostDetail, useDeletePost, useLikePost, useUnlikePost, useScrapPost, useUnscrapPost, useReportPost } = usePost()

const postId = computed(() => route.params.postId)
const { data: post, isLoading, error: postError } = usePostDetail(postId)

const { mutate: deleteMutate } = useDeletePost()
const { mutate: likeMutate } = useLikePost()
const { mutate: unlikeMutate } = useUnlikePost()
const { mutate: scrapMutate } = useScrapPost()
const { mutate: unscrapMutate } = useUnscrapPost()
const { mutate: reportMutate } = useReportPost()

const error = computed(() => postError.value ? t('board.postDetail.loadFailed') : '')

const isAuthor = computed(() => {
  return authStore.user && post.value && authStore.user.userId === post.value.author.userId
})

const isAdmin = computed(() => authStore.isAdmin)

function formatDate(dateString) {
  return new Date(dateString).toLocaleString()
}

const isBlurred = ref(false)
const blurTimer = ref(null)
const timeLeft = ref(5)

const titleRef = ref(null)

async function handleDelete() {
  if (!confirm(t('common.messages.confirmDelete'))) return

  deleteMutate(route.params.postId, {
    onSuccess: () => {
      router.push(`/board/${post.value.board.boardUrl}`)
    },
    onError: (err) => {
      logger.error('Failed to delete post:', err)
      alert(t('board.postDetail.deleteFailed'))
    }
  })
}

async function handleLike() {
  if (!authStore.isAuthenticated) return
  if (post.value.isLiked) {
    unlikeMutate(route.params.postId, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
    })
  } else {
    likeMutate(route.params.postId, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
    })
  }
}

async function handleScrap() {
  if (!authStore.isAuthenticated) return
  if (post.value.isScrapped) {
    unscrapMutate(route.params.postId, {
      onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
    })
  } else {
    scrapMutate(route.params.postId, {
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
    alert(t('board.postDetail.reportReasonRequired') || '신고 사유를 입력해주세요.')
    return
  }

  reportMutate({ targetPostId: route.params.postId, reason: reportReason.value }, {
    onSuccess: () => {
      alert(t('board.postDetail.reportSuccess') || '신고가 접수되었습니다.')
      showReportModal.value = false
    },
    onError: (err) => {
      logger.error('Report failed:', err)
      alert(t('board.postDetail.reportFailed') || '신고 접수에 실패했습니다.')
    }
  })
}

function startBlurTimer() {
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
  if (newHash) {
    nextTick(() => {
      const element = document.querySelector(newHash)
      if (element) {
        element.scrollIntoView({ behavior: 'smooth' })
      }
    })
  }
})

watch(post, (newPost) => {
  if (newPost) {
    if (newPost.isSpoiler) {
      isBlurred.value = true
      timeLeft.value = 5
      startBlurTimer()
    }
    nextTick(() => {
      window.scrollTo(0, 0)
      if (route.hash) {
        const element = document.querySelector(route.hash)
        if (element) {
          element.scrollIntoView({ behavior: 'smooth' })
        }
      } else if (titleRef.value) {
        titleRef.value.scrollIntoView({ behavior: 'smooth' })
      }
    })
  }
})

const currentUrl = computed(() => window.location.origin + route.fullPath)

function handleCopyUrl() {
  navigator.clipboard.writeText(currentUrl.value).then(() => {
    alert(t('common.messages.urlCopied'))
  }).catch(err => {
    logger.error('Failed to copy URL:', err)
  })
}

function handleShare() {
  if (navigator.share) {
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
</script>

<template>
  <div class="w-full bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
      <div class="mt-4">
        <button @click="router.back()"
          class="text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">
          {{ $t('common.back') }}
        </button>
      </div>
    </div>

    <div v-else-if="post">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700">
        <div class="flex items-center justify-between">
          <button @click="router.push(`/board/${post.board.boardUrl}`)"
            class="inline-flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200">
            <ArrowLeft class="h-4 w-4 mr-1" />
            {{ $t('board.postDetail.toList') }}
          </button>

          <div class="flex space-x-2">
            <router-link v-if="isAuthor" :to="`/board/${post.board.boardUrl}/post/${post.postId}/edit`"
              class="inline-flex items-center px-3 py-1.5 border border-gray-300 dark:border-gray-600 shadow-sm text-xs font-medium rounded-md text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200">
              {{ $t('common.edit') }}
            </router-link>
            <button v-if="isAuthor || isAdmin || post.board.isAdmin" @click="handleDelete"
              class="inline-flex items-center px-3 py-1.5 border border-transparent shadow-sm text-xs font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 cursor-pointer">
              {{ $t('common.delete') }}
            </button>
          </div>
        </div>

        <div class="mt-4">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-white" ref="titleRef">{{ post.title }}</h1>
          <div class="mt-2 flex items-center text-sm text-gray-500 dark:text-gray-400 space-x-4">
            <div class="flex items-center">
              <User class="h-4 w-4 mr-1" />
              <UserMenu :user-id="post.author.userId" :display-name="post.author.displayName" />
            </div>
            <div class="flex items-center">
              <Clock class="h-4 w-4 mr-1" />
              {{ formatDate(post.createdAt) }}
            </div>
            <div class="flex items-center">
              <Eye class="h-4 w-4 mr-1" />
              {{ post.viewCount }}
            </div>
            <div class="flex items-center">
              <MessageSquare class="h-4 w-4 mr-1" />
              {{ post.commentCount }}
            </div>
          </div>
        </div>
      </div>


      <!-- Content -->
      <div
        class="px-4 py-5 sm:p-6 min-h-[200px] prose dark:prose-invert max-w-none relative text-gray-900 dark:text-gray-100">

        <!-- URL Copy -->
        <div class="flex justify-end mb-4 not-prose">
          <div class="flex items-center space-x-2 bg-gray-100 dark:bg-gray-700 rounded-md px-3 py-1.5">
            <span class="text-[10px] text-gray-500 dark:text-gray-400 select-all">{{ currentUrl }}</span>
            <div class="h-3 w-px bg-gray-300 dark:bg-gray-600 mx-1"></div>
            <button @click="handleCopyUrl"
              class="text-xs font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300 focus:outline-none whitespace-nowrap">
              {{ $t('common.copy') }}
            </button>
          </div>
        </div>

        <div v-html="post.contents" class="ql-editor transition-all duration-500"
          :class="{ 'blur-md select-none': isBlurred }"></div>

        <!-- Spoiler Overlay -->
        <div v-if="isBlurred"
          class="absolute inset-0 flex flex-col items-center justify-center z-10 bg-white/50 dark:bg-black/50">
          <div
            class="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-lg text-center border border-gray-200 dark:border-gray-700">
            <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-2">{{ $t('board.postDetail.spoilerWarning') }}
            </h3>
            <p class="text-gray-600 dark:text-gray-300 mb-4">{{ $t('board.postDetail.spoilerTimer', { time: timeLeft })
            }}</p>
            <button @click="revealSpoiler"
              class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
              {{ $t('board.postDetail.revealSpoiler') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Tags -->
      <div v-if="post.tags && post.tags.length > 0"
        class="px-4 py-4 sm:px-6 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
        <PostTags :modelValue="post.tags" :readOnly="true" :boardUrl="post.board.boardUrl" />
      </div>

      <!-- Stats & Actions -->
      <div
        class="px-4 py-4 sm:px-6 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex items-center justify-center space-x-8 transition-colors duration-200">
        <button @click="handleLike" :disabled="!authStore.isAuthenticated"
          class="flex flex-col items-center group cursor-pointer"
          :class="{ 'text-indigo-600 dark:text-indigo-400': post.isLiked, 'text-gray-500 dark:text-gray-400': !post.isLiked, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated }">
          <div class="p-2 rounded-full group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 transition-colors">
            <ThumbsUp class="h-6 w-6" :class="{ 'fill-current': post.isLiked }" />
          </div>
          <span class="text-sm font-medium mt-1">{{ post.likeCount }}</span>
        </button>

        <button @click="handleScrap" :disabled="!authStore.isAuthenticated"
          class="flex flex-col items-center group cursor-pointer"
          :class="{ 'text-yellow-500': post.isScrapped, 'text-gray-500 dark:text-gray-400': !post.isScrapped, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated }">
          <div class="p-2 rounded-full group-hover:bg-yellow-50 dark:group-hover:bg-yellow-900/30 transition-colors">
            <Bookmark class="h-6 w-6" :class="{ 'fill-current': post.isScrapped }" />
          </div>
          <span class="text-sm font-medium mt-1">{{ $t('common.scrap') }}</span>
        </button>

        <button @click="handleShare"
          class="flex flex-col items-center group cursor-pointer text-gray-500 dark:text-gray-400">
          <div class="p-2 rounded-full group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 transition-colors">
            <Share2 class="h-6 w-6" />
          </div>
          <span class="text-sm font-medium mt-1">{{ $t('common.share') }}</span>
        </button>

        <button v-if="authStore.isAuthenticated && !isAuthor" @click="handleReport"
          class="flex flex-col items-center group cursor-pointer text-gray-500 dark:text-gray-400">
          <div class="p-2 rounded-full group-hover:bg-red-50 dark:group-hover:bg-red-900/30 transition-colors">
            <AlertTriangle class="h-6 w-6" />
          </div>
          <span class="text-sm font-medium mt-1">{{ $t('common.report') || 'Report' }}</span>
        </button>
      </div>

      <!-- Comments Section -->
      <div class="border-t border-gray-200 dark:border-gray-700 mt-8 p-4">
        <CommentList :postId="post.postId" :boardUrl="post.board.boardUrl" />
      </div>

      <!-- Report Modal -->
      <BaseModal :isOpen="showReportModal" :title="$t('common.report') || '신고'" @close="showReportModal = false">
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('report.target') || '신고 대상' }}
            </label>
            <div class="mt-1 text-sm text-gray-900 dark:text-white font-medium">
              {{ $t('common.post') || '게시글' }} | {{ post.title }}
            </div>
          </div>
          <div>
            <label for="report-reason" class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('report.reason') || '신고 사유' }}
            </label>
            <textarea id="report-reason" v-model="reportReason" rows="4"
              class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm dark:bg-gray-700 dark:border-gray-600 dark:text-white dark:placeholder-gray-400 p-2 border"
              :placeholder="$t('report.inputReason') || '신고 사유를 상세히 입력해주세요.'"></textarea>
          </div>
        </div>
        <template #footer>
          <div class="flex justify-end space-x-3">
            <button @click="showReportModal = false"
              class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 dark:bg-gray-700 dark:text-gray-300 dark:border-gray-600 dark:hover:bg-gray-600">
              {{ $t('common.cancel') || '취소' }}
            </button>
            <button @click="submitReport"
              class="px-4 py-2 text-sm font-medium text-white bg-red-600 border border-transparent rounded-md hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500">
              {{ $t('common.submit') || '제출' }}
            </button>
          </div>
        </template>
      </BaseModal>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePost } from '@/composables/usePost'
import { useAuthStore } from '@/stores/auth'
import { User, Clock, ThumbsUp, MessageSquare, Eye, ArrowLeft, MoreHorizontal, Bookmark, AlertTriangle, Share2, Copy } from 'lucide-vue-next'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostTags from '@/components/tag/PostTags.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

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

import { formatDate } from '@/utils/date'

const isAuthor = computed(() => {
  return authStore.user && post.value && authStore.user.userId === post.value.author.userId
})

const isAdmin = computed(() => authStore.isAdmin)

const isBlurred = ref(false)
const blurTimer = ref(null)
const timeLeft = ref(5)

const titleRef = ref(null)

async function handleDelete() {
  const isConfirmed = await confirm(t('common.messages.confirmDelete'))
  if (!isConfirmed) return

  deleteMutate(route.params.postId, {
    onSuccess: () => {
      router.push(`/board/${post.value.board.boardUrl}`)
    },
    onError: (err) => {
      logger.error('Failed to delete post:', err)
      toastStore.addToast(t('board.postDetail.deleteFailed'), 'error')
    }
  })
}

async function handleLike() {
  if (!authStore.isAuthenticated) return
  if (post.value.liked) {
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
  if (post.value.scrapped) {
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
    toastStore.addToast(t('board.postDetail.reportReasonRequired'), 'error')
    return
  }

  reportMutate({ targetPostId: route.params.postId, reason: reportReason.value }, {
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
  }
})

const currentUrl = computed(() => window.location.origin + route.fullPath)

function handleCopyUrl() {
  navigator.clipboard.writeText(currentUrl.value).then(() => {
    toastStore.addToast(t('common.messages.urlCopied'), 'success')
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
  <BaseCard noPadding>
    <div v-if="isLoading" class="text-center py-10">
      <BaseSpinner size="lg" />
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
      <div class="mt-4">
        <BaseButton @click="router.back()" variant="ghost">
          {{ $t('common.back') }}
        </BaseButton>
      </div>
    </div>

    <div v-else-if="post">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700">
        <div class="flex items-center justify-between">
          <BaseButton @click="router.push(`/board/${post.board.boardUrl}`)" variant="ghost" size="sm">
            <ArrowLeft class="h-4 w-4 mr-1" />
            {{ $t('board.postDetail.toList') }}
          </BaseButton>

          <div class="flex space-x-2">
            <router-link v-if="isAuthor" :to="`/board/${post.board.boardUrl}/post/${post.postId}/edit`"
              class="inline-flex items-center px-3 py-1.5 border border-gray-300 dark:border-gray-600 shadow-sm text-xs font-medium rounded-md text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200">
              {{ $t('common.edit') }}
            </router-link>
            <BaseButton v-if="isAuthor || isAdmin || post.board.isAdmin" @click="handleDelete" variant="danger"
              size="sm">
              {{ $t('common.delete') }}
            </BaseButton>
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
            <BaseButton @click="handleCopyUrl" variant="ghost" size="sm" class="text-xs">
              {{ $t('common.copy') }}
            </BaseButton>
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
            <BaseButton @click="revealSpoiler" variant="primary">
              {{ $t('board.postDetail.revealSpoiler') }}
            </BaseButton>
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
        <BaseButton @click="handleLike" :disabled="!authStore.isAuthenticated" variant="ghost"
          class="flex flex-col items-center group cursor-pointer h-auto py-2"
          :class="{ 'text-indigo-600 dark:text-indigo-400': post.liked, 'text-gray-500 dark:text-gray-400': !post.liked, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated }">
          <div class="p-2 rounded-full group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 transition-colors">
            <ThumbsUp class="h-6 w-6" :class="{ 'fill-current': post.liked }" />
          </div>
          <span class="text-sm font-medium mt-1">{{ post.likeCount }}</span>
        </BaseButton>

        <BaseButton @click="handleScrap" :disabled="!authStore.isAuthenticated" variant="ghost"
          class="flex flex-col items-center group cursor-pointer h-auto py-2"
          :class="{ 'text-yellow-500': post.scrapped, 'text-gray-500 dark:text-gray-400': !post.scrapped, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated }">
          <div class="p-2 rounded-full group-hover:bg-yellow-50 dark:group-hover:bg-yellow-900/30 transition-colors">
            <Bookmark class="h-6 w-6" :class="{ 'fill-current': post.scrapped }" />
          </div>
          <span class="text-sm font-medium mt-1">{{ $t('common.scrap') }}</span>
        </BaseButton>

        <BaseButton @click="handleShare" variant="ghost"
          class="flex flex-col items-center group cursor-pointer text-gray-500 dark:text-gray-400 h-auto py-2">
          <div class="p-2 rounded-full group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 transition-colors">
            <Share2 class="h-6 w-6" />
          </div>
          <span class="text-sm font-medium mt-1">{{ $t('common.share') }}</span>
        </BaseButton>

        <BaseButton v-if="authStore.isAuthenticated && !isAuthor" @click="handleReport" variant="ghost"
          class="flex flex-col items-center group cursor-pointer text-gray-500 dark:text-gray-400 h-auto py-2">
          <div class="p-2 rounded-full group-hover:bg-red-50 dark:group-hover:bg-red-900/30 transition-colors">
            <AlertTriangle class="h-6 w-6" />
          </div>
          <span class="text-sm font-medium mt-1">{{ $t('common.report') }}</span>
        </BaseButton>
      </div>

      <!-- Comments Section -->
      <div class="border-t border-gray-200 dark:border-gray-700 mt-8 p-4">
        <CommentList :postId="post.postId" :boardUrl="post.board.boardUrl" />
      </div>

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
          <div class="flex justify-end space-x-3">
            <BaseButton @click="showReportModal = false" variant="secondary">
              {{ $t('common.cancel') }}
            </BaseButton>
            <BaseButton @click="submitReport" variant="danger">
              {{ $t('common.submit') }}
            </BaseButton>
          </div>
        </template>
      </BaseModal>
    </div>
  </BaseCard>
</template>

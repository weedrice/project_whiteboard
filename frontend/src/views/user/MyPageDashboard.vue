<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { userApi } from '@/api/user'
import { postApi } from '@/api/post'
import { User, Mail, Calendar, FileText, CheckCircle, XCircle, Clock, MessageSquare, ShieldCheck } from 'lucide-vue-next'
import { authApi } from '@/api/auth'
import PostList from '@/components/board/PostList.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import CommentList from '@/components/comment/CommentList.vue'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useToastStore } from '@/stores/toast'
import { getOptimizedProfileImageUrl, handleImageError } from '@/utils/image'
import { extractErrorMessage } from '@/utils/errorHandler'
import { formatDate } from '@/utils/date'
import { isValidEmail } from '@/utils/validation'
import { renderCommentContentHtml } from '@/utils/commentContent'
import type { User as UserType, PostSummary, Post, Comment } from '@/types'
import type { UserAgent } from '@/api/user'

const { t } = useI18n()
const { handleSilentError } = useErrorHandler()
const toastStore = useToastStore()

// Comment list uses a dedicated emoticon rendering class.
function renderCommentContent(content: string | undefined): string {
  return renderCommentContentHtml(content, 'comment-emoticon comment-emoticon-list')
}

const profile = ref<UserType | null>(null)
const myAgents = ref<UserAgent[]>([])

// Posts pagination state
const myPosts = ref<PostSummary[]>([])
const myPostsTotalCount = ref(0)
const myPostsCurrentPage = ref(0)
const myPostsSize = ref(10) // 10 items per page
const myPostsSort = ref('createdAt,desc')
const isInquiryDetailOpen = ref(false)
const selectedInquiryPost = ref<Post | null>(null)
const isInquiryDetailLoading = ref(false)
const inquiryDetailError = ref('')
const isDeletingInquiry = ref(false)

// Comments pagination state
const myComments = ref<Comment[]>([])
const myCommentsTotalCount = ref(0)
const myCommentsCurrentPage = ref(0)
const myCommentsSize = ref(10) // 10 items per page

const isLoading = ref(true)
const error = ref<string | null>(null)
const isEditModalOpen = ref(false)



async function fetchMyProfile() {
  try {
    const { data } = await userApi.getMyProfile()
    if (data.success) {
      profile.value = data.data
    }
  } catch (err: unknown) {
    handleSilentError(err, 'Failed to load my profile')
  }
}

async function fetchMyPosts() {
  try {
    const { data } = await userApi.getMyPosts({
      page: myPostsCurrentPage.value,
      size: myPostsSize.value,
      sort: myPostsSort.value
    })
    if (data.success) {
      myPosts.value = data.data.content
      myPostsTotalCount.value = data.data.totalElements
    }
  } catch (err: unknown) {
    handleSilentError(err, 'Failed to load my posts')
  }
}

async function fetchMyComments() {
  try {
    const { data } = await userApi.getMyComments({ page: myCommentsCurrentPage.value, size: myCommentsSize.value })
    if (data.success) {
      myComments.value = data.data.content
      myCommentsTotalCount.value = data.data.totalElements
    }
  } catch (err: unknown) {
    handleSilentError(err, 'Failed to load my comments')
  }
}

function handleMyPostsPageChange(page: number) {
  myPostsCurrentPage.value = page
  fetchMyPosts()
}

function handleMyPostsSortChange(newSort: string) {
  myPostsSort.value = newSort
  fetchMyPosts()
}

function isInquiryPostItem(post: { boardUrl?: string | number }): boolean {
  return String(post.boardUrl || '').toLowerCase() === 'inquiry'
}

async function openMyInquiryPost(post: { postId: number; boardUrl?: string | number }) {
  if (!isInquiryPostItem(post)) {
    return
  }

  isInquiryDetailOpen.value = true
  selectedInquiryPost.value = null
  inquiryDetailError.value = ''
  isInquiryDetailLoading.value = true

  try {
    const { data } = await postApi.getPost(post.postId, { params: { incrementView: false } })
    if (data.success) {
      selectedInquiryPost.value = data.data
    }
  } catch (err: unknown) {
    inquiryDetailError.value = extractErrorMessage(err) || t('common.messages.loadFailed')
  } finally {
    isInquiryDetailLoading.value = false
  }
}

async function fetchMyAgents() {
  try {
    const { data } = await userApi.getMyAgents()
    if (data.success) {
      myAgents.value = data.data.agents
    }
  } catch (err: unknown) {
    handleSilentError(err, 'Failed to load my agents')
  }
}

function closeInquiryModal() {
  isInquiryDetailOpen.value = false
  selectedInquiryPost.value = null
  inquiryDetailError.value = ''
  fetchMyPosts()
}

async function deleteInquiryPost() {
  const post = selectedInquiryPost.value
  if (!post) return
  if (!window.confirm('\uBB38\uC758\uB97C \uC0AD\uC81C\uD558\uC2DC\uACA0\uC2B5\uB2C8\uAE4C?')) {
    return
  }

  isDeletingInquiry.value = true
  try {
    await postApi.deletePost(post.postId)
    toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
    closeInquiryModal()
    await fetchMyPosts()
  } catch (err: unknown) {
    toastStore.addToast(extractErrorMessage(err) || t('common.messages.deleteFailed'), 'error')
  } finally {
    isDeletingInquiry.value = false
  }
}

function handleMyCommentsPageChange(page: number) {
  myCommentsCurrentPage.value = page
  fetchMyComments()
}
function getAgentSummary() {
  if (myAgents.value.length === 0) {
    return '\uB4F1\uB85D\uB41C \uC5D0\uC774\uC804\uD2B8\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.'
  }

  const activeAgents = myAgents.value.filter((agent) => agent.status === 'ACTIVE')
  if (activeAgents.length === 1) {
    return `${activeAgents[0].name} \uC5F0\uACB0\uB428`
  }
  if (activeAgents.length > 1) {
    return `\uD65C\uC131 \uC5D0\uC774\uC804\uD2B8 ${activeAgents.length}\uAC1C \uC5F0\uACB0\uB428`
  }
  return `\uC5D0\uC774\uC804\uD2B8 ${myAgents.value.length}\uAC1C \uB4F1\uB85D\uB428`
}

function getAgentStatusLabel(status: UserAgent['status']) {
  if (status === 'ACTIVE') return '\uD65C\uC131'
  if (status === 'SUSPENDED') return '\uC815\uC9C0'
  return '\uB300\uAE30'
}

// Email Verification
const isVerifyModalOpen = ref(false)
const emailVerification = reactive({
  email: '',
  code: '',
  isCodeSent: false,
  isVerified: false,
  loading: false,
  timeLeft: 0,
  resendCooldown: 0
})
let verifyTimerInterval: ReturnType<typeof setInterval> | null = null
let verifyResendInterval: ReturnType<typeof setInterval> | null = null

function openVerifyModal() {
  emailVerification.email = profile.value?.email || ''
  emailVerification.code = ''
  emailVerification.isCodeSent = false
  emailVerification.isVerified = false
  emailVerification.loading = false
  emailVerification.timeLeft = 0
  emailVerification.resendCooldown = 0
  stopVerifyTimer()
  stopVerifyResendCooldown()
  isVerifyModalOpen.value = true
}

function startVerifyTimer() {
  stopVerifyTimer()
  emailVerification.timeLeft = 300
  verifyTimerInterval = setInterval(() => {
    if (emailVerification.timeLeft > 0) {
      emailVerification.timeLeft--
    } else {
      stopVerifyTimer()
    }
  }, 1000)
}

function stopVerifyTimer() {
  if (verifyTimerInterval) {
    clearInterval(verifyTimerInterval)
    verifyTimerInterval = null
  }
}

function startVerifyResendCooldown() {
  stopVerifyResendCooldown()
  emailVerification.resendCooldown = 60
  verifyResendInterval = setInterval(() => {
    if (emailVerification.resendCooldown > 0) {
      emailVerification.resendCooldown--
    } else {
      stopVerifyResendCooldown()
    }
  }, 1000)
}

function stopVerifyResendCooldown() {
  if (verifyResendInterval) {
    clearInterval(verifyResendInterval)
    verifyResendInterval = null
  }
}

function formatVerifyTime(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}



async function sendVerifyCode() {
  const trimmed = emailVerification.email.trim()
  if (!trimmed) {
    toastStore.addToast(t('auth.emailRequired'), 'error')
    return
  }
  if (!isValidEmail(trimmed)) {
    toastStore.addToast(t('auth.validation.emailFormat'), 'error')
    return
  }

  emailVerification.loading = true
  try {
    const { data } = await authApi.sendVerificationCode(emailVerification.email)
    if (data.success) {
      emailVerification.isCodeSent = true
      startVerifyTimer()
      startVerifyResendCooldown()
      toastStore.addToast(t('auth.codeSent'), 'success')
    }
  } catch (err: unknown) {
    const message = extractErrorMessage(err) || t('auth.sendCodeFailed')
    toastStore.addToast(message, 'error')
    emailVerification.resendCooldown = 0
    stopVerifyResendCooldown()
  } finally {
    emailVerification.loading = false
  }
}

async function verifyEmailCode() {
  if (!emailVerification.code || !emailVerification.email) return

  if (emailVerification.timeLeft <= 0) {
    toastStore.addToast(t('auth.codeExpired'), 'error')
    return
  }

  emailVerification.loading = true
  try {
    const { data } = await userApi.verifyEmail({
      email: emailVerification.email,
      code: emailVerification.code
    })
    if (data.success) {
      emailVerification.isVerified = true
      stopVerifyTimer()
      toastStore.addToast(t('auth.codeVerified'), 'success')
      // ????썹땟怨⒲뀋????썹땟?ⓦ궘?????⑤베鍮????곗뵯???????쇰젩.
      await fetchMyProfile()
      isVerifyModalOpen.value = false
    }
  } catch (err: unknown) {
    const message = extractErrorMessage(err) || t('auth.verificationFailed')
    toastStore.addToast(message, 'error')
  } finally {
    emailVerification.loading = false
  }
}

onMounted(async () => {
  isLoading.value = true
  await Promise.all([
    fetchMyProfile(),
    fetchMyAgents(),
    fetchMyPosts(),
    fetchMyComments()
  ])
  isLoading.value = false
})

onUnmounted(() => {
  stopVerifyTimer()
  stopVerifyResendCooldown()
})
</script>

<template>
  <div>
    <div v-if="isLoading" class="space-y-6">
      <!-- Profile Skeleton -->
      <div class="max-w-full mx-auto bg-white dark:bg-gray-800 shadow rounded-lg p-6">
        <div class="flex items-center mb-6">
          <BaseSkeleton width="4rem" height="4rem" rounded="rounded-full" className="mr-4" />
          <div class="flex-1">
            <BaseSkeleton width="150px" height="24px" className="mb-2" />
            <BaseSkeleton width="200px" height="16px" />
          </div>
        </div>
        <div class="space-y-4">
          <BaseSkeleton v-for="i in 4" :key="i" width="100%" height="40px" />
        </div>
      </div>
      <!-- Posts Skeleton -->
      <div class="max-w-full mx-auto bg-white dark:bg-gray-800 shadow rounded-lg p-6">
        <BaseSkeleton width="120px" height="24px" className="mb-4" />
        <div class="space-y-4">
          <BaseSkeleton v-for="i in 3" :key="i" width="100%" height="60px" />
        </div>
      </div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500 dark:text-red-400">
      {{ error }}
    </div>

    <div v-else>
      <!-- Profile Section -->
      <div class="max-w-full mx-auto">
        <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg mb-6 transition-colors duration-200">
          <div class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-4">
            <div class="flex items-center min-w-0 flex-1">
              <img v-if="profile?.profileImageUrl" :src="getOptimizedProfileImageUrl(profile.profileImageUrl)"
                class="h-16 w-16 rounded-full mr-4 flex-shrink-0" alt="Profile" @error="handleImageError($event)" />
              <div v-else
                class="h-16 w-16 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold text-2xl mr-4 flex-shrink-0">
                {{ profile?.displayName?.[0] || 'U' }}
              </div>
              <div class="min-w-0">
                <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white truncate">{{ profile?.displayName
                }}</h3>
                <p class="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">{{ $t('user.profile.personalDetails')
                }}</p>
              </div>
            </div>
            <BaseButton @click="isEditModalOpen = true"
              class="w-full sm:w-auto min-h-[44px] sm:min-h-0 flex items-center justify-center">{{
                $t('user.profile.edit') }}</BaseButton>
          </div>
          <div class="border-t border-gray-200 dark:border-gray-700 px-4 py-5 sm:p-0">
            <dl class="sm:divide-y sm:divide-gray-200 dark:sm:divide-gray-700">
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center whitespace-nowrap">
                  <User class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.displayName') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0">{{ profile?.displayName
                }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center whitespace-nowrap">
                  <Mail class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.email') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0">
                  {{ profile?.email }}
                  <span v-if="profile?.isEmailVerified"
                    class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400">
                    <CheckCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.verified') }}
                  </span>
                  <button v-else @click="openVerifyModal" type="button"
                    class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-400 hover:bg-red-200 dark:hover:bg-red-800/50 transition-colors cursor-pointer">
                    <XCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.notVerified') }}
                  </button>
                </dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center whitespace-nowrap">
                  <Calendar class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.joined') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0">{{
                  profile?.createdAt ? formatDate(profile.createdAt) : '' }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center whitespace-nowrap">
                  <Clock class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.lastLogin') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0">{{
                  profile?.lastLoginAt ? formatDate(profile.lastLoginAt) : '' }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center whitespace-nowrap">
                  <ShieldCheck class="h-4 w-4 mr-1.5" />
                  {{ '\uC5D0\uC774\uC804\uD2B8 \uCF54\uB4DC' }}
                </dt>
                <dd class="mt-1 sm:mt-0">
                  <div v-if="myAgents.length > 0" class="flex flex-wrap gap-2">
                    <span
                      v-for="agent in myAgents"
                      :key="agent.agentId"
                      class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium"
                      :class="agent.status === 'ACTIVE'
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300'
                        : agent.status === 'SUSPENDED'
                          ? 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300'
                          : 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'"
                    >
                      <span>{{ agent.name }}</span>
                      <span>{{ getAgentStatusLabel(agent.status) }}</span>
                    </span>
                  </div>
                  <span v-else class="text-sm text-gray-500 dark:text-gray-400">
                    {{ '\uB4F1\uB85D\uB41C \uC5D0\uC774\uC804\uD2B8 \uC5C6\uC74C' }}
                  </span>
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </div>

      <!-- My Posts Section -->
      <div class="max-w-full mx-auto">
        <div
          class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg mb-6 pb-6 transition-colors duration-200">
          <div class="px-4 py-4 sm:py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
            <FileText class="h-5 w-5 text-gray-500 dark:text-gray-400 mr-2 flex-shrink-0" />
            <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('user.myPosts') }}</h3>
          </div>

          <div v-if="myPosts.length > 0">
            <PostList :posts="myPosts" :totalCount="myPostsTotalCount" :page="myPostsCurrentPage" :size="myPostsSize"
              :current-sort="myPostsSort" :show-board-name="true" :intercept-inquiry="true"
              @update:sort="handleMyPostsSortChange" @inquiry-click="openMyInquiryPost" />
            <div class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
              <Pagination :current-page="myPostsCurrentPage" :total-pages="Math.ceil(myPostsTotalCount / myPostsSize)"
                @page-change="handleMyPostsPageChange" />
            </div>
          </div>
          <EmptyState v-else :title="$t('common.noData')" :icon="FileText" />
        </div>

        <!-- My Comments Section -->
        <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
          <div class="px-4 py-4 sm:py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
            <MessageSquare class="h-5 w-5 text-gray-500 dark:text-gray-400 mr-2 flex-shrink-0" />
            <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('user.myComments') }}</h3>
          </div>

          <div v-if="myComments.length > 0">
            <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
              <li v-for="comment in myComments" :key="comment.commentId"
                class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200 min-h-[44px]">
                <router-link v-if="comment.post" :to="`/board/${comment.post.boardUrl}/post/${comment.post.postId}`"
                  class="block">
                  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
                    <div class="flex flex-wrap items-center gap-2 min-w-0">
                      <span
                        class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200 flex-shrink-0">
                        {{ comment.post.boardName }}
                      </span>
                      <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate min-w-0">
                        {{ comment.post.title }}
                      </p>
                    </div>
                    <p class="flex-shrink-0 font-normal text-gray-500 dark:text-gray-400 text-xs">{{
                      formatDate(comment.createdAt) }}</p>
                  </div>
                  <div class="mt-1 comment-content-list">
                    <p class="text-sm text-gray-900 dark:text-gray-300 line-clamp-2"
                      v-html="renderCommentContent(comment.content)"></p>
                  </div>
                </router-link>
                <div v-else class="block">
                  <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('user.comments.deletedPost') }}</p>
                  <div class="comment-content-list mt-1">
                    <p class="text-sm text-gray-900 dark:text-gray-300 line-clamp-2"
                      v-html="renderCommentContent(comment.content)"></p>
                  </div>
                </div>
              </li>
            </ul>
            <div class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
              <Pagination :current-page="myCommentsCurrentPage"
                :total-pages="Math.ceil(myCommentsTotalCount / myCommentsSize)"
                @page-change="handleMyCommentsPageChange" />
            </div>
          </div>
          <EmptyState v-else :title="t('common.noData')" :icon="MessageSquare" />
        </div>
      </div>

      <BaseModal :isOpen="isEditModalOpen" :title="$t('user.profile.edit')" @close="isEditModalOpen = false" mobile-full
        mobile-fit-content>
        <ProfileEditor @close="isEditModalOpen = false" @refreshed="() => { fetchMyProfile(); fetchMyAgents() }" />
      </BaseModal>

      <!-- Email Verification Modal -->
      <BaseModal :isOpen="isVerifyModalOpen" :title="$t('auth.emailNotVerified')" @close="isVerifyModalOpen = false">
        <div class="space-y-6 p-4">
          <!-- Email Input & Send Button -->
          <div class="flex flex-col">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              {{ t('user.profile.email') }}
            </label>
            <div class="flex gap-2 items-end">
              <div class="flex-1 min-w-0">
                <BaseInput v-model="emailVerification.email" :placeholder="t('auth.emailPlaceholder')"
                  :disabled="emailVerification.isCodeSent" inputClass="h-11 min-h-[44px]" hideLabel
                  :error="emailVerification.email.trim() && !isValidEmail(emailVerification.email) ? t('auth.validation.emailFormat') : ''"
                  hideErrorText />
              </div>
              <BaseButton @click="sendVerifyCode"
                :disabled="emailVerification.loading || (emailVerification.isCodeSent && emailVerification.resendCooldown > 0) || !isValidEmail(emailVerification.email.trim())"
                :loading="emailVerification.loading" class="h-11 min-h-[44px] shrink-0">
                <span v-if="emailVerification.isCodeSent && emailVerification.resendCooldown > 0">
                  {{ formatVerifyTime(emailVerification.resendCooldown) }}
                </span>
                <span v-else>
                  {{ emailVerification.isCodeSent ? t('auth.resendCode') : t('auth.sendCode') }}
                </span>
              </BaseButton>
            </div>
            <p v-if="emailVerification.email.trim() && !isValidEmail(emailVerification.email)"
              class="text-xs sm:text-sm text-red-600 dark:text-red-400 mt-1" role="alert">
              {{ t('auth.validation.emailFormat') }}
            </p>
          </div>

          <!-- Code Input area -->
          <div v-if="emailVerification.isCodeSent" class="space-y-4">
            <div class="relative">
              <BaseInput v-model="emailVerification.code" :placeholder="t('auth.codePlaceholder')" hideLabel
                :disabled="emailVerification.timeLeft <= 0">
                <template #prefix>
                  <ShieldCheck class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
              <span class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium"
                :class="emailVerification.timeLeft <= 60 ? 'text-red-500' : 'text-gray-500'">
                {{ formatVerifyTime(emailVerification.timeLeft) }}
              </span>
            </div>

            <p v-if="emailVerification.timeLeft <= 0" class="text-xs text-red-500">
              {{ t('auth.codeExpired') }}
            </p>

            <BaseButton @click="verifyEmailCode"
              :disabled="emailVerification.loading || emailVerification.timeLeft <= 0 || !emailVerification.code"
              :loading="emailVerification.loading" variant="primary" class="w-full">
              {{ t('auth.verifyCode') }}
            </BaseButton>
          </div>
        </div>
      </BaseModal>

      <BaseModal :isOpen="isInquiryDetailOpen" :title="'\uBB38\uC758 \uC0C1\uC138'" size="2xl" mobile-full @close="closeInquiryModal">
        <div class="space-y-4 p-2 sm:p-4">
          <div v-if="isInquiryDetailLoading" class="flex items-center justify-center py-10">
            <BaseSkeleton width="100%" height="180px" />
          </div>

          <div
            v-else-if="inquiryDetailError"
            class="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300"
          >
            {{ inquiryDetailError }}
          </div>

          <template v-else-if="selectedInquiryPost">
            <div class="space-y-2 border-b border-gray-200 pb-3 dark:border-gray-700">
              <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ selectedInquiryPost.title }}</h2>
              <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
                <span>{{ '\uC791\uC131\uC77C' }} {{ formatDate(selectedInquiryPost.createdAt) }}</span>
              </div>
            </div>

            <div class="max-h-[60vh] overflow-y-auto rounded-md bg-gray-50 p-4 text-sm text-gray-800 dark:bg-gray-900/30 dark:text-gray-200">
              <div v-if="selectedInquiryPost.contents" class="break-words leading-6" v-html="selectedInquiryPost.contents" />
              <div v-else>-</div>
            </div>

            <div class="border-t border-gray-200 pt-4 dark:border-gray-700">
              <CommentList :postId="selectedInquiryPost.postId" boardUrl="inquiry" />
            </div>
          </template>
        </div>

        <template #footer>
          <div class="flex justify-end gap-2">
            <BaseButton type="button" variant="danger" :loading="isDeletingInquiry" @click="deleteInquiryPost">
              {{ $t('common.delete') }}
            </BaseButton>
            <BaseButton type="button" variant="secondary" @click="closeInquiryModal">
              {{ $t('common.close') }}
            </BaseButton>
          </div>
        </template>
      </BaseModal>
    </div>
  </div>
</template>


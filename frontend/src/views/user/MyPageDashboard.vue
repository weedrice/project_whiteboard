<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { User, Mail, Calendar, FileText, CheckCircle, XCircle, Clock, MessageSquare, ShieldCheck } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import CommentList from '@/components/comment/CommentList.vue'
import { useMyPageDashboardResource } from '@/composables/useMyPageDashboardResource'
import { useInquiryDetailModal } from '@/composables/useInquiryDetailModal'
import { useEmailVerificationFlow } from '@/composables/useEmailVerificationFlow'
import { getOptimizedProfileImageUrl, handleImageError } from '@/utils/image'
import { formatDate } from '@/utils/date'
import { renderCommentContentHtml } from '@/utils/commentContent'
import { applyImageFallback } from '@/utils/imageFallback'
import { renderPostContentHtml } from '@/utils/postContentHtml'
import { resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'

const { t } = useI18n()

// Comment list uses a dedicated emoticon rendering class.
function renderCommentContent(content: string | null | undefined): string {
  return renderCommentContentHtml(content, 'comment-emoticon comment-emoticon-list')
}

const isEditModalOpen = ref(false)




const {
  profile,
  myAgents,
  myPosts,
  myPostsTotalCount,
  myPostsCurrentPage,
  myPostsSize,
  myPostsSort,
  myCommentItems,
  myCommentsTotalCount,
  myCommentsCurrentPage,
  myCommentsSize,
  isLoading,
  error,
  myPostsError,
  myCommentsError,
  fetchMyProfile,
  fetchMyAgents,
  fetchMyPosts,
  handleMyPostsPageChange,
  handleMyPostsSortChange,
  handleMyCommentsPageChange,
  getAgentStatusLabel,
  loadDashboard
} = useMyPageDashboardResource()

const {
  isInquiryDetailOpen,
  selectedInquiryPost,
  isInquiryDetailLoading,
  inquiryDetailError,
  isDeletingInquiry,
  isInquiryPostItem,
  openMyInquiryPost,
  closeInquiryModal,
  deleteInquiryPost
} = useInquiryDetailModal(fetchMyPosts)

const selectedInquiryPostContentsHtml = computed(() =>
  renderPostContentHtml(selectedInquiryPost.value?.contents)
)


const {
  isVerifyModalOpen,
  emailVerification,
  formatVerifyTime,
  isValidEmail,
  openVerifyModal,
  closeVerifyModal,
  sendVerifyCode,
  verifyEmailCode
} = useEmailVerificationFlow({
  getEmail: () => profile.value?.email || '',
  refreshProfile: fetchMyProfile
})

onMounted(async () => {
  await loadDashboard()
})

</script>

<template>
  <div>
    <div v-if="isLoading" class="space-y-6">
      <!-- Profile Skeleton -->
      <div class="max-w-full mx-auto nv-surface shadow rounded-lg p-6">
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
      <div class="max-w-full mx-auto nv-surface shadow rounded-lg p-6">
        <BaseSkeleton width="120px" height="24px" className="mb-4" />
        <div class="space-y-4">
          <BaseSkeleton v-for="i in 3" :key="i" width="100%" height="60px" />
        </div>
      </div>
    </div>

    <div v-else-if="error" class="text-center py-10 nv-form-error">
      {{ error }}
    </div>

    <div v-else>
      <!-- Profile Section -->
      <div class="max-w-full mx-auto">
        <div class="nv-surface shadow overflow-hidden sm:rounded-lg mb-6 transition-colors duration-200">
          <div class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-4">
            <div class="flex items-center min-w-0 flex-1">
              <UserAvatar
                :image-url="profile?.profileImageUrl ? getOptimizedProfileImageUrl(profile.profileImageUrl) : null"
                :name="profile?.displayName || 'U'"
                alt="Profile"
                size-class="h-16 w-16"
                fallback-class="font-bold text-2xl"
                class="mr-4"
                @image-error="handleImageError"
              />
              <div class="min-w-0">
                <h3 class="text-lg leading-6 font-medium nv-title truncate">{{ profile?.displayName
                }}</h3>
                <p class="mt-1 max-w-2xl text-sm nv-text-subtle">{{ $t('user.profile.personalDetails')
                }}</p>
              </div>
            </div>
            <BaseButton @click="isEditModalOpen = true"
              class="w-full sm:w-auto min-h-[44px] sm:min-h-0 flex items-center justify-center">{{
                $t('user.profile.edit') }}</BaseButton>
          </div>
          <div class="border-t nv-border px-4 py-5 sm:p-0">
            <dl class="sm:divide-y sm:divide-[var(--nv-border)]">
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium nv-text-subtle flex items-center whitespace-nowrap">
                  <User class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.displayName') }}
                </dt>
                <dd class="mt-1 text-sm nv-text sm:mt-0">{{ profile?.displayName
                }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium nv-text-subtle flex items-center whitespace-nowrap">
                  <Mail class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.email') }}
                </dt>
                <dd class="mt-1 text-sm nv-text sm:mt-0">
                  {{ profile?.email }}
                  <span v-if="profile?.isEmailVerified"
                    class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium nv-status-success">
                    <CheckCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.verified') }}
                  </span>
                  <button v-else @click="openVerifyModal" type="button"
                    class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium nv-status-danger nv-hover-surface transition-colors cursor-pointer">
                    <XCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.notVerified') }}
                  </button>
                </dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium nv-text-subtle flex items-center whitespace-nowrap">
                  <ShieldCheck class="h-4 w-4 mr-1.5" />
                  {{ t('user.profile.agentCode') }}
                </dt>
                <dd class="mt-1 sm:mt-0">
                  <div v-if="myAgents.length > 0" class="flex flex-wrap gap-2">
                    <span
                      v-for="agent in myAgents"
                      :key="agent.agentId"
                      class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium"
                      :class="agent.status === 'ACTIVE'
                        ? 'nv-status-success'
                        : agent.status === 'SUSPENDED'
                          ? 'nv-status-danger'
                          : 'nv-surface-muted nv-text-muted'"
                    >
                      <span>{{ agent.name }}</span>
                      <span>{{ getAgentStatusLabel(agent.status) }}</span>
                    </span>
                  </div>
                  <span v-else class="text-sm nv-text-subtle">
                    {{ t('user.profile.agentEmpty') }}
                  </span>
                </dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium nv-text-subtle flex items-center whitespace-nowrap">
                  <Calendar class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.joined') }}
                </dt>
                <dd class="mt-1 text-sm nv-text sm:mt-0">{{
                  profile?.createdAt ? formatDate(profile.createdAt) : '' }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
                <dt class="text-sm font-medium nv-text-subtle flex items-center whitespace-nowrap">
                  <Clock class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.lastLogin') }}
                </dt>
                <dd class="mt-1 text-sm nv-text sm:mt-0">{{
                  profile?.lastLoginAt ? formatDate(profile.lastLoginAt) : '' }}</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>

      <!-- My Posts Section -->
      <div class="max-w-full mx-auto">
        <div
          class="nv-surface shadow overflow-hidden sm:rounded-lg mb-6 pb-6 transition-colors duration-200">
          <div class="px-4 py-4 sm:py-5 sm:px-6 border-b nv-border flex items-center">
            <FileText class="h-5 w-5 nv-text-subtle mr-2 flex-shrink-0" />
            <h3 class="text-lg leading-6 font-medium nv-title">{{ $t('user.myPosts') }}</h3>
          </div>

          <div
            v-if="myPostsError"
            class="mx-4 mt-4 rounded border px-4 py-3 text-sm nv-danger-surface"
          >
            {{ myPostsError }}
          </div>
          <div v-else-if="myPosts.length > 0">
            <PostList :posts="myPosts" :totalCount="myPostsTotalCount" :page="myPostsCurrentPage" :size="myPostsSize"
              :current-sort="myPostsSort" :show-board-name="true" :intercept-inquiry="true"
              :resolve-post-route="resolvePostDetailRoute" :should-intercept-post="isInquiryPostItem"
              :resolve-board-route="resolveBoardRoute"
              :show-inquiry-status="isInquiryPostItem"
              @update:sort="handleMyPostsSortChange" @inquiry-click="openMyInquiryPost" />
            <div class="nv-surface-muted px-4 py-4 sm:px-6 flex justify-center">
              <Pagination :current-page="myPostsCurrentPage" :total-pages="Math.ceil(myPostsTotalCount / myPostsSize)"
                @page-change="handleMyPostsPageChange" />
            </div>
          </div>
          <EmptyState v-else :title="$t('common.noData')" :icon="FileText" />
        </div>

        <!-- My Comments Section -->
        <div class="nv-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
          <div class="px-4 py-4 sm:py-5 sm:px-6 border-b nv-border flex items-center">
            <MessageSquare class="h-5 w-5 nv-text-subtle mr-2 flex-shrink-0" />
            <h3 class="text-lg leading-6 font-medium nv-title">{{ $t('user.myComments') }}</h3>
          </div>

          <div
            v-if="myCommentsError"
            class="mx-4 mt-4 rounded border px-4 py-3 text-sm nv-danger-surface"
          >
            {{ myCommentsError }}
          </div>
          <div v-else-if="myCommentItems.length > 0">
            <ul role="list" class="divide-y divide-[var(--nv-border)]">
              <li v-for="comment in myCommentItems" :key="comment.commentId"
                class="px-4 py-4 sm:px-6 nv-hover-surface transition-colors duration-200 min-h-[44px]">
                <router-link v-if="comment.postLink" :to="comment.postLink"
                  class="block">
                  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
                    <div class="flex flex-wrap items-center gap-2 min-w-0">
                      <span
                        class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium nv-surface-muted nv-text-muted flex-shrink-0">
                        {{ comment.boardLabel }}
                      </span>
                      <p class="text-sm font-medium nv-accent-text truncate min-w-0">
                        {{ comment.postTitle }}
                      </p>
                    </div>
                    <p class="flex-shrink-0 font-normal nv-text-subtle text-xs">{{
                      formatDate(comment.createdAt) }}</p>
                  </div>
                  <div class="mt-1 comment-content-list">
                    <p class="text-sm nv-text-muted line-clamp-2"
                      v-html="renderCommentContent(comment.content)" @error.capture="applyImageFallback"></p>
                  </div>
                </router-link>
                <div v-else class="block">
                  <p class="text-sm nv-text-subtle">{{ t('user.comments.deletedPost') }}</p>
                  <div class="comment-content-list mt-1">
                    <p class="text-sm nv-text-muted line-clamp-2"
                      v-html="renderCommentContent(comment.content)" @error.capture="applyImageFallback"></p>
                  </div>
                </div>
              </li>
            </ul>
            <div class="nv-surface-muted px-4 py-4 sm:px-6 flex justify-center">
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
      <BaseModal :isOpen="isVerifyModalOpen" :title="$t('auth.emailNotVerified')" @close="closeVerifyModal">
        <div class="space-y-6 p-4">
          <!-- Email Input & Send Button -->
          <div class="flex flex-col">
            <label class="block text-sm font-medium nv-text mb-1">
              {{ t('user.profile.email') }}
            </label>
            <div class="flex gap-2 items-end">
              <div class="flex-1 min-w-0">
                <BaseInput id="email-verification-email" v-model="emailVerification.email" name="emailVerificationEmail"
                  autocomplete="email" :label="t('user.profile.email')" :placeholder="t('auth.emailPlaceholder')"
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
              class="text-xs sm:text-sm nv-form-error mt-1" role="alert">
              {{ t('auth.validation.emailFormat') }}
            </p>
          </div>

          <!-- Code Input area -->
          <div v-if="emailVerification.isCodeSent" class="space-y-4">
            <div class="relative">
              <BaseInput id="email-verification-code" v-model="emailVerification.code" name="emailVerificationCode"
                inputmode="numeric" autocomplete="one-time-code" :label="t('auth.codePlaceholder')"
                :placeholder="t('auth.codePlaceholder')" hideLabel :disabled="emailVerification.timeLeft <= 0">
                <template #prefix>
                  <ShieldCheck class="h-5 w-5 nv-text-subtle" />
                </template>
              </BaseInput>
              <span class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium"
                :class="emailVerification.timeLeft <= 60 ? 'nv-form-error' : 'nv-text-subtle'">
                {{ formatVerifyTime(emailVerification.timeLeft) }}
              </span>
            </div>

            <p v-if="emailVerification.timeLeft <= 0" class="text-xs nv-form-error">
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

      <BaseModal :isOpen="isInquiryDetailOpen" :title="t('user.inquiryDetail.title')" size="2xl" mobile-full @close="closeInquiryModal">
        <div class="space-y-4 p-2 sm:p-4">
          <div v-if="isInquiryDetailLoading" class="flex items-center justify-center py-10">
            <BaseSkeleton width="100%" height="180px" />
          </div>

          <div
            v-else-if="inquiryDetailError"
            class="rounded border px-4 py-3 text-sm nv-danger-surface"
          >
            {{ inquiryDetailError }}
          </div>

          <template v-else-if="selectedInquiryPost">
            <div class="space-y-2 border-b nv-border pb-3">
              <h2 class="text-lg font-semibold nv-title">{{ selectedInquiryPost.title }}</h2>
              <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs nv-text-subtle">
                <span>{{ t('common.createdAt') }} {{ formatDate(selectedInquiryPost.createdAt) }}</span>
              </div>
            </div>

            <div class="max-h-[60vh] overflow-y-auto rounded-md nv-surface-muted p-4 text-sm">
              <div v-if="selectedInquiryPostContentsHtml" class="break-words leading-6" v-html="selectedInquiryPostContentsHtml" @error.capture="applyImageFallback" />
              <div v-else>-</div>
            </div>

            <div class="border-t nv-border pt-4">
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


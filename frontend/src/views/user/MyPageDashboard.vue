<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { FileText, MessageSquare } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import DashboardListSection from '@/components/user/DashboardListSection.vue'
import EmailVerificationModal from '@/components/user/EmailVerificationModal.vue'
import MyPageCommentList from '@/components/user/MyPageCommentList.vue'
import MyPageProfileCard from '@/components/user/MyPageProfileCard.vue'
import MyPageSummaryCards from '@/components/user/MyPageSummaryCards.vue'
import { useMyPageDashboardResource } from '@/features/user/dashboard/useMyPageDashboardResource'
import { useEmailVerificationFlow } from '@/composables/useEmailVerificationFlow'
import { resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'

const { t } = useI18n()

const isEditModalOpen = ref(false)

const {
  profile,
  myAgents,
  myPosts,
  myPostsTotalCount,
  myPostsTotalPages,
  myPostsCurrentPage,
  myPostsSize,
  myPostsSort,
  myCommentItems,
  myCommentsTotalCount,
  myCommentsTotalPages,
  myCommentsCurrentPage,
  isLoading,
  isProfileLoading,
  isAgentsLoading,
  isMyPostsLoading,
  isMyCommentsLoading,
  error,
  profileError,
  agentsError,
  myPostsError,
  myCommentsError,
  fetchMyProfile,
  fetchMyAgents,
  fetchMyPosts,
  fetchMyComments,
  handleMyPostsPageChange,
  handleMyPostsSortChange,
  handleMyCommentsPageChange,
  getAgentStatusLabel,
  loadDashboard
} = useMyPageDashboardResource(t)

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
  <div :aria-busy="isLoading">
    <h1 class="mb-4 text-2xl font-semibold tracking-[-0.04em] nv-title">{{ $t('common.myPage') }}</h1>
    <div v-if="isLoading" class="space-y-6" role="status" aria-live="polite" aria-busy="true">
      <!-- Profile Skeleton -->
      <div class="max-w-full mx-auto nv-surface nv-elevated-surface shadow rounded-lg p-6">
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
      <div class="max-w-full mx-auto nv-surface nv-elevated-surface shadow rounded-lg p-6">
        <BaseSkeleton width="120px" height="24px" className="mb-4" />
        <div class="space-y-4">
          <BaseSkeleton v-for="i in 3" :key="i" width="100%" height="60px" />
        </div>
      </div>
    </div>

    <ErrorState
      v-else-if="error"
      title-tag="h2"
      :message="error"
      auto-focus
      show-retry
      @retry="loadDashboard"
    />

    <div v-else>
      <MyPageSummaryCards :post-count="myPostsTotalCount" :comment-count="myCommentsTotalCount" />

      <!-- Profile Section -->
      <div class="max-w-full mx-auto" :aria-busy="isProfileLoading || isAgentsLoading">
        <MyPageProfileCard
          v-if="profile"
          :profile="profile"
          :agents="myAgents"
          :get-agent-status-label="getAgentStatusLabel"
          @edit="isEditModalOpen = true"
          @verify-email="openVerifyModal"
        />
        <ErrorState
          v-if="profileError"
          title-tag="h2"
          :message="profileError"
          :show-icon="false"
          show-retry
          @retry="fetchMyProfile"
        />
        <ErrorState
          v-if="agentsError"
          title-tag="h3"
          :message="agentsError"
          :show-icon="false"
          show-retry
          @retry="fetchMyAgents"
        />
      </div>

      <!-- My Posts Section -->
      <div class="max-w-full mx-auto">
        <DashboardListSection
          :title="$t('user.myPosts')"
          :icon="FileText"
          :error="myPostsError"
          :loading="isMyPostsLoading"
          :item-count="myPosts.length"
          :empty-title="$t('common.noData')"
          :current-page="myPostsCurrentPage"
          :total-pages="myPostsTotalPages"
          with-bottom-spacing
          @page-change="handleMyPostsPageChange"
          @retry="fetchMyPosts"
        >
          <PostList :posts="myPosts" :totalCount="myPostsTotalCount" :page="myPostsCurrentPage" :size="myPostsSize"
            :current-sort="myPostsSort" :show-board-name="true"
            :resolve-post-route="resolvePostDetailRoute" :resolve-board-route="resolveBoardRoute"
            @update:sort="handleMyPostsSortChange" />
        </DashboardListSection>

        <!-- My Comments Section -->
        <DashboardListSection
          :title="$t('user.myComments')"
          :icon="MessageSquare"
          :error="myCommentsError"
          :loading="isMyCommentsLoading"
          :item-count="myCommentItems.length"
          :empty-title="t('common.noData')"
          :current-page="myCommentsCurrentPage"
          :total-pages="myCommentsTotalPages"
          @page-change="handleMyCommentsPageChange"
          @retry="fetchMyComments"
        >
          <MyPageCommentList :comments="myCommentItems" />
        </DashboardListSection>
      </div>

      <BaseModal :isOpen="isEditModalOpen" :title="$t('user.profile.edit')" @close="isEditModalOpen = false" mobile-full
        mobile-fit-content>
        <ProfileEditor @close="isEditModalOpen = false" @refreshed="() => { fetchMyProfile(); fetchMyAgents() }" />
      </BaseModal>

      <EmailVerificationModal
        :is-open="isVerifyModalOpen"
        :verification="emailVerification"
        :format-verify-time="formatVerifyTime"
        :is-valid-email="isValidEmail"
        @close="closeVerifyModal"
        @send-code="sendVerifyCode"
        @verify-code="verifyEmailCode"
        @update:email="emailVerification.email = $event"
        @update:code="emailVerification.code = $event"
      />

    </div>
  </div>
</template>


<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminDetailModalShell from '@/components/admin/AdminDetailModalShell.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import BooleanBadge from '@/components/admin/BooleanBadge.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import DescriptionItem from '@/components/admin/detail/DescriptionItem.vue'
import AdminUserCommentsTab from '@/components/admin/user-detail/AdminUserCommentsTab.vue'
import AdminUserPostsTab from '@/components/admin/user-detail/AdminUserPostsTab.vue'
import AdminUserSubscriptionsTab from '@/components/admin/user-detail/AdminUserSubscriptionsTab.vue'
import { useAdmin } from '@/composables/useAdmin'
import { useAdminUserDetailTabs } from '@/features/admin/users/useAdminUserDetailTabs'
import { formatDate } from '@/utils/date'
import { formatInteger } from '@/utils/numberFormat'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/features/comments/commentContent'
import type { SanitizedHtml } from '@/utils/sanitize'
import {
  getAdminUserRoleLabel,
  getAdminUserRoleVariant,
  getAdminUserStatusLabel,
  getAdminUserStatusVariant,
} from '@/utils/adminUserDisplay'

const { t } = useI18n()
const { useAdminUserDetail } = useAdmin()

const props = defineProps<{
  isOpen: boolean
  userId: number | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

const queryUserId = computed<number | null>(() => (props.isOpen ? props.userId : null))

const { data: userDetail, isLoading: isDetailLoading, isError: isDetailError, refetch: refetchDetail } = useAdminUserDetail(queryUserId)
const {
  activeTab,
  commentItems,
  isCommentsLoading,
  isCommentsError,
  isPostsLoading,
  isPostsError,
  isSubscriptionsLoading,
  isSubscriptionsError,
  nextCommentsPage,
  nextPostsPage,
  nextSubscriptionsPage,
  prevCommentsPage,
  prevPostsPage,
  prevSubscriptionsPage,
  refetchComments,
  refetchPosts,
  refetchSubscriptions,
  postItems,
  subscriptionItems,
  userComments,
  userPosts,
  userSubscriptions,
} = useAdminUserDetailTabs({
  isOpen: computed(() => props.isOpen),
  userId: computed(() => props.userId),
  t,
})

const statusVariant = computed(() => {
  if (!userDetail.value) return 'gray'
  return getAdminUserStatusVariant(userDetail.value.status)
})

function getStatusLabel(status: string) {
  return getAdminUserStatusLabel(t, status)
}

function getRoleLabel(role: string | undefined) {
  return getAdminUserRoleLabel(t, role)
}

const roleVariant = computed(() => {
  return getAdminUserRoleVariant(userDetail.value?.role)
})

const detailTabOptions = computed(() => [
  { value: 'posts', label: t('admin.users.detail.writtenPosts') },
  { value: 'comments', label: t('admin.users.detail.writtenComments') },
  { value: 'subscriptions', label: t('admin.users.detail.subscribedBoards') },
])

function renderCommentContent(content: string | null | undefined): SanitizedHtml {
  return renderCommentContentHtml(content, 'comment-emoticon comment-emoticon-list', [], t('comment.emoticonAlt'))
}

function isCommentEmoticonOnly(content: string | null | undefined): boolean {
  return isEmoticonOnlyContent(content)
}

</script>

<template>
  <AdminDetailModalShell
    :is-open="isOpen"
    :title="t('admin.users.detail.title')"
    size="2xl"
    :loading="isDetailLoading"
    :error="isDetailError"
    :empty="!isDetailError && !userDetail"
    :loading-text="t('common.loading')"
    :empty-text="t('admin.users.detail.loadFailed')"
    @close="$emit('close')"
  >
    <template #error>
      <ErrorState
        title-tag="h3"
        :message="t('admin.users.detail.loadFailed')"
        show-retry
        @retry="refetchDetail()"
      />
    </template>
    <div v-if="userDetail" class="space-y-6">
      <div class="flex items-center gap-4 rounded-lg border nv-border p-4">
        <UserAvatar
          :image-url="userDetail.profileImageUrl"
          :name="userDetail.displayName || userDetail.loginId"
          size-class="h-16 w-16"
          fallback-class="text-xl font-semibold"
        />
        <div class="min-w-0 flex-1">
          <div class="truncate text-lg font-semibold nv-title">{{ userDetail.displayName }}</div>
          <div class="truncate text-sm nv-text-muted">@{{ userDetail.loginId }} · {{ userDetail.email }}</div>
          <div class="mt-2 flex items-center gap-2">
            <AdminStatusBadge :label="getStatusLabel(userDetail.status)" :variant="statusVariant" />
            <AdminStatusBadge :label="getRoleLabel(userDetail.role)" :variant="roleVariant" />
            <BooleanBadge
              :value="Boolean(userDetail.isEmailVerified)"
              :true-label="t('admin.users.detail.emailVerified')"
              :false-label="t('admin.users.detail.emailNotVerified')"
            />
          </div>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">{{ t('admin.users.detail.writtenPosts') }}</div>
          <div class="mt-1 text-lg font-semibold nv-title">{{ formatInteger(userDetail.postCount) }}</div>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">{{ t('admin.users.detail.writtenComments') }}</div>
          <div class="mt-1 text-lg font-semibold nv-title">{{ formatInteger(userDetail.commentCount) }}</div>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">{{ t('admin.users.detail.subscribedBoards') }}</div>
          <div class="mt-1 text-lg font-semibold nv-title">{{ formatInteger(userDetail.subscriptionCount) }}</div>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">{{ t('admin.users.detail.reportsAndSanctions') }}</div>
          <div class="mt-1 text-lg font-semibold nv-title">
            {{ userDetail.reportSummary?.pendingCount || 0 }} / {{ userDetail.sanctionSummary?.count || 0 }}
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div class="rounded-lg border nv-border p-3">
          <dl class="space-y-2">
            <DescriptionItem :label="t('admin.users.detail.joinedAndRecentLogin')" value-class="mt-2 space-y-0.5 text-sm nv-text">
              <div>{{ t('admin.users.detail.joined') }}: {{ formatDate(userDetail.createdAt) }}</div>
              <div>{{ t('admin.users.detail.lastLoginAt') }}: {{ userDetail.lastLoginAt ? formatDate(userDetail.lastLoginAt) : '-' }}</div>
              <div v-if="userDetail.deletedAt" class="text-[var(--nv-danger-text)]">{{ t('admin.users.detail.deletedAt') }}: {{ formatDate(userDetail.deletedAt) }}</div>
            </DescriptionItem>
          </dl>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <dl class="space-y-2">
            <DescriptionItem :label="t('admin.users.detail.recentAccess')" value-class="mt-2 space-y-0.5 text-sm nv-text">
              <div>IP: {{ userDetail.recentLogin?.ipAddress || '-' }}</div>
              <div>{{ t('admin.users.detail.accessTime') }}: {{ userDetail.recentLogin?.loggedAt ? formatDate(userDetail.recentLogin.loggedAt) : '-' }}</div>
              <div class="truncate nv-text-subtle">UA: {{ userDetail.recentLogin?.userAgent || '-' }}</div>
            </DescriptionItem>
          </dl>
        </div>
      </div>

      <div>
        <div class="mb-3 flex items-center gap-2 border-b nv-border pb-2">
          <BaseSegmentedControl
            v-model="activeTab"
            :options="detailTabOptions"
            :label="t('admin.users.detail.title')"
            variant="joined"
          />
        </div>

        <AdminUserPostsTab
          v-if="activeTab === 'posts'"
          :items="postItems"
          :loading="isPostsLoading"
          :error="isPostsError"
          :page-data="userPosts"
          @previous="prevPostsPage"
          @next="nextPostsPage"
          @retry="refetchPosts()"
        />

        <AdminUserCommentsTab
          v-else-if="activeTab === 'comments'"
          :items="commentItems"
          :loading="isCommentsLoading"
          :error="isCommentsError"
          :page-data="userComments"
          :render-comment-content="renderCommentContent"
          :is-comment-emoticon-only="isCommentEmoticonOnly"
          @previous="prevCommentsPage"
          @next="nextCommentsPage"
          @retry="refetchComments()"
        />

        <AdminUserSubscriptionsTab
          v-else
          :items="subscriptionItems"
          :loading="isSubscriptionsLoading"
          :error="isSubscriptionsError"
          :page-data="userSubscriptions"
          @previous="prevSubscriptionsPage"
          @next="nextSubscriptionsPage"
          @retry="refetchSubscriptions()"
        />
      </div>
    </div>
  </AdminDetailModalShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminInlinePager from '@/components/admin/AdminInlinePager.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import BooleanBadge from '@/components/admin/BooleanBadge.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import DescriptionItem from '@/components/admin/detail/DescriptionItem.vue'
import { useAdmin } from '@/composables/useAdmin'
import { useAdminUserDetailTabs } from '@/composables/useAdminUserDetailTabs'
import { formatDate } from '@/utils/date'
import { formatInteger } from '@/utils/numberFormat'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/utils/commentContent'
import { applyImageFallback } from '@/utils/imageFallback'
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

const { data: userDetail, isLoading: isDetailLoading } = useAdminUserDetail(queryUserId)
const {
  activeTab,
  commentItems,
  isCommentsLoading,
  isPostsLoading,
  isSubscriptionsLoading,
  nextCommentsPage,
  nextPostsPage,
  nextSubscriptionsPage,
  prevCommentsPage,
  prevPostsPage,
  prevSubscriptionsPage,
  postItems,
  subscriptionItems,
  userComments,
  userPosts,
  userSubscriptions,
} = useAdminUserDetailTabs({
  isOpen: computed(() => props.isOpen),
  userId: computed(() => props.userId)
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

function renderCommentContent(content: string | null | undefined): string {
  return renderCommentContentHtml(content, 'comment-emoticon comment-emoticon-list')
}

function isCommentEmoticonOnly(content: string | null | undefined): boolean {
  return isEmoticonOnlyContent(content)
}

</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.users.detail.title')" size="2xl" @close="$emit('close')">
    <div v-if="isDetailLoading" class="py-10 text-center text-sm nv-text-subtle">
      로딩 중...
    </div>

    <div v-else-if="userDetail" class="space-y-6">
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
              true-label="이메일 인증"
              false-label="이메일 미인증"
            />
          </div>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">작성 글</div>
          <div class="mt-1 text-lg font-semibold nv-title">{{ formatInteger(userDetail.postCount) }}</div>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">작성 댓글</div>
          <div class="mt-1 text-lg font-semibold nv-title">{{ formatInteger(userDetail.commentCount) }}</div>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">구독 노드</div>
          <div class="mt-1 text-lg font-semibold nv-title">{{ formatInteger(userDetail.subscriptionCount) }}</div>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <div class="text-xs nv-text-subtle">신고/제재</div>
          <div class="mt-1 text-lg font-semibold nv-title">
            {{ userDetail.reportSummary?.pendingCount || 0 }} / {{ userDetail.sanctionSummary?.count || 0 }}
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div class="rounded-lg border nv-border p-3">
          <dl class="space-y-2">
            <DescriptionItem label="가입일 / 최근 로그인" value-class="mt-2 space-y-0.5 text-sm nv-text">
              <div>가입: {{ formatDate(userDetail.createdAt) }}</div>
              <div>최근 로그인: {{ userDetail.lastLoginAt ? formatDate(userDetail.lastLoginAt) : '-' }}</div>
              <div v-if="userDetail.deletedAt" class="text-[var(--nv-danger-text)]">탈퇴일: {{ formatDate(userDetail.deletedAt) }}</div>
            </DescriptionItem>
          </dl>
        </div>
        <div class="rounded-lg border nv-border p-3">
          <dl class="space-y-2">
            <DescriptionItem label="최근 접속" value-class="mt-2 space-y-0.5 text-sm nv-text">
              <div>IP: {{ userDetail.recentLogin?.ipAddress || '-' }}</div>
              <div>시간: {{ userDetail.recentLogin?.loggedAt ? formatDate(userDetail.recentLogin.loggedAt) : '-' }}</div>
              <div class="truncate nv-text-subtle">UA: {{ userDetail.recentLogin?.userAgent || '-' }}</div>
            </DescriptionItem>
          </dl>
        </div>
      </div>

      <div>
        <div class="mb-3 flex items-center gap-2 border-b nv-border pb-2">
          <BaseButton :variant="activeTab === 'posts' ? 'primary' : 'secondary'" size="sm" @click="activeTab = 'posts'">작성 글</BaseButton>
          <BaseButton :variant="activeTab === 'comments' ? 'primary' : 'secondary'" size="sm" @click="activeTab = 'comments'">작성 댓글</BaseButton>
          <BaseButton :variant="activeTab === 'subscriptions' ? 'primary' : 'secondary'" size="sm" @click="activeTab = 'subscriptions'">구독 노드</BaseButton>
        </div>

        <div v-if="activeTab === 'posts'" class="space-y-2">
          <div v-if="isPostsLoading" class="py-6 text-center text-sm nv-text-subtle">로딩 중...</div>
          <div v-else-if="!postItems.length" class="py-6 text-center text-sm nv-text-subtle">작성한 글이 없습니다.</div>
          <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
            <div v-for="post in postItems" :key="post.postId" class="rounded-lg border nv-border p-3">
              <div class="truncate text-sm font-medium nv-title">{{ post.title }}</div>
              <div class="mt-2 flex flex-wrap gap-1">
                <AdminStatusBadge v-for="badge in post.badges" :key="badge.label" :label="badge.label" :variant="badge.variant" />
              </div>
              <div class="mt-2 text-xs nv-text-subtle">
                {{ post.metaText }}
              </div>
              <div v-if="post.categoryText" class="mt-1 text-xs nv-text-subtle">
                {{ post.categoryText }}
              </div>
              <div class="mt-1 text-xs nv-text-subtle">
                {{ post.statsText }}
              </div>
            </div>
          </div>
          <AdminInlinePager
            v-if="userPosts"
            :page="userPosts.number"
            :total-pages="userPosts.totalPages"
            @previous="prevPostsPage"
            @next="nextPostsPage"
          />
        </div>

        <div v-else-if="activeTab === 'comments'" class="space-y-2">
          <div v-if="isCommentsLoading" class="py-6 text-center text-sm nv-text-subtle">로딩 중...</div>
          <div v-else-if="!commentItems.length" class="py-6 text-center text-sm nv-text-subtle">작성한 댓글이 없습니다.</div>
          <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
            <div v-for="comment in commentItems" :key="comment.commentId" class="rounded-lg border nv-border p-3">
              <div class="mb-2 flex flex-wrap gap-1">
                <AdminStatusBadge v-for="badge in comment.badges" :key="badge.label" :label="badge.label" :variant="badge.variant" />
              </div>
              <div class="comment-content-list">
                <p v-if="isCommentEmoticonOnly(comment.content)" v-html="renderCommentContent(comment.content)" class="text-sm" @error.capture="applyImageFallback"></p>
                <p v-else v-html="renderCommentContent(comment.content)" class="line-clamp-2 break-words text-sm nv-text" @error.capture="applyImageFallback"></p>
              </div>
              <div class="mt-1 text-xs nv-text-subtle">
                {{ comment.metaText }}
              </div>
              <div class="mt-1 text-xs nv-text-subtle">
                {{ comment.statsText }}
              </div>
            </div>
          </div>
          <AdminInlinePager
            v-if="userComments"
            :page="userComments.number"
            :total-pages="userComments.totalPages"
            @previous="prevCommentsPage"
            @next="nextCommentsPage"
          />
        </div>

        <div v-else class="space-y-2">
          <div v-if="isSubscriptionsLoading" class="py-6 text-center text-sm nv-text-subtle">로딩 중...</div>
          <div v-else-if="!subscriptionItems.length" class="py-6 text-center text-sm nv-text-subtle">구독한 노드가 없습니다.</div>
            <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
              <div v-for="board in subscriptionItems" :key="board.boardId" class="rounded-lg border nv-border p-3">
                <div class="truncate text-sm font-medium nv-title">{{ board.boardName }}</div>
                <div class="mt-2 flex flex-wrap gap-1">
                  <AdminStatusBadge v-for="badge in board.badges" :key="badge.label" :label="badge.label" :variant="badge.variant" />
                </div>
                <div class="mt-1 text-xs nv-text-subtle">{{ board.boardPath }}</div>
                <div class="mt-1 text-xs nv-text-subtle">{{ board.sortOrderText }}</div>
              </div>
          </div>
          <AdminInlinePager
            v-if="userSubscriptions"
            :page="userSubscriptions.number"
            :total-pages="userSubscriptions.totalPages"
            @previous="prevSubscriptionsPage"
            @next="nextSubscriptionsPage"
          />
        </div>
      </div>
    </div>

    <div v-else class="py-10 text-center text-sm nv-text-subtle">
      사용자 정보를 불러올 수 없습니다.
    </div>
  </BaseModal>
</template>

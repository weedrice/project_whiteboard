<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useAdmin } from '@/composables/useAdmin'
import { useAdminUserDetailTabs } from '@/composables/useAdminUserDetailTabs'
import { formatDate } from '@/utils/date'
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
  isCommentsLoading,
  isPostsLoading,
  isSubscriptionsLoading,
  nextCommentsPage,
  nextPostsPage,
  nextSubscriptionsPage,
  prevCommentsPage,
  prevPostsPage,
  prevSubscriptionsPage,
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

function getSubscriptionStateLabel(reason?: string | null) {
  if (reason === 'INACTIVE') return '비활성'
  if (reason === 'PRIVATE') return '비공개'
  if (reason === 'RESTRICTED') return '접근 제한'
  return '접근 가능'
}
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.users.detail.title')" size="2xl" @close="$emit('close')">
    <div v-if="isDetailLoading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
      로딩 중...
    </div>

    <div v-else-if="userDetail" class="space-y-6">
      <div class="flex items-center gap-4 rounded-lg border border-gray-200 p-4 dark:border-gray-700">
        <img
          v-if="userDetail.profileImageUrl"
          :src="userDetail.profileImageUrl"
          alt="profile"
          class="h-16 w-16 rounded-full object-cover"
        />
        <div
          v-else
          class="flex h-16 w-16 items-center justify-center rounded-full bg-gray-200 text-xl font-semibold text-gray-700 dark:bg-gray-700 dark:text-gray-200"
        >
          {{ (userDetail.displayName || userDetail.loginId).slice(0, 1).toUpperCase() }}
        </div>
        <div class="min-w-0 flex-1">
          <div class="truncate text-lg font-semibold text-gray-900 dark:text-white">{{ userDetail.displayName }}</div>
          <div class="truncate text-sm text-gray-600 dark:text-gray-300">@{{ userDetail.loginId }} · {{ userDetail.email }}</div>
          <div class="mt-2 flex items-center gap-2">
            <BaseBadge :variant="statusVariant" size="sm">{{ getStatusLabel(userDetail.status) }}</BaseBadge>
            <BaseBadge :variant="roleVariant" size="sm">{{ getRoleLabel(userDetail.role) }}</BaseBadge>
            <BaseBadge :variant="userDetail.isEmailVerified ? 'success' : 'gray'" size="sm">이메일 {{ userDetail.isEmailVerified ? '인증' : '미인증' }}</BaseBadge>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <div class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400">작성 글</div>
          <div class="mt-1 text-lg font-semibold text-gray-900 dark:text-white">{{ userDetail.postCount.toLocaleString() }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400">작성 댓글</div>
          <div class="mt-1 text-lg font-semibold text-gray-900 dark:text-white">{{ userDetail.commentCount.toLocaleString() }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400">구독 게시판</div>
          <div class="mt-1 text-lg font-semibold text-gray-900 dark:text-white">{{ userDetail.subscriptionCount.toLocaleString() }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400">신고/제재</div>
          <div class="mt-1 text-lg font-semibold text-gray-900 dark:text-white">
            {{ userDetail.reportSummary?.pendingCount || 0 }} / {{ userDetail.sanctionSummary?.count || 0 }}
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400">가입일 / 최근 로그인</div>
          <div class="mt-2 text-sm text-gray-900 dark:text-white">가입: {{ formatDate(userDetail.createdAt) }}</div>
          <div class="text-sm text-gray-900 dark:text-white">최근 로그인: {{ userDetail.lastLoginAt ? formatDate(userDetail.lastLoginAt) : '-' }}</div>
          <div v-if="userDetail.deletedAt" class="text-sm text-red-600 dark:text-red-400">탈퇴일: {{ formatDate(userDetail.deletedAt) }}</div>
        </div>
        <div class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400">최근 접속</div>
          <div class="mt-2 text-sm text-gray-900 dark:text-white">IP: {{ userDetail.recentLogin?.ipAddress || '-' }}</div>
          <div class="text-sm text-gray-900 dark:text-white">시간: {{ userDetail.recentLogin?.loggedAt ? formatDate(userDetail.recentLogin.loggedAt) : '-' }}</div>
          <div class="truncate text-sm text-gray-500 dark:text-gray-400">UA: {{ userDetail.recentLogin?.userAgent || '-' }}</div>
        </div>
      </div>

      <div>
        <div class="mb-3 flex items-center gap-2 border-b border-gray-200 pb-2 dark:border-gray-700">
          <BaseButton :variant="activeTab === 'posts' ? 'primary' : 'secondary'" size="sm" @click="activeTab = 'posts'">작성 글</BaseButton>
          <BaseButton :variant="activeTab === 'comments' ? 'primary' : 'secondary'" size="sm" @click="activeTab = 'comments'">작성 댓글</BaseButton>
          <BaseButton :variant="activeTab === 'subscriptions' ? 'primary' : 'secondary'" size="sm" @click="activeTab = 'subscriptions'">구독 게시판</BaseButton>
        </div>

        <div v-if="activeTab === 'posts'" class="space-y-2">
          <div v-if="isPostsLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">로딩 중...</div>
          <div v-else-if="!userPosts?.content?.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">작성한 글이 없습니다.</div>
          <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
            <div v-for="post in userPosts.content" :key="post.postId" class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <div class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ post.title }}</div>
              <div class="mt-2 flex flex-wrap gap-1">
                <BaseBadge :variant="post.deleted ? 'danger' : 'gray'" size="sm">{{ post.deleted ? '삭제됨' : '노출중' }}</BaseBadge>
                <BaseBadge v-if="post.notice" variant="warning" size="sm">공지</BaseBadge>
                <BaseBadge v-if="post.secret" variant="warning" size="sm">비밀글</BaseBadge>
                <BaseBadge v-if="post.nsfw" variant="danger" size="sm">NSFW</BaseBadge>
                <BaseBadge v-if="post.spoiler" variant="gray" size="sm">스포일러</BaseBadge>
                <BaseBadge v-if="post.authorType === 'AGENT'" variant="gray" size="sm">Agent {{ post.agentName || post.agentId }}</BaseBadge>
              </div>
              <div class="mt-2 text-xs text-gray-500 dark:text-gray-400">
                {{ post.boardName }} · /{{ post.boardUrl }} · {{ formatDate(post.createdAt) }}
              </div>
              <div v-if="post.categoryName" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                카테고리: {{ post.categoryName }}
              </div>
              <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                조회 {{ post.viewCount.toLocaleString() }} · 추천 {{ post.likeCount.toLocaleString() }} · 댓글 {{ post.commentCount.toLocaleString() }}
              </div>
            </div>
          </div>
          <div v-if="userPosts && userPosts.totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
            <BaseButton variant="secondary" size="sm" :disabled="userPosts.number <= 0" @click="prevPostsPage">이전</BaseButton>
            <BaseButton variant="secondary" size="sm" :disabled="userPosts.number + 1 >= userPosts.totalPages" @click="nextPostsPage">다음</BaseButton>
          </div>
        </div>

        <div v-else-if="activeTab === 'comments'" class="space-y-2">
          <div v-if="isCommentsLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">로딩 중...</div>
          <div v-else-if="!userComments?.content?.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">작성한 댓글이 없습니다.</div>
          <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
            <div v-for="comment in userComments.content" :key="comment.commentId" class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <div class="mb-2 flex flex-wrap gap-1">
                <BaseBadge :variant="comment.deleted ? 'danger' : 'gray'" size="sm">{{ comment.deleted ? '삭제됨' : '노출중' }}</BaseBadge>
                <BaseBadge v-if="comment.parentId" variant="gray" size="sm">답글</BaseBadge>
                <BaseBadge v-if="comment.post.deleted" variant="warning" size="sm">원문 삭제</BaseBadge>
                <BaseBadge v-if="!comment.post.boardActive" variant="warning" size="sm">게시판 비활성</BaseBadge>
                <BaseBadge v-if="!comment.post.boardPublic" variant="gray" size="sm">비공개 게시판</BaseBadge>
                <BaseBadge v-if="comment.authorType === 'AGENT'" variant="gray" size="sm">Agent {{ comment.agentName || comment.agentId }}</BaseBadge>
              </div>
              <div class="comment-content-list">
                <p v-if="isCommentEmoticonOnly(comment.content)" v-html="renderCommentContent(comment.content)" class="text-sm" @error.capture="applyImageFallback"></p>
                <p v-else v-html="renderCommentContent(comment.content)" class="line-clamp-2 break-words text-sm text-gray-900 dark:text-white" @error.capture="applyImageFallback"></p>
              </div>
              <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ comment.post.title }} · /{{ comment.post.boardUrl }} · {{ formatDate(comment.createdAt) }}
              </div>
              <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                좋아요 {{ comment.likeCount.toLocaleString() }} · depth {{ comment.depth }}
              </div>
            </div>
          </div>
          <div v-if="userComments && userComments.totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
            <BaseButton variant="secondary" size="sm" :disabled="userComments.number <= 0" @click="prevCommentsPage">이전</BaseButton>
            <BaseButton variant="secondary" size="sm" :disabled="userComments.number + 1 >= userComments.totalPages" @click="nextCommentsPage">다음</BaseButton>
          </div>
        </div>

        <div v-else class="space-y-2">
          <div v-if="isSubscriptionsLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">로딩 중...</div>
          <div v-else-if="!userSubscriptions?.content?.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">구독한 게시판이 없습니다.</div>
            <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
              <div v-for="board in userSubscriptions.content" :key="board.boardId" class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
                <div class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ board.boardName }}</div>
                <div class="mt-2 flex flex-wrap gap-1">
                  <BaseBadge :variant="board.subscriptionAccessible ? 'success' : 'warning'" size="sm">{{ getSubscriptionStateLabel(board.inaccessibleReason) }}</BaseBadge>
                  <BaseBadge :variant="board.boardActive ? 'success' : 'warning'" size="sm">{{ board.boardActive ? '활성' : '비활성' }}</BaseBadge>
                  <BaseBadge :variant="board.boardPublic ? 'gray' : 'warning'" size="sm">{{ board.boardPublic ? '공개' : '비공개' }}</BaseBadge>
                  <BaseBadge variant="gray" size="sm">{{ board.role }}</BaseBadge>
                </div>
                <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">/{{ board.boardUrl }}</div>
                <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">정렬 순서 {{ board.sortOrder ?? '-' }}</div>
              </div>
          </div>
          <div v-if="userSubscriptions && userSubscriptions.totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
            <BaseButton variant="secondary" size="sm" :disabled="userSubscriptions.number <= 0" @click="prevSubscriptionsPage">이전</BaseButton>
            <BaseButton variant="secondary" size="sm" :disabled="userSubscriptions.number + 1 >= userSubscriptions.totalPages" @click="nextSubscriptionsPage">다음</BaseButton>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
      사용자 정보를 불러올 수 없습니다.
    </div>
  </BaseModal>
</template>

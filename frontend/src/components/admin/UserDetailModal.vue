<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useAdmin } from '@/composables/useAdmin'
import { formatDate } from '@/utils/date'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/utils/commentContent'

const { t } = useI18n()
const { useAdminUserDetail, useAdminUserPosts, useAdminUserComments, useAdminUserSubscriptions } = useAdmin()

const props = defineProps<{
  isOpen: boolean
  userId: number | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

const activeTab = ref<'posts' | 'comments' | 'subscriptions'>('posts')
const postsPage = ref(0)
const commentsPage = ref(0)
const subscriptionsPage = ref(0)
const tabSize = 10

const queryUserId = computed<number | null>(() => (props.isOpen ? props.userId : null))

const postsParams = computed(() => ({ page: postsPage.value, size: tabSize }))
const commentsParams = computed(() => ({ page: commentsPage.value, size: tabSize }))
const subscriptionsParams = computed(() => ({ page: subscriptionsPage.value, size: tabSize }))

const { data: userDetail, isLoading: isDetailLoading } = useAdminUserDetail(queryUserId)
const { data: userPosts, isLoading: isPostsLoading } = useAdminUserPosts(queryUserId, postsParams)
const { data: userComments, isLoading: isCommentsLoading } = useAdminUserComments(queryUserId, commentsParams)
const { data: userSubscriptions, isLoading: isSubscriptionsLoading } = useAdminUserSubscriptions(queryUserId, subscriptionsParams)

const statusVariant = computed(() => {
  if (!userDetail.value) return 'gray'
  if (userDetail.value.status === 'ACTIVE') return 'success'
  if (userDetail.value.status === 'SUSPENDED' || userDetail.value.status === 'SANCTIONED') return 'danger'
  if (userDetail.value.status === 'DELETED') return 'warning'
  return 'gray'
})

function getStatusLabel(status: string) {
  return t(`admin.users.status.${status}`)
}

function getRoleLabel(role: string | undefined) {
  if (!role) return '-'
  return t(`admin.users.role.${role}`)
}

const roleVariant = computed(() => {
  switch (userDetail.value?.role) {
    case 'SUPER_ADMIN':
      return 'danger'
    case 'BOARD_ADMIN':
    case 'MODERATOR':
      return 'warning'
    default:
      return 'gray'
  }
})

watch(() => props.isOpen, (open) => {
  if (!open) return
  activeTab.value = 'posts'
  postsPage.value = 0
  commentsPage.value = 0
  subscriptionsPage.value = 0
})

function prevPostsPage() {
  if (!userPosts.value) return
  if (userPosts.value.number > 0) postsPage.value -= 1
}

function nextPostsPage() {
  if (!userPosts.value) return
  if (userPosts.value.number + 1 < userPosts.value.totalPages) postsPage.value += 1
}

function prevCommentsPage() {
  if (!userComments.value) return
  if (userComments.value.number > 0) commentsPage.value -= 1
}

function nextCommentsPage() {
  if (!userComments.value) return
  if (userComments.value.number + 1 < userComments.value.totalPages) commentsPage.value += 1
}

function prevSubscriptionsPage() {
  if (!userSubscriptions.value) return
  if (userSubscriptions.value.number > 0) subscriptionsPage.value -= 1
}

function nextSubscriptionsPage() {
  if (!userSubscriptions.value) return
  if (userSubscriptions.value.number + 1 < userSubscriptions.value.totalPages) subscriptionsPage.value += 1
}

function renderCommentContent(content: string | undefined): string {
  return renderCommentContentHtml(content, 'comment-emoticon comment-emoticon-list')
}

function isCommentEmoticonOnly(content: string | undefined): boolean {
  return isEmoticonOnlyContent(content)
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
          <div v-else-if="!userPosts?.content?.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">데이터가 없습니다.</div>
          <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
            <div v-for="post in userPosts.content" :key="post.postId" class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <div class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ post.title }}</div>
              <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ post.boardName }} · {{ formatDate(post.createdAt) }}</div>
            </div>
          </div>
          <div v-if="userPosts && userPosts.totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
            <BaseButton variant="secondary" size="sm" :disabled="userPosts.number <= 0" @click="prevPostsPage">이전</BaseButton>
            <BaseButton variant="secondary" size="sm" :disabled="userPosts.number + 1 >= userPosts.totalPages" @click="nextPostsPage">다음</BaseButton>
          </div>
        </div>

        <div v-else-if="activeTab === 'comments'" class="space-y-2">
          <div v-if="isCommentsLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">로딩 중...</div>
          <div v-else-if="!userComments?.content?.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">데이터가 없습니다.</div>
          <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
            <div v-for="comment in userComments.content" :key="comment.commentId" class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <div class="comment-content-list">
                <p v-if="isCommentEmoticonOnly(comment.content)" v-html="renderCommentContent(comment.content)" class="text-sm"></p>
                <p v-else v-html="renderCommentContent(comment.content)" class="line-clamp-2 break-words text-sm text-gray-900 dark:text-white"></p>
              </div>
              <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ comment.post?.title }} · {{ formatDate(comment.createdAt) }}</div>
            </div>
          </div>
          <div v-if="userComments && userComments.totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
            <BaseButton variant="secondary" size="sm" :disabled="userComments.number <= 0" @click="prevCommentsPage">이전</BaseButton>
            <BaseButton variant="secondary" size="sm" :disabled="userComments.number + 1 >= userComments.totalPages" @click="nextCommentsPage">다음</BaseButton>
          </div>
        </div>

        <div v-else class="space-y-2">
          <div v-if="isSubscriptionsLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">로딩 중...</div>
          <div v-else-if="!userSubscriptions?.content?.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">데이터가 없습니다.</div>
            <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
              <div v-for="board in userSubscriptions.content" :key="board.boardId" class="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
                <div class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ board.boardName || t('user.subscriptions.unavailableBoard') }}</div>
                <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">/{{ board.boardUrl }}</div>
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

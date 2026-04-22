<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'
import { useComment } from '@/composables/useComment'
import { useAuthStore } from '@/stores/auth'
import { useConfirm } from '@/composables/useConfirm'
import logger from '@/utils/logger'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'

const { confirm } = useConfirm()

const props = defineProps<{
  postId: number | string
  boardUrl: string
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const { useComments, useDeleteComment } = useComment()

const params = ref({ page: 0, size: 50 })
const postId = computed(() => props.postId)
const { data: commentsData, isLoading, error: commentsError } = useComments(postId, params)
const comments = computed<Comment[]>(() => commentsData.value?.content || [])
const commentLoadFailedMessage = computed(() => {
  const translated = t('common.messages.loadFailed')
  return translated === 'common.messages.loadFailed' ? '댓글을 불러오지 못했습니다.' : translated
})

const { mutate: deleteComment } = useDeleteComment()

async function handleDelete(comment: Comment) {
  const isConfirmed = await confirm(t('common.messages.confirmDelete'))
  if (!isConfirmed) {
    return
  }

  deleteComment(comment.commentId, {
    onError: (err) => {
      logger.error('Failed to delete comment:', err)
    },
  })
}
</script>

<template>
  <div class="mt-6 sm:mt-8">
    <h3 class="mb-4 text-base font-medium text-gray-900 dark:text-gray-100 sm:mb-6 sm:text-lg">
      {{ $t('comment.title') }}
    </h3>

    <div
      id="comment-composer"
      class="mb-6 rounded-[24px] border border-[var(--nv-line)] bg-[color:var(--nv-surface)] px-4 py-4 sm:mb-8 sm:px-5"
    >
      <div v-if="authStore.isAuthenticated">
        <CommentForm :postId="postId" />
      </div>
      <div v-else class="text-xs text-gray-500 dark:text-gray-400 sm:text-sm">
        <router-link
          to="/login"
          class="text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300"
        >
          {{ $t('common.login') }}
        </router-link>
        {{ $t('comment.loginRequired', { login: '' }) }}
      </div>
    </div>

    <div v-if="isLoading" class="space-y-4 sm:space-y-6">
      <div v-for="i in 3" :key="i" class="flex space-x-3">
        <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" />
        <div class="flex-1 space-y-2">
          <div class="flex items-center justify-between">
            <BaseSkeleton width="100px" height="16px" />
            <BaseSkeleton width="60px" height="14px" />
          </div>
          <BaseSkeleton width="100%" height="16px" />
          <BaseSkeleton width="80%" height="16px" />
        </div>
      </div>
    </div>

    <div v-else-if="commentsError" class="py-4 text-center text-xs text-red-500 sm:text-sm">
      {{ commentLoadFailedMessage }}
    </div>

    <div v-else class="space-y-4 sm:space-y-6">
      <CommentItem
        v-for="comment in comments"
        :key="comment.commentId"
        v-memo="[postId, boardUrl, comment.commentId, comment.content, comment.likeCount, comment.createdAt, comment.isDeleted, comment.replyCount, comment.hasReplies]"
        :comment="comment"
        :postId="postId"
        :boardUrl="boardUrl"
        @delete="handleDelete"
      />

      <div
        v-if="comments.length === 0"
        class="py-3 text-center text-xs text-gray-500 dark:text-gray-400 sm:py-4 sm:text-sm"
      >
        {{ $t('comment.empty') }}
      </div>
    </div>
  </div>
</template>

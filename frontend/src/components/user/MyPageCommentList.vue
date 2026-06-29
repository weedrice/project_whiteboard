<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { MyCommentListItem } from '@/composables/useMyPageDashboardResource'
import { formatDate } from '@/utils/date'
import { renderCommentContentHtml } from '@/utils/commentContent'
import { applyImageFallback } from '@/utils/imageFallback'

defineProps<{
  comments: MyCommentListItem[]
}>()

const { t } = useI18n()

function renderCommentContent(content: string | null | undefined): string {
  return renderCommentContentHtml(content, 'comment-emoticon comment-emoticon-list')
}
</script>

<template>
  <ul role="list" class="divide-y divide-[var(--nv-border)]">
    <li
      v-for="comment in comments"
      :key="comment.commentId"
      class="px-4 py-4 sm:px-6 nv-hover-surface transition-colors duration-200 min-h-[44px]"
    >
      <router-link v-if="comment.postLink" :to="comment.postLink" class="block">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
          <div class="flex flex-wrap items-center gap-2 min-w-0">
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium nv-surface-muted nv-text-muted flex-shrink-0">
              {{ comment.boardLabel }}
            </span>
            <p class="text-sm font-medium nv-accent-text truncate min-w-0">
              {{ comment.postTitle }}
            </p>
          </div>
          <p class="flex-shrink-0 font-normal nv-text-subtle text-xs">
            {{ formatDate(comment.createdAt) }}
          </p>
        </div>
        <div class="mt-1 comment-content-list">
          <p
            class="text-sm nv-text-muted line-clamp-2"
            v-html="renderCommentContent(comment.content)"
            @error.capture="applyImageFallback"
          />
        </div>
      </router-link>
      <div v-else class="block">
        <p class="text-sm nv-text-subtle">{{ t('user.comments.deletedPost') }}</p>
        <div class="comment-content-list mt-1">
          <p
            class="text-sm nv-text-muted line-clamp-2"
            v-html="renderCommentContent(comment.content)"
            @error.capture="applyImageFallback"
          />
        </div>
      </div>
    </li>
  </ul>
</template>

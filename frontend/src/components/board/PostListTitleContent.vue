<script setup lang="ts">
import { Image as ImageIcon, Lock } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { PostSummary } from '@/types'
import { isDefaultCategory } from '@/utils/board'

const props = withDefaults(defineProps<{
  post: PostSummary
  boardUrl?: string
  truncateTitle?: boolean
  showNoticeBadge?: boolean
  showCommentCount?: boolean
  showPreviewIndicator?: boolean
  showSecretIndicator?: boolean
}>(), {
  truncateTitle: false,
  showNoticeBadge: true,
  showCommentCount: true,
  showPreviewIndicator: true,
  showSecretIndicator: true
})

const { t } = useI18n()

const hasPreviewImage = (post: PostSummary) => (
  Boolean(post.thumbnailUrl) || post.firstMediaType?.toLowerCase() === 'image'
)

const isInquiryPost = (post: PostSummary, boardUrl?: string) => (
  (post.boardUrl || boardUrl) === 'inquiry'
)
</script>

<template>
  <span
    v-if="post.category && !isDefaultCategory(post.category)"
    class="nv-post-badge nv-post-badge-category"
  >
    {{ post.category.name }}
  </span>
  <span v-if="showNoticeBadge && post.isNotice" class="nv-post-badge nv-post-badge-notice">
    {{ $t('common.notice') }}
  </span>
  <span
    v-if="showSecretIndicator && post.isSecret"
    class="inline-flex flex-shrink-0 items-center text-amber-500"
    :title="t('board.writePost.secret')"
  >
    <Lock class="h-4 w-4" />
  </span>
  <span v-if="showPreviewIndicator && hasPreviewImage(post)" class="inline-flex flex-shrink-0 items-center text-[var(--nv-muted)]">
    <ImageIcon class="h-4 w-4" />
  </span>
  <span
    v-if="typeof post.inquiryAnswered === 'boolean' && isInquiryPost(post, boardUrl)"
    class="nv-post-badge"
    :class="post.inquiryAnswered ? 'nv-post-badge-success' : 'nv-post-badge-warn'"
  >
    {{ post.inquiryAnswered ? 'Answered' : 'Pending' }}
  </span>
  <span :class="['nv-post-title-text', truncateTitle ? 'truncate' : 'min-w-0 flex-1 break-words']">
    {{ post.title }}
  </span>
  <span v-if="showCommentCount && post.commentCount > 0" class="nv-post-comment-count flex-shrink-0 text-[var(--nv-accent)]">
    [{{ post.commentCount }}]
  </span>
</template>

<style scoped>
.nv-post-badge {
  align-items: center;
  align-self: center;
  border-radius: 9999px;
  display: inline-flex;
  flex-shrink: 0;
  font-size: 0.62rem;
  font-weight: 700;
  justify-content: center;
  letter-spacing: 0.02em;
  line-height: 1;
  min-height: 1.35rem;
  padding: 0.15rem 0.55rem;
  vertical-align: middle;
}

.nv-post-badge-category {
  background: color-mix(in srgb, var(--nv-surface-2) 70%, transparent);
  border: 1px solid var(--nv-line);
  color: var(--nv-ink-soft);
}

.nv-post-badge-notice {
  background: color-mix(in srgb, #ef4444 12%, transparent);
  border: 1px solid color-mix(in srgb, #ef4444 25%, var(--nv-line));
  color: #dc2626;
}

.nv-post-badge-success {
  background: color-mix(in srgb, #10b981 12%, transparent);
  border: 1px solid color-mix(in srgb, #10b981 24%, var(--nv-line));
  color: #047857;
}

.nv-post-badge-warn {
  background: color-mix(in srgb, #f59e0b 12%, transparent);
  border: 1px solid color-mix(in srgb, #f59e0b 24%, var(--nv-line));
  color: #b45309;
}

.nv-post-title-text,
.nv-post-comment-count {
  line-height: 1.35;
}
</style>

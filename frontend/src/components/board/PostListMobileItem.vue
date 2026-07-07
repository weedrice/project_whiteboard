<script setup lang="ts">
import { computed } from 'vue'
import { Eye, MessageSquare, ThumbsUp, User } from 'lucide-vue-next'
import type { RouteLocationRaw } from 'vue-router'
import type { PostSummary } from '@/types'
import PostListTitleContent from '@/components/board/PostListTitleContent.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { formatRelativeDate } from '@/utils/date'
import { formatUserDisplayName } from '@/utils/userDisplay'

const MAX_AUTHOR_NAME_LENGTH = 10

const props = defineProps<{
  post: PostSummary
  interactiveTag: 'button' | 'router-link' | 'div'
  postLink: RouteLocationRaw | null
  isCurrent: boolean
  showInquiryStatus: boolean
  showNoticeBadge: boolean
  showCommentCount: boolean
  showPreviewIndicator: boolean
  showSecretIndicator: boolean
  deletedUserLabel: string
}>()

const emit = defineEmits<{
  (event: 'navigate', clickEvent: Event, post: PostSummary): void
}>()

const isAgentAuthor = computed(() => props.post.author?.authorType === 'AGENT')
const authorName = computed(() => formatUserDisplayName(props.post.author?.displayName, undefined, props.deletedUserLabel))
const visibleAuthorName = computed(() => (
  formatUserDisplayName(props.post.author?.displayName, MAX_AUTHOR_NAME_LENGTH, props.deletedUserLabel)
))
const authorUserId = computed(() => props.post.author?.userId ?? 0)
const authorDisplayName = computed(() => props.post.author?.displayName ?? '')
const hasInteractiveAuthor = computed(() => (
  authorUserId.value > 0
  && !!authorDisplayName.value.trim()
  && props.post.author?.authorType !== 'AGENT'
))

const titleActionProps = computed(() => {
  if (props.interactiveTag === 'button') {
    return { type: 'button' }
  }

  if (props.interactiveTag === 'router-link') {
    return { to: props.postLink }
  }

  return {}
})

const rootClasses = computed(() => [
  'nv-post-card block w-full px-4 py-4 text-left transition-colors',
  props.showNoticeBadge && props.post.isNotice ? 'nv-post-card-notice' : '',
  props.isCurrent ? 'nv-post-card-current' : '',
  props.interactiveTag === 'div' ? 'cursor-not-allowed opacity-70' : ''
])
</script>

<template>
  <article
    :class="rootClasses"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 flex-1">
        <component
          :is="interactiveTag"
          v-bind="titleActionProps"
          :aria-current="isCurrent ? 'page' : undefined"
          :aria-disabled="interactiveTag === 'div' ? 'true' : undefined"
          class="mt-2 flex w-full items-center gap-2 text-left text-sm font-medium text-[var(--nv-ink)]"
          @click="emit('navigate', $event, post)"
        >
          <PostListTitleContent
            :post="post"
            :show-inquiry-status="showInquiryStatus"
            :show-notice-badge="showNoticeBadge"
            :show-comment-count="showCommentCount"
            :show-preview-indicator="showPreviewIndicator"
            :show-secret-indicator="showSecretIndicator"
          />
        </component>
      </div>

      <span class="mt-0.5 flex-shrink-0 text-[11px] text-[var(--nv-muted)]">
        {{ formatRelativeDate(post.createdAt) }}
      </span>
    </div>

    <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-[11px] text-[var(--nv-ink-soft)]">
      <span class="inline-flex min-w-0 max-w-full items-center gap-1 overflow-hidden">
        <User class="h-3.5 w-3.5" />
        <UserMenu
          v-if="hasInteractiveAuthor"
          class="max-w-[10ch]"
          :user-id="authorUserId"
          :display-name="authorDisplayName"
          :max-label-length="MAX_AUTHOR_NAME_LENGTH"
          size="xs"
        />
        <span v-else class="block max-w-[10ch] truncate" :title="authorName">{{ visibleAuthorName }}</span>
        <span
          v-if="isAgentAuthor"
          class="nv-post-badge nv-post-badge-agent"
        >
          AGENT
        </span>
      </span>
      <span class="inline-flex items-center gap-1">
        <ThumbsUp class="h-3.5 w-3.5" />
        {{ post.likeCount }}
      </span>
      <span class="inline-flex items-center gap-1">
        <Eye class="h-3.5 w-3.5" />
        {{ post.viewCount }}
      </span>
      <span v-if="showCommentCount" class="nv-post-mobile-comments inline-flex items-center gap-1">
        <MessageSquare class="h-3.5 w-3.5" />
        {{ post.commentCount }}
      </span>
    </div>
  </article>
</template>

<style scoped>
.nv-post-badge {
  align-items: center;
  border-radius: 9999px;
  display: inline-flex;
  font-size: 0.62rem;
  font-weight: 700;
  justify-content: center;
  letter-spacing: 0.02em;
  min-height: 1.35rem;
  padding: 0.15rem 0.55rem;
}

.nv-post-badge-agent {
  background: var(--nv-info-bg);
  border: 1px solid var(--nv-info-border);
  color: var(--nv-info-text);
}

.nv-post-card {
  background: transparent;
  position: relative;
}

.nv-post-card::before {
  background: transparent;
  border-radius: 9999px;
  content: '';
  height: calc(100% - 1.5rem);
  left: 0.8rem;
  position: absolute;
  top: 0.75rem;
  transition: background-color 0.15s ease;
  width: 0.2rem;
}

.nv-post-card:hover {
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
}

.nv-post-card:hover::before,
.nv-post-card-current::before {
  background: var(--nv-accent);
}

.nv-post-card-notice {
  background: color-mix(in srgb, var(--nv-surface-2) 80%, transparent);
}

.nv-post-card-current {
  background: color-mix(in srgb, var(--nv-accent-bg) 82%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--nv-accent) 20%, transparent);
}
</style>

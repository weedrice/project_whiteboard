<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronRight, MessageSquare } from 'lucide-vue-next'
import type { FeedPost } from '@/types'
import { formatRelativeDate } from '@/utils/date'
import { buildPostDetailPath } from '@/utils/feedPreview'
import { formatInteger } from '@/utils/numberFormat'

const props = defineProps<{
  posts: FeedPost[]
}>()

const router = useRouter()
const visiblePosts = computed(() => props.posts.slice(0, 4))

const goToPost = (post: FeedPost) => {
  router.push(buildPostDetailPath(post.boardUrl, post.postId))
}
</script>

<template>
  <div class="nv-home-activity-panel">
    <button
      v-for="post in visiblePosts"
      :key="post.postId"
      type="button"
      class="nv-home-activity-row"
      @click="goToPost(post)"
    >
      <div class="flex min-w-0 items-start gap-2 sm:items-center sm:gap-3">
        <span class="w-auto flex-shrink-0 whitespace-nowrap text-left text-xs font-medium uppercase tracking-[0.14em] text-[var(--nv-muted)] sm:w-[5.75rem]">
          {{ formatRelativeDate(post.createdAt) }}
        </span>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-[var(--nv-ink)]">{{ post.title }}</p>
          <div class="mt-0.5 flex min-w-0 items-center gap-2 text-xs text-[var(--nv-muted)]">
            <span class="truncate">{{ post.boardName }}</span>
            <span>&middot;</span>
            <span class="truncate">{{ post.authorName }}</span>
          </div>
        </div>
      </div>
      <div class="ml-0 mt-2 flex flex-shrink-0 items-center justify-end gap-3 text-xs text-[var(--nv-ink-soft)] sm:ml-4 sm:mt-0">
        <span class="inline-flex items-center gap-1">
          <MessageSquare class="h-3.5 w-3.5" aria-hidden="true" />
          {{ formatInteger(post.commentCount) }}
        </span>
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </div>
    </button>
  </div>
</template>

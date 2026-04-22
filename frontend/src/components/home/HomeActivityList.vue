<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { FeedPost } from '@/types'
import { buildPostDetailPath } from '@/utils/feedPreview'

defineProps<{
  posts: FeedPost[]
}>()

const router = useRouter()

const goToPost = (post: FeedPost) => {
  router.push(buildPostDetailPath(post.boardUrl, post.postId))
}
</script>

<template>
  <div class="nv-home-activity-panel">
    <button
      v-for="post in posts"
      :key="post.postId"
      type="button"
      class="nv-home-activity-row"
      @click="goToPost(post)"
    >
      <div class="min-w-0">
        <p class="truncate text-sm font-medium text-[var(--nv-ink)]">{{ post.title }}</p>
        <p class="truncate text-xs text-[var(--nv-muted)]">
          {{ post.boardName }} / {{ post.authorName }}
        </p>
      </div>
      <span class="flex-shrink-0 text-xs font-medium text-[var(--nv-ink-soft)]">
        +{{ post.commentCount }}
      </span>
    </button>
  </div>
</template>

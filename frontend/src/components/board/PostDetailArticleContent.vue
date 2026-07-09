<script setup lang="ts">
import { computed } from 'vue'
import type { VNodeRef } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ImageLightbox from '@/components/common/ui/ImageLightbox.vue'
import PostContentView from '@/components/board/PostContentView.vue'
import PostDetailReactionBar from '@/components/board/PostDetailReactionBar.vue'
import PostDetailSpoilerOverlay from '@/components/board/PostDetailSpoilerOverlay.vue'
import PostDetailTagSection from '@/components/board/PostDetailTagSection.vue'
import PostDetailUrlChip from '@/components/board/PostDetailUrlChip.vue'
import PostPoll from '@/components/board/PostPoll.vue'
import { useRichContentInteractions } from '@/features/board/posts/detail/useRichContentInteractions'
import type { PostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'

const props = defineProps<{
  postView: PostDetailViewModel
  postContents: string
  assignContentRef: VNodeRef
  currentUrl: string
  compactUrl: string
  showCopyHint: boolean
  isBlurred: boolean
  timeLeft: number
  isAuthenticated: boolean
  canReport: boolean
  isLikeAnimating: boolean
  isBookmarkAnimating: boolean
}>()

const emit = defineEmits<{
  (e: 'copy-url'): void
  (e: 'reveal-spoiler'): void
  (e: 'tag-click', tag: string): void
  (e: 'like'): void
  (e: 'bookmark'): void
  (e: 'share'): void
  (e: 'report'): void
}>()

const router = useRouter()
const { t } = useI18n()
const isContentInteractionDisabled = computed(() => props.isBlurred)
const {
  articleRef,
  lightboxOpen,
  lightboxIndex,
  lightboxImages,
  handleContentClick,
  handleContentKeydown,
} = useRichContentInteractions({
  isDisabled: isContentInteractionDisabled,
  router,
  getCodeCopyLabels: () => ({
    copy: t('board.writePost.codeBlock.copy'),
    copied: t('board.writePost.codeBlock.copyDone'),
    failed: t('board.writePost.codeBlock.copyFailed'),
  }),
})
</script>

<template>
  <div class="nv-post-content-stack min-w-0">
    <PostDetailUrlChip
      :current-url="currentUrl"
      :compact-url="compactUrl"
      :show-copy-hint="showCopyHint"
      @copy-url="emit('copy-url')"
    />

    <div
      ref="articleRef"
      class="nv-post-article relative overflow-hidden"
      @click="handleContentClick"
      @keydown="handleContentKeydown"
    >
      <PostContentView
        :ref="assignContentRef"
        class="ql-editor nv-rich-content nv-post-readable prose prose-sm sm:prose-base dark:prose-invert"
        :class="{ 'blur-md select-none': isBlurred }"
        :content="postContents"
        :sandbox-title="postView.title"
      />

      <PostDetailSpoilerOverlay
        v-if="isBlurred"
        :time-left="timeLeft"
        @reveal-spoiler="emit('reveal-spoiler')"
      />
    </div>

    <PostPoll
      v-if="postView.poll"
      :post-id="postView.postId"
      :poll="postView.poll"
      :is-authenticated="isAuthenticated"
    />

    <ImageLightbox
      :is-open="lightboxOpen"
      :images="lightboxImages"
      :initial-index="lightboxIndex"
      :title="postView.title"
      @close="lightboxOpen = false"
      @update:index="lightboxIndex = $event"
    />

    <PostDetailTagSection
      :tags="postView.tags"
      :board-url="postView.boardUrl"
      @tag-click="emit('tag-click', $event)"
    />

    <section
      v-if="postView.seriesNavigation?.series"
      class="rounded-lg border nv-border nv-surface p-4"
    >
      <h2 class="text-sm font-semibold nv-title">{{ postView.seriesNavigation.series.title }}</h2>
      <div class="mt-3 grid gap-2 sm:grid-cols-2">
        <RouterLink
          v-if="postView.seriesNavigation.previousPost"
          :to="{ name: 'post-detail', params: { boardUrl: postView.seriesNavigation.previousPost.boardUrl, postId: postView.seriesNavigation.previousPost.postId } }"
          class="rounded-md border nv-border p-3 text-sm nv-hover-surface"
        >
          <span class="block text-xs nv-text-subtle">{{ t('board.postDetail.previousInSeries') }}</span>
          <span class="mt-1 block truncate nv-title">{{ postView.seriesNavigation.previousPost.title }}</span>
        </RouterLink>
        <RouterLink
          v-if="postView.seriesNavigation.nextPost"
          :to="{ name: 'post-detail', params: { boardUrl: postView.seriesNavigation.nextPost.boardUrl, postId: postView.seriesNavigation.nextPost.postId } }"
          class="rounded-md border nv-border p-3 text-sm nv-hover-surface"
        >
          <span class="block text-xs nv-text-subtle">{{ t('board.postDetail.nextInSeries') }}</span>
          <span class="mt-1 block truncate nv-title">{{ postView.seriesNavigation.nextPost.title }}</span>
        </RouterLink>
      </div>
    </section>

    <div class="hidden sm:block">
      <PostDetailReactionBar
        :liked="postView.liked"
        :scrapped="postView.scrapped"
        :like-count="postView.likeCount"
        :is-authenticated="isAuthenticated"
        :can-report="canReport"
        :is-like-animating="isLikeAnimating"
        :is-bookmark-animating="isBookmarkAnimating"
        @like="emit('like')"
        @bookmark="emit('bookmark')"
        @share="emit('share')"
        @report="emit('report')"
      />
    </div>
  </div>
</template>

<style scoped>
.nv-post-content-stack {
  display: grid;
  gap: 1.25rem;
}

.nv-post-article {
  padding: 0.25rem 0;
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { VNodeRef } from 'vue'
import ImageLightbox from '@/components/common/ui/ImageLightbox.vue'
import PostContentView from '@/components/board/PostContentView.vue'
import PostDetailReactionBar from '@/components/board/PostDetailReactionBar.vue'
import PostDetailSpoilerOverlay from '@/components/board/PostDetailSpoilerOverlay.vue'
import PostDetailTagSection from '@/components/board/PostDetailTagSection.vue'
import PostDetailUrlChip from '@/components/board/PostDetailUrlChip.vue'
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

const articleRef = ref<HTMLElement | null>(null)
const lightboxOpen = ref(false)
const lightboxIndex = ref(0)
const lightboxImages = computed(() => {
  if (!articleRef.value || props.isBlurred) return []
  return Array.from(articleRef.value.querySelectorAll<HTMLImageElement>('.nv-rich-content img'))
    .map((image) => image.currentSrc || image.src)
    .filter(Boolean)
})

function openLightbox(event: MouseEvent) {
  if (props.isBlurred) return
  const image = (event.target as HTMLElement | null)?.closest?.('img')
  if (!(image instanceof HTMLImageElement)) return

  const source = image.currentSrc || image.src
  const index = lightboxImages.value.indexOf(source)
  if (index < 0) return

  lightboxIndex.value = index
  lightboxOpen.value = true
}
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
      @click="openLightbox"
    >
      <PostContentView
        :ref="assignContentRef"
        class="ql-editor nv-rich-content prose prose-sm max-w-none sm:prose-base dark:prose-invert"
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

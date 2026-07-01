<script setup lang="ts">
import type { VNodeRef } from 'vue'
import { Copy } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import PostContentView from '@/components/board/PostContentView.vue'
import PostDetailReactionBar from '@/components/board/PostDetailReactionBar.vue'
import PostTags from '@/components/tag/PostTags.vue'
import type { PostDetailViewModel } from '@/composables/usePostDetailViewModel'

defineProps<{
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
</script>

<template>
  <div class="nv-post-content-stack min-w-0">
    <div class="nv-post-content-top">
      <Transition name="fade">
        <span
          v-if="showCopyHint"
          class="nv-post-copy-hint"
        >
          {{ $t('common.messages.urlCopied') }}
        </span>
      </Transition>

      <button
        type="button"
        class="nv-post-url-chip"
        :title="currentUrl"
        :aria-label="$t('common.copy')"
        @click="emit('copy-url')"
      >
        <Copy class="h-3.5 w-3.5 flex-shrink-0" />
        <span>{{ compactUrl }}</span>
      </button>
    </div>

    <div class="nv-post-article relative overflow-hidden">
      <PostContentView
        :ref="assignContentRef"
        class="ql-editor nv-rich-content prose prose-sm max-w-none sm:prose-base dark:prose-invert"
        :class="{ 'blur-md select-none': isBlurred }"
        :content="postContents"
        :sandbox-title="postView.title"
      />

      <div v-if="isBlurred" class="nv-post-spoiler">
        <div class="nv-post-spoiler-card">
          <h3 class="text-lg font-semibold text-[var(--nv-ink)]">
            {{ $t('board.postDetail.spoilerWarning') }}
          </h3>
          <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
            {{ $t('board.postDetail.spoilerTimer', { time: timeLeft }) }}
          </p>
          <div class="nv-post-spoiler-actions">
            <BaseButton size="sm" @click="emit('reveal-spoiler')">
              {{ $t('board.postDetail.revealSpoiler') }}
            </BaseButton>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="postView.tags.length > 0"
      class="nv-post-tags"
    >
      <p class="nv-post-section-label">{{ $t('board.postDetail.tags') }}</p>
      <PostTags
        :modelValue="postView.tags"
        :readOnly="true"
        :boardUrl="postView.boardUrl"
        compact
        @tag-click="emit('tag-click', $event)"
      />
    </div>

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
.nv-post-copy-hint {
  background: var(--nv-success-bg);
  border: 1px solid var(--nv-success-border);
  border-radius: 9999px;
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-success-text);
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.35rem 0.65rem;
  position: absolute;
  right: 0;
  top: calc(100% + 0.5rem);
  white-space: nowrap;
  z-index: 20;
}

.nv-post-content-top {
  display: flex;
  justify-content: flex-end;
  position: relative;
}

.nv-post-content-stack {
  display: grid;
  gap: 1.25rem;
}

.nv-post-url-chip {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 68%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  cursor: pointer;
  color: var(--nv-muted);
  display: inline-flex;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  gap: 0.35rem;
  letter-spacing: -0.02em;
  max-width: min(16rem, 42vw);
  min-width: 0;
  padding: 0.55rem 0.7rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-url-chip:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink-soft);
}

.nv-post-url-chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nv-post-article {
  padding: 0.25rem 0;
}

.nv-post-tags {
  display: grid;
  gap: 0.55rem;
}

.nv-post-section-label {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.nv-post-spoiler {
  align-items: flex-start;
  background: color-mix(in srgb, var(--nv-surface) 45%, transparent);
  display: flex;
  inset: 0;
  justify-content: center;
  overflow-y: auto;
  padding: clamp(0.75rem, 4vh, 1.5rem);
  position: absolute;
}

.nv-post-spoiler-card {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.5rem;
  box-shadow: var(--nv-shadow-card);
  margin-block: auto;
  max-height: 100%;
  max-width: 22rem;
  overflow-y: auto;
  padding: clamp(1rem, 3vw, 1.5rem);
  text-align: center;
  width: 100%;
}

.nv-post-spoiler-actions {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 639px) {
  .nv-post-url-chip {
    max-width: min(11rem, 48vw);
  }
}
</style>

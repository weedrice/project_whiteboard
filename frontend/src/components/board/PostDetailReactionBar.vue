<script setup lang="ts">
import { AlertTriangle, Bookmark, Share2, ThumbsUp } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

defineProps<{
  liked: boolean
  scrapped: boolean
  likeCount: number
  isAuthenticated: boolean
  canReport: boolean
  isLikeAnimating: boolean
  isBookmarkAnimating: boolean
}>()

const emit = defineEmits<{
  (e: 'like'): void
  (e: 'bookmark'): void
  (e: 'share'): void
  (e: 'report'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="nv-post-reaction-row">
    <button
      type="button"
      class="nv-post-action-btn nv-post-action-btn-circle"
      :class="{ 'is-active': liked }"
      :aria-label="t('common.likes')"
      :aria-pressed="liked"
      :title="t('common.likes')"
      :disabled="!isAuthenticated"
      @click="emit('like')"
    >
      <ThumbsUp class="h-5 w-5" :class="{ 'fill-current bounce-in': isLikeAnimating }" />
      <span class="nv-post-action-count">{{ likeCount }}</span>
    </button>
    <button
      type="button"
      class="nv-post-action-btn nv-post-action-btn-circle"
      :class="{ 'is-active is-bookmark': scrapped }"
      :aria-label="t('board.postDetail.bookmark')"
      :aria-pressed="scrapped"
      :title="t('board.postDetail.bookmark')"
      :disabled="!isAuthenticated"
      @click="emit('bookmark')"
    >
      <Bookmark class="h-5 w-5" :class="{ 'fill-current bounce-in': isBookmarkAnimating }" />
    </button>
    <button
      type="button"
      class="nv-post-action-btn nv-post-action-btn-circle"
      :aria-label="t('common.share')"
      :title="t('common.share')"
      @click="emit('share')"
    >
      <Share2 class="h-5 w-5" />
    </button>
    <button
      v-if="canReport"
      type="button"
      class="nv-post-action-btn nv-post-action-btn-circle is-report"
      :aria-label="t('common.report')"
      :title="t('common.report')"
      @click="emit('report')"
    >
      <AlertTriangle class="h-5 w-5" />
    </button>
  </div>
</template>

<style scoped>
.nv-post-reaction-row {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
  margin-top: 1rem;
}

.nv-post-action-btn {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.1rem;
  color: var(--nv-ink-soft);
  display: inline-flex;
  gap: 0.55rem;
  justify-content: center;
  min-height: 3rem;
  min-width: 0;
  padding: 0.8rem 1rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-post-action-count {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 92%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink);
  display: inline-flex;
  font-size: 0.68rem;
  font-weight: 700;
  justify-content: center;
  min-width: 1.45rem;
  padding: 0.12rem 0.38rem;
  position: absolute;
  right: -0.35rem;
  top: -0.2rem;
}

.nv-post-action-btn:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-action-btn.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-post-action-btn.is-bookmark {
  color: var(--nv-warning-text);
}

.nv-post-action-btn.is-report svg {
  stroke: var(--nv-danger);
}

.nv-post-action-btn-circle {
  border-radius: 9999px;
  height: 3.1rem;
  justify-content: center;
  padding: 0;
  position: relative;
  width: 3.1rem;
}

.bounce-in {
  animation: bounce-in 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes bounce-in {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.3);
  }
  100% {
    transform: scale(1);
  }
}
</style>

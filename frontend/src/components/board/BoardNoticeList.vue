<script setup lang="ts">
import { useId } from 'vue'
import { ChevronDown, ChevronUp, Megaphone } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { RouteLocationRaw } from 'vue-router'
import type { PostSummary } from '@/types'
import { formatRelativeDate } from '@/utils/date'

defineProps<{
  notices: PostSummary[]
  visibleNotices: PostSummary[]
  hasNoticeOverflow: boolean
  isExpanded: boolean
  highlightedPostId?: string | number | null
  getNoticeRoute: (notice: PostSummary) => RouteLocationRaw
}>()

const emit = defineEmits<{
  (event: 'update:isExpanded', value: boolean): void
}>()

const { t } = useI18n()
const noticeListId = useId()
</script>

<template>
  <div v-if="notices.length" class="nv-board-notices">
    <div class="nv-board-notice-heading nv-kicker">
      <span class="inline-flex items-center gap-1.5">
        <Megaphone class="h-4 w-4" />
        {{ t('board.detail.notices.title') }}
      </span>
      <span class="text-xs font-medium text-[var(--nv-muted)]">
        {{ notices.length }}
      </span>
    </div>

    <div :id="noticeListId" class="divide-y divide-[var(--nv-line-soft)]">
      <router-link
        v-for="notice in visibleNotices"
        :key="notice.postId"
        :to="getNoticeRoute(notice)"
        class="nv-board-notice-row"
        :class="{ 'is-current': String(notice.postId) === String(highlightedPostId ?? '') }"
      >
        <span class="nv-board-notice-badge">{{ t('common.notice') }}</span>
        <span class="min-w-0 flex-1 truncate text-sm font-semibold text-[var(--nv-ink)]">
          {{ notice.title }}
        </span>
        <span class="hidden flex-shrink-0 text-xs text-[var(--nv-muted)] sm:inline">
          {{ formatRelativeDate(notice.createdAt) }}
        </span>
      </router-link>
    </div>

    <button
      v-if="hasNoticeOverflow"
      type="button"
      class="nv-board-notice-more"
      :aria-expanded="isExpanded"
      :aria-controls="noticeListId"
      @click="emit('update:isExpanded', !isExpanded)"
    >
      <span></span>
      <span class="inline-flex items-center gap-1">
        {{ isExpanded ? t('board.detail.notices.collapse') : t('board.detail.notices.more') }}
        <ChevronUp v-if="isExpanded" class="h-3.5 w-3.5" />
        <ChevronDown v-else class="h-3.5 w-3.5" />
      </span>
      <span></span>
    </button>
  </div>
</template>

<style scoped>
.nv-board-notices {
  background: color-mix(in srgb, var(--nv-surface-2) 42%, transparent);
  border-bottom: 1px solid var(--nv-line);
}

.nv-board-notice-heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 0.85rem 1rem 0.4rem;
}

.nv-board-notice-row {
  align-items: center;
  display: flex;
  gap: 0.65rem;
  min-height: 2.75rem;
  padding: 0.7rem 1rem;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.nv-board-notice-row:hover,
.nv-board-notice-row.is-current {
  background: color-mix(in srgb, var(--nv-accent-bg) 70%, transparent);
}

.nv-board-notice-badge {
  align-items: center;
  background: var(--nv-danger-bg);
  border: 1px solid var(--nv-danger-border);
  border-radius: 9999px;
  color: var(--nv-danger-text);
  display: inline-flex;
  flex-shrink: 0;
  font-size: 0.62rem;
  font-weight: 700;
  line-height: 1;
  min-height: 1.35rem;
  justify-content: center;
  padding: 0.15rem 0.55rem;
}

.nv-board-notice-more {
  align-items: center;
  color: var(--nv-muted);
  cursor: pointer;
  display: grid;
  font-size: var(--nv-type-kicker);
  font-weight: 700;
  gap: 0.65rem;
  grid-template-columns: 1fr auto 1fr;
  letter-spacing: 0.08em;
  min-height: 2.75rem;
  padding: 0 1rem 0.55rem;
  text-transform: uppercase;
  width: 100%;
}

.nv-board-notice-more > span:first-child,
.nv-board-notice-more > span:last-child {
  background: var(--nv-line);
  height: 1px;
}

.nv-board-notice-more:hover {
  color: var(--nv-accent);
}
</style>

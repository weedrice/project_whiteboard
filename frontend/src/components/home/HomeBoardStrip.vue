<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { BoardListItem } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

defineProps<{
  boards: BoardListItem[]
  remainingSlots: number
  isLoading: boolean
  isError: boolean
}>()

const { t, locale } = useI18n()
const numberFormatter = computed(() => new Intl.NumberFormat(locale.value === 'ko' ? 'ko-KR' : 'en-US'))
const formatNumber = (value: number) => numberFormatter.value.format(value)
</script>

<template>
  <section class="space-y-4">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ t('home.landing.topBoards') }}</h2>
      </div>
      <RouterLink to="/boards" class="text-sm font-medium text-[var(--nv-accent)] hover:underline">
        {{ t('common.viewAll') }}
      </RouterLink>
    </div>

    <div v-if="isLoading" class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)]">
      <div class="grid grid-cols-2 gap-px xl:grid-cols-7">
        <div
          v-for="index in 7"
          :key="index"
          class="bg-[var(--nv-surface)] px-4 py-4 text-sm text-[var(--nv-muted)]"
        >
          {{ t('home.landing.loadingBoards') }}
        </div>
      </div>
    </div>
    <div
      v-else-if="boards.length"
      class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)] shadow-[var(--nv-shadow-soft)]"
    >
      <div class="grid grid-cols-2 gap-px xl:grid-cols-7">
        <RouterLink
          v-for="board in boards"
          :key="board.boardId"
          :to="`/board/${encodePathSegment(board.boardUrl)}`"
          class="group flex min-h-[68px] flex-col bg-[var(--nv-surface)] px-3.5 pt-3 pb-2 transition-all duration-150 hover:bg-[var(--nv-surface-2)]"
        >
          <div class="min-w-0">
            <p class="line-clamp-2 text-[15px] font-semibold leading-5 text-[var(--nv-ink)] group-hover:text-[var(--nv-accent)]">
              {{ board.boardName }}
            </p>
          </div>
          <div class="mt-0.5 flex items-center gap-2 text-left">
            <p class="text-[12px] font-medium tracking-[0.02em] text-[var(--nv-ink-soft)]">{{ formatNumber(board.postCount ?? 0) }}</p>
          </div>
        </RouterLink>
        <RouterLink
          v-if="remainingSlots > 0"
          to="/boards"
          class="nv-home-board-view-all group flex min-h-[68px] flex-col justify-center bg-[var(--nv-bg)] px-3.5 pt-3 pb-2 transition-all duration-150 hover:bg-[var(--nv-surface-2)]"
          :style="{ '--remaining-board-slots': remainingSlots }"
        >
          <span class="text-[15px] font-semibold leading-5 text-[var(--nv-ink)] group-hover:text-[var(--nv-accent)]">
            {{ t('common.viewAll') }}
          </span>
          <span class="mt-0.5 text-[12px] font-medium tracking-[0.02em] text-[var(--nv-ink-soft)]">
            {{ t('home.landing.topBoards') }}
          </span>
        </RouterLink>
      </div>
    </div>
    <div
      v-else
      class="rounded-[24px] border border-dashed border-[var(--nv-line)] px-5 py-6 text-sm text-[var(--nv-muted)]"
    >
      {{ isError ? t('home.landing.boardsUnavailable') : t('home.landing.emptyBoards') }}
    </div>
  </section>
</template>

<style scoped>
@media (min-width: 1280px) {
  .nv-home-board-view-all {
    grid-column: span var(--remaining-board-slots, 1) / span var(--remaining-board-slots, 1);
  }
}
</style>

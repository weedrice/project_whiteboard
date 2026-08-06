<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PostListDensityControl from '@/components/board/PostListDensityControl.vue'
import type { PostListDensity } from '@/components/board/postListDensity'
import type { Category } from '@/types/board'

withDefaults(defineProps<{
  categories: Category[]
  isAllPostsActive: boolean
  conceptOnly: boolean
  selectedCategoryId: number | null
  density?: PostListDensity
}>(), {
  density: 'default',
})

const emit = defineEmits<{
  (event: 'activateAll'): void
  (event: 'toggleConcept'): void
  (event: 'toggleCategory', categoryId: number): void
  (event: 'update:density', density: PostListDensity): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="nv-board-toolbar-sticky flex items-center gap-2 px-4 py-3 sm:gap-3 sm:px-5">
    <div class="nv-board-filter-rail" role="group" :aria-label="t('board.detail.filterLabel')">
      <div class="nv-board-filter-track">
        <button
          type="button"
          class="nv-board-filter-chip"
          :class="{ 'is-active': isAllPostsActive }"
          :aria-pressed="isAllPostsActive"
          @click="emit('activateAll')"
        >
          {{ t('board.detail.filter.all') }}
        </button>
        <button
          type="button"
          class="nv-board-filter-chip"
          :class="{ 'is-active': conceptOnly }"
          :aria-pressed="conceptOnly"
          @click="emit('toggleConcept')"
        >
          {{ t('board.detail.filter.concept') }}
        </button>
        <button
          v-for="category in categories"
          :key="category.categoryId"
          type="button"
          class="nv-board-filter-chip"
          :class="{ 'is-active': selectedCategoryId === category.categoryId }"
          :aria-pressed="selectedCategoryId === category.categoryId"
          @click="emit('toggleCategory', category.categoryId)"
        >
          {{ category.name }}
        </button>
      </div>
    </div>

    <PostListDensityControl
      class="nv-board-density-control"
      :model-value="density"
      @update:model-value="emit('update:density', $event)"
    />
  </div>
</template>

<style scoped>
.nv-board-toolbar-sticky {
  position: relative;
}

.nv-board-filter-chip {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 0.6rem;
  color: var(--nv-ink-soft);
  cursor: pointer;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 0.8rem;
  font-weight: 600;
  justify-content: center;
  min-height: 2.1rem;
  padding: 0.45rem 0.8rem;
  transition: transform 0.12s ease, background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-board-filter-chip:hover {
  border-color: color-mix(in srgb, var(--nv-accent) 18%, var(--nv-line));
  color: var(--nv-ink);
}

.nv-board-filter-chip:active {
  transform: scale(0.98);
}

.nv-board-filter-chip.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 26%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-board-filter-rail {
  flex: 1 1 auto;
  margin-inline: -0.15rem;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 0.15rem;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.nv-board-density-control {
  flex: 0 0 auto;
}

.nv-board-filter-rail::-webkit-scrollbar {
  display: none;
}

.nv-board-filter-track {
  display: inline-flex;
  gap: 0.5rem;
  min-width: 100%;
  width: max-content;
}

@media (max-width: 639px) {
  .nv-board-filter-chip {
    min-height: 2.75rem;
  }
}

.nv-board-filter-track > .nv-board-filter-chip {
  min-width: max-content;
}

@media (max-width: 640px) {
  .nv-board-filter-chip {
    font-size: 0.75rem;
    min-height: 2.75rem;
    padding: 0.4rem 0.7rem;
  }
}
</style>

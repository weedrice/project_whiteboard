<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PostListDensityControl from '@/components/board/PostListDensityControl.vue'
import type { PostListDensity } from '@/components/board/postListDensity'
import type { Category } from '@/types/board'

const MOUSE_DRAG_THRESHOLD_PX = 6

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

const filterRail = ref<HTMLElement | null>(null)
const isDragging = ref(false)
let activePointerId: number | null = null
let dragStartX = 0
let dragStartY = 0
let dragStartScrollLeft = 0
let suppressNextClick = false
let clickSuppressionTimer: number | null = null
let lostPointerEndCleanup: (() => void) | null = null

function clearClickSuppressionTimer() {
  if (clickSuppressionTimer === null) return
  window.clearTimeout(clickSuppressionTimer)
  clickSuppressionTimer = null
}

function scheduleClickSuppressionReset() {
  clearClickSuppressionTimer()
  clickSuppressionTimer = window.setTimeout(() => {
    suppressNextClick = false
    clickSuppressionTimer = null
  }, 0)
}

function clearLostPointerEndListener() {
  lostPointerEndCleanup?.()
  lostPointerEndCleanup = null
}

function waitForLostPointerEnd(pointerId: number) {
  clearLostPointerEndListener()
  const onPointerEnd = (event: PointerEvent) => {
    if (event.pointerId !== pointerId) return
    clearLostPointerEndListener()
    scheduleClickSuppressionReset()
  }
  document.addEventListener('pointerup', onPointerEnd, true)
  document.addEventListener('pointercancel', onPointerEnd, true)
  lostPointerEndCleanup = () => {
    document.removeEventListener('pointerup', onPointerEnd, true)
    document.removeEventListener('pointercancel', onPointerEnd, true)
  }
}

function onFilterRailPointerDown(event: PointerEvent) {
  if (activePointerId === null) {
    suppressNextClick = false
    clearClickSuppressionTimer()
    clearLostPointerEndListener()
  }
  if (
    event.pointerType !== 'mouse'
    || !event.isPrimary
    || event.button !== 0
    || activePointerId !== null
  ) return

  const rail = filterRail.value
  if (!rail || rail.scrollWidth <= rail.clientWidth) return

  activePointerId = event.pointerId
  dragStartX = event.clientX
  dragStartY = event.clientY
  dragStartScrollLeft = rail.scrollLeft
}

function onFilterRailPointerMove(event: PointerEvent) {
  if (event.pointerId !== activePointerId) return

  const rail = filterRail.value
  if (!rail) return

  const deltaX = event.clientX - dragStartX
  const deltaY = event.clientY - dragStartY
  if (!isDragging.value) {
    if (
      Math.abs(deltaX) <= MOUSE_DRAG_THRESHOLD_PX
      && Math.abs(deltaY) <= MOUSE_DRAG_THRESHOLD_PX
    ) return
    if (Math.abs(deltaX) <= Math.abs(deltaY)) {
      activePointerId = null
      return
    }

    if (typeof rail.setPointerCapture !== 'function') {
      activePointerId = null
      return
    }
    try {
      rail.setPointerCapture(event.pointerId)
    } catch {
      activePointerId = null
      return
    }
    isDragging.value = true
    suppressNextClick = true
  }

  event.preventDefault()
  const maxScrollLeft = Math.max(0, rail.scrollWidth - rail.clientWidth)
  rail.scrollLeft = Math.min(maxScrollLeft, Math.max(0, dragStartScrollLeft - deltaX))
}

function finishFilterRailDrag(event: PointerEvent, canceled = false) {
  if (event.pointerId !== activePointerId) return

  const rail = filterRail.value
  const wasDragging = isDragging.value
  activePointerId = null
  isDragging.value = false
  if (canceled) {
    suppressNextClick = false
    clearClickSuppressionTimer()
  } else if (wasDragging) {
    scheduleClickSuppressionReset()
  }
  if (rail?.hasPointerCapture?.(event.pointerId)) rail.releasePointerCapture(event.pointerId)
}

function onFilterRailLostPointerCapture(event: PointerEvent) {
  if (event.pointerId !== activePointerId) return
  activePointerId = null
  isDragging.value = false
  waitForLostPointerEnd(event.pointerId)
}

function onFilterRailPointerLeave(event: PointerEvent) {
  if (event.pointerId !== activePointerId || isDragging.value) return
  activePointerId = null
}

function onFilterRailClick(event: MouseEvent) {
  if (!suppressNextClick) return

  suppressNextClick = false
  clearClickSuppressionTimer()
  clearLostPointerEndListener()
  if (event.detail === 0) return
  event.preventDefault()
  event.stopPropagation()
}

onBeforeUnmount(() => {
  clearClickSuppressionTimer()
  clearLostPointerEndListener()
})
</script>

<template>
  <div class="nv-board-toolbar-sticky flex items-center gap-2 px-4 py-3 sm:gap-3 sm:px-5">
    <div
      ref="filterRail"
      class="nv-board-filter-rail"
      :class="{ 'is-dragging': isDragging }"
      role="group"
      :aria-label="t('board.detail.filterLabel')"
      @click.capture="onFilterRailClick"
      @pointerdown="onFilterRailPointerDown"
      @pointermove="onFilterRailPointerMove"
      @pointerup="finishFilterRailDrag"
      @pointercancel="finishFilterRailDrag($event, true)"
      @pointerleave="onFilterRailPointerLeave"
      @lostpointercapture="onFilterRailLostPointerCapture"
    >
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

.nv-board-filter-rail.is-dragging,
.nv-board-filter-rail.is-dragging .nv-board-filter-chip {
  cursor: grabbing;
  user-select: none;
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

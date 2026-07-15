<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Check, LoaderCircle, RotateCw } from 'lucide-vue-next'
import { isStandaloneDisplayMode } from '@/pwaDisplayMode'

const PULL_THRESHOLD = 72
const MAX_PULL_DISTANCE = 112
const PULL_RESISTANCE = 0.45

const props = withDefaults(defineProps<{
  enabled?: boolean
  refresh: () => Promise<unknown> | unknown
}>(), {
  enabled: true,
})

type PullState = 'idle' | 'pulling' | 'refreshing' | 'complete'

const standalone = ref(false)
const state = ref<PullState>('idle')
const pullDistance = ref(0)
const activePointerId = ref<number | null>(null)
const startX = ref(0)
const startY = ref(0)
const rawPullDistance = ref(0)
const rejectedGesture = ref(false)

const active = computed(() => props.enabled && standalone.value)
const indicatorLabel = computed(() => {
  if (state.value === 'refreshing') return 'common.pwa.pullToRefresh.refreshing'
  if (state.value === 'complete') return 'common.pwa.pullToRefresh.complete'
  if (rawPullDistance.value >= PULL_THRESHOLD) return 'common.pwa.pullToRefresh.release'
  return 'common.pwa.pullToRefresh.pull'
})
const contentStyle = computed(() => ({
  transform: active.value && pullDistance.value > 0 ? `translateY(${pullDistance.value}px)` : undefined,
  willChange: active.value && pullDistance.value > 0 ? 'transform' : undefined,
}))
const indicatorStyle = computed(() => ({
  opacity: Math.min(1, pullDistance.value / 36),
  transform: `translate(-50%, ${Math.max(0, pullDistance.value - 44)}px)`,
}))

function isExcludedTarget(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(
    'input, textarea, select, button, a, [contenteditable="true"], [role="button"]',
  ) != null
}

function resetGesture() {
  activePointerId.value = null
  rawPullDistance.value = 0
  rejectedGesture.value = false
  if (state.value === 'pulling') state.value = 'idle'
  if (state.value === 'idle') pullDistance.value = 0
}

function onPointerDown(event: PointerEvent) {
  if (!active.value || state.value === 'refreshing' || event.pointerType !== 'touch') return
  if (activePointerId.value !== null) {
    rejectedGesture.value = true
    return
  }
  if (window.scrollY > 0 || isExcludedTarget(event.target)) return

  activePointerId.value = event.pointerId
  startX.value = event.clientX
  startY.value = event.clientY
  rawPullDistance.value = 0
  rejectedGesture.value = false
}

function onPointerMove(event: PointerEvent) {
  if (event.pointerId !== activePointerId.value || rejectedGesture.value || state.value === 'refreshing') return
  const deltaX = event.clientX - startX.value
  const deltaY = event.clientY - startY.value
  if (deltaY <= 0 || Math.abs(deltaX) > deltaY) {
    rejectedGesture.value = true
    pullDistance.value = 0
    return
  }
  if (window.scrollY > 0) {
    resetGesture()
    return
  }

  event.preventDefault()
  rawPullDistance.value = deltaY
  pullDistance.value = Math.min(MAX_PULL_DISTANCE, deltaY * PULL_RESISTANCE)
  state.value = 'pulling'
}

async function runRefresh() {
  if (state.value === 'refreshing') return
  state.value = 'refreshing'
  pullDistance.value = 48
  try {
    await props.refresh()
    state.value = 'complete'
    await new Promise((resolve) => window.setTimeout(resolve, 500))
  } finally {
    state.value = 'idle'
    pullDistance.value = 0
  }
}

function onPointerEnd(event: PointerEvent) {
  if (event.pointerId !== activePointerId.value) return
  const shouldRefresh = !rejectedGesture.value && rawPullDistance.value >= PULL_THRESHOLD
  activePointerId.value = null
  if (shouldRefresh) void runRefresh()
  else resetGesture()
}

onMounted(() => {
  standalone.value = isStandaloneDisplayMode()
})
</script>

<template>
  <div
    class="nv-pull-to-refresh"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerEnd"
    @pointercancel="onPointerEnd"
  >
    <div
      v-if="active && pullDistance > 0"
      class="nv-pull-to-refresh-indicator"
      :style="indicatorStyle"
      aria-hidden="true"
    >
      <LoaderCircle v-if="state === 'refreshing'" class="h-5 w-5 animate-spin" />
      <Check v-else-if="state === 'complete'" class="h-5 w-5" />
      <RotateCw v-else class="h-5 w-5" />
    </div>
    <div class="nv-pull-to-refresh-content" :style="contentStyle">
      <slot />
    </div>
    <span class="sr-only" aria-live="polite">{{ $t(indicatorLabel) }}</span>
  </div>
</template>

<style scoped>
.nv-pull-to-refresh {
  min-height: 100%;
  position: relative;
}

.nv-pull-to-refresh-indicator {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-accent);
  display: flex;
  height: 2.5rem;
  justify-content: center;
  left: 50%;
  position: absolute;
  top: 0;
  width: 2.5rem;
  z-index: 1;
}

.nv-pull-to-refresh-content {
  transition: transform 180ms ease-out;
}

@media (prefers-reduced-motion: reduce) {
  .nv-pull-to-refresh-content {
    transition: none;
  }

  .animate-spin {
    animation: none;
  }
}
</style>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, toRef, watch } from 'vue'
import { ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useEventListener } from '@/composables/useEventListener'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { useLightboxGestures } from '@/composables/useLightboxGestures'
import { useBodyScrollLock } from '@/composables/useBodyScrollLock'

const props = withDefaults(defineProps<{
  isOpen: boolean
  images: Array<{ src: string, alt: string }>
  initialIndex?: number
  title?: string
}>(), {
  initialIndex: 0,
  title: 'Image viewer',
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'update:index', value: number): void
}>()

const dialogRef = ref<HTMLElement | null>(null)
const { t } = useI18n()
const currentIndex = ref(0)
const { trapFocus, restoreFocus } = useFocusTrap(dialogRef, () => props.isOpen)
useBodyScrollLock(toRef(props, 'isOpen'))

const hasMultipleImages = computed(() => props.images.length > 1)
const currentImage = computed(() => props.images[currentIndex.value] ?? null)
const counterLabel = computed(() => `${currentIndex.value + 1} / ${props.images.length}`)
function clampIndex(index: number) {
  if (props.images.length === 0) return 0
  return Math.min(Math.max(index, 0), props.images.length - 1)
}

function close() {
  emit('close')
}

function go(delta: number) {
  if (!hasMultipleImages.value) return
  const nextIndex = (currentIndex.value + delta + props.images.length) % props.images.length
  currentIndex.value = nextIndex
  resetImageTransform()
  emit('update:index', nextIndex)
}

const {
  imageTransform,
  resetImageTransform,
  handleImagePointerDown,
  handleImagePointerMove,
  handleImagePointerUp,
  toggleZoom,
} = useLightboxGestures({ close, go })

function handleKeydown(event: KeyboardEvent) {
  if (!props.isOpen) return
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    go(-1)
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    go(1)
  }
}

watch(() => props.isOpen, async (isOpen) => {
  if (isOpen) {
    currentIndex.value = clampIndex(props.initialIndex)
    resetImageTransform()
    await nextTick()
    trapFocus()
  } else {
    restoreFocus()
  }
}, { immediate: true })

watch(() => props.initialIndex, (index) => {
  if (props.isOpen) {
    currentIndex.value = clampIndex(index)
    resetImageTransform()
  }
})

useEventListener(() => document, 'keydown', handleKeydown)

onUnmounted(() => {
  restoreFocus()
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-[var(--nv-z-overlay)] flex items-center justify-center bg-[color-mix(in_srgb,var(--nv-scrim)_85%,transparent)] p-4"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      @click.self="close"
    >
      <div ref="dialogRef" class="relative flex h-full w-full max-w-6xl items-center justify-center">
        <button
          type="button"
          class="absolute right-0 top-0 z-10 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--nv-on-media)_12%,transparent)] p-2 text-[var(--nv-on-media)] transition hover:bg-[color-mix(in_srgb,var(--nv-on-media)_20%,transparent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-on-media)]"
          :aria-label="$t('common.close')"
          @click="close"
        >
          <X class="h-5 w-5" aria-hidden="true" />
        </button>

        <button
          v-if="hasMultipleImages"
          type="button"
          class="absolute left-0 z-10 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--nv-on-media)_12%,transparent)] p-2 text-[var(--nv-on-media)] transition hover:bg-[color-mix(in_srgb,var(--nv-on-media)_20%,transparent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-on-media)]"
          :aria-label="t('common.previous')"
          @click="go(-1)"
        >
          <ChevronLeft class="h-6 w-6" aria-hidden="true" />
        </button>

        <img
          v-if="currentImage"
          :src="currentImage.src"
          :alt="currentImage.alt || title"
          class="lightbox-image max-w-full touch-none select-none rounded-md object-contain shadow-2xl transition-transform duration-150"
          :style="imageTransform"
          draggable="false"
          @dblclick="toggleZoom"
          @pointerdown="handleImagePointerDown"
          @pointermove="handleImagePointerMove"
          @pointerup="handleImagePointerUp"
          @pointercancel="handleImagePointerUp"
        >

        <button
          v-if="hasMultipleImages"
          type="button"
          class="absolute right-0 z-10 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--nv-on-media)_12%,transparent)] p-2 text-[var(--nv-on-media)] transition hover:bg-[color-mix(in_srgb,var(--nv-on-media)_20%,transparent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-on-media)]"
          :aria-label="t('common.next')"
          @click="go(1)"
        >
          <ChevronRight class="h-6 w-6" aria-hidden="true" />
        </button>

        <div
          v-if="hasMultipleImages"
          class="absolute bottom-0 rounded-full bg-[color-mix(in_srgb,var(--nv-on-media)_12%,transparent)] px-3 py-1 text-xs font-medium text-[var(--nv-on-media)]"
        >
          {{ counterLabel }}
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.lightbox-image {
  max-height: 88vh;
  max-height: 88dvh;
}
</style>

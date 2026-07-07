<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { useEventListener } from '@/composables/useEventListener'
import { useFocusTrap } from '@/composables/useFocusTrap'

const props = withDefaults(defineProps<{
  isOpen: boolean
  images: string[]
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
const currentIndex = ref(0)
const { trapFocus, restoreFocus } = useFocusTrap(dialogRef, () => props.isOpen)
let previousBodyOverflow: string | null = null

const hasMultipleImages = computed(() => props.images.length > 1)
const currentImage = computed(() => props.images[currentIndex.value] ?? '')
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
  emit('update:index', nextIndex)
}

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

function lockScroll() {
  if (previousBodyOverflow !== null) return
  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
}

function unlockScroll() {
  if (previousBodyOverflow === null) return
  document.body.style.overflow = previousBodyOverflow
  previousBodyOverflow = null
}

watch(() => props.isOpen, async (isOpen) => {
  if (isOpen) {
    currentIndex.value = clampIndex(props.initialIndex)
    lockScroll()
    await nextTick()
    trapFocus()
  } else {
    unlockScroll()
    restoreFocus()
  }
}, { immediate: true })

watch(() => props.initialIndex, (index) => {
  if (props.isOpen) {
    currentIndex.value = clampIndex(index)
  }
})

useEventListener(() => document, 'keydown', handleKeydown)

onUnmounted(() => {
  unlockScroll()
  restoreFocus()
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-[80] flex items-center justify-center bg-black/85 p-4"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      @click.self="close"
    >
      <div ref="dialogRef" class="relative flex h-full w-full max-w-6xl items-center justify-center">
        <button
          type="button"
          class="absolute right-0 top-0 z-10 rounded-full bg-white/12 p-2 text-white transition hover:bg-white/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
          :aria-label="$t('common.close')"
          @click="close"
        >
          <X class="h-5 w-5" aria-hidden="true" />
        </button>

        <button
          v-if="hasMultipleImages"
          type="button"
          class="absolute left-0 z-10 rounded-full bg-white/12 p-2 text-white transition hover:bg-white/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
          aria-label="Previous image"
          @click="go(-1)"
        >
          <ChevronLeft class="h-6 w-6" aria-hidden="true" />
        </button>

        <img
          v-if="currentImage"
          :src="currentImage"
          :alt="title"
          class="max-h-[88vh] max-w-full select-none rounded-md object-contain shadow-2xl"
          draggable="false"
        >

        <button
          v-if="hasMultipleImages"
          type="button"
          class="absolute right-0 z-10 rounded-full bg-white/12 p-2 text-white transition hover:bg-white/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
          aria-label="Next image"
          @click="go(1)"
        >
          <ChevronRight class="h-6 w-6" aria-hidden="true" />
        </button>

        <div
          v-if="hasMultipleImages"
          class="absolute bottom-0 rounded-full bg-white/12 px-3 py-1 text-xs font-medium text-white"
        >
          {{ counterLabel }}
        </div>
      </div>
    </div>
  </Teleport>
</template>

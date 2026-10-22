<script setup lang="ts">
import { vImageState } from '@/directives/imageState'
import { computed, nextTick, ref, toRef, watch } from 'vue'
import { ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useEventListener } from '@/composables/useEventListener'
import { useDialogLifecycle } from '@/composables/useDialogLifecycle'
import { useLightboxGestures } from '@/composables/useLightboxGestures'
import { isComposingKeyboardEvent } from '@/utils/keyboard'

const props = withDefaults(defineProps<{
  isOpen: boolean
  images: Array<{
    src: string
    alt: string
    width?: number
    height?: number
    id?: string
    pending?: boolean
    failed?: boolean
    requestLoad?: (retry?: boolean) => void
    onLoaded?: () => void
  }>
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
const overlayRef = ref<HTMLElement | null>(null)
const { t } = useI18n()
const currentIndex = ref(0)
const retryRevision = ref(0)
const retrying = ref(false)
const imageState = ref<'loading' | 'loaded' | 'error'>('loading')
const retryButton = ref<HTMLButtonElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)

const hasMultipleImages = computed(() => props.images.length > 1)
const currentImage = computed(() => props.images[currentIndex.value] ?? null)
const imagePending = computed(() => Boolean(currentImage.value?.pending
  || (currentImage.value?.requestLoad && !currentImage.value.src && !currentImage.value.failed)))
const counterLabel = computed(() => `${currentIndex.value + 1} / ${props.images.length}`)
const imageKey = computed(() => JSON.stringify([currentIndex.value, currentImage.value?.id, currentImage.value?.src, retryRevision.value]))
const imageSizeStyle = computed(() => {
  const { width, height } = currentImage.value ?? {}
  if (!width || !height || !Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return undefined
  return { width: `min(${width}px, 100%, ${88 * width / height}dvh)`, height: 'auto', aspectRatio: `${width} / ${height}` }
})
const statusLabel = computed(() => {
  if (!props.isOpen || !currentImage.value) return ''
  const values = { current: currentIndex.value + 1, total: props.images.length }
  if (imageState.value === 'loaded') return t('common.imageViewerLoaded', values)
  if (imageState.value === 'error') return t('common.imageViewerError', values)
  return t('common.imageViewerLoading', values)
})

function onImageStateChange(event: Event) {
  const image = event.currentTarget as HTMLImageElement
  if (!props.isOpen || image.dataset.viewerKey !== imageKey.value) return
  const state = image.dataset.imageState
  if (state !== 'loading' && state !== 'loaded' && state !== 'error') return
  // A directive initializes before applying its explicit API pending/failed options.
  if (currentImage.value?.failed ? state !== 'error' : imagePending.value && state !== 'loading') return
  imageState.value = state
  if (state === 'loaded') currentImage.value?.onLoaded?.()
  if (state !== 'loading') {
    const restoreFocus = state === 'loaded' && document.activeElement === retryButton.value
    retrying.value = false
    if (restoreFocus) {
      const key = imageKey.value
      void nextTick(() => {
        if (props.isOpen && imageKey.value === key) closeButton.value?.focus({ preventScroll: true })
      })
    }
  }
}

function retryImage() {
  if (!props.isOpen || imageState.value !== 'error' || retrying.value) return
  retrying.value = true
  imageState.value = 'loading'
  retryRevision.value += 1
  currentImage.value?.requestLoad?.(true)
}
function clampIndex(index: number) {
  if (props.images.length === 0) return 0
  return Math.min(Math.max(index, 0), props.images.length - 1)
}

function close() {
  emit('close')
}

const { isTopDialog } = useDialogLifecycle({
  isOpen: toRef(props, 'isOpen'),
  dialogRef,
  overlayRef,
  close,
})

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
  handleImagePointerCancel,
  toggleZoom,
} = useLightboxGestures({ close, go })

function isImageReady(event: Event) {
  return (event.currentTarget as HTMLImageElement).dataset.imageState === 'loaded'
}

function handleZoom(event: Event) {
  if (!isImageReady(event)) return
  toggleZoom()
}

function handleKeydown(event: KeyboardEvent) {
  if (!props.isOpen || !isTopDialog.value || event.defaultPrevented || isComposingKeyboardEvent(event)
    || event.altKey || event.ctrlKey || event.metaKey) return
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

watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    currentIndex.value = clampIndex(props.initialIndex)
    resetImageTransform()
  }
}, { immediate: true })

watch(() => props.initialIndex, (index) => {
  if (props.isOpen) {
    currentIndex.value = clampIndex(index)
    resetImageTransform()
  }
})

watch([() => props.isOpen, currentIndex, () => currentImage.value?.id ?? currentImage.value?.src], () => {
  const restoreFocus = document.activeElement === retryButton.value
  resetImageTransform()
  imageState.value = 'loading'
  retrying.value = false
  if (restoreFocus && props.isOpen) {
    void nextTick(() => {
      if (props.isOpen) closeButton.value?.focus({ preventScroll: true })
    })
  }
}, { flush: 'sync' })

watch([() => props.isOpen, currentIndex, () => currentImage.value?.id ?? currentImage.value?.src], () => {
  if (props.isOpen) currentImage.value?.requestLoad?.()
}, { immediate: true })

useEventListener(() => document, 'keydown', handleKeydown)

</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen"
      ref="overlayRef"
      class="nv-dialog-overlay nv-dialog-overlay--media flex items-center justify-center p-4"
      role="dialog"
      :aria-modal="isTopDialog ? 'true' : undefined"
      :aria-hidden="isTopDialog ? undefined : 'true'"
      :inert="isTopDialog ? undefined : true"
      :aria-label="title"
      @click.self="close"
    >
      <div ref="dialogRef" class="nv-dialog-surface nv-dialog-surface--media relative flex h-full w-full max-w-6xl items-center justify-center">
        <button
          ref="closeButton"
          type="button"
          class="absolute right-0 top-0 z-10 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[var(--nv-media-control-bg)] p-2 text-[var(--nv-on-media)] transition hover:brightness-90 active:brightness-75 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-on-media)]"
          :aria-label="$t('common.close')"
          @click="close"
        >
          <X class="h-5 w-5" aria-hidden="true" />
        </button>

        <button
          v-if="hasMultipleImages"
          type="button"
          class="absolute left-0 z-10 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[var(--nv-media-control-bg)] p-2 text-[var(--nv-on-media)] transition hover:brightness-90 active:brightness-75 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-on-media)]"
          :aria-label="t('common.previous')"
          @click="go(-1)"
        >
          <ChevronLeft class="h-6 w-6" aria-hidden="true" />
        </button>

        <img v-image-state="{ pending: imagePending, failed: currentImage.failed }"
          v-if="currentImage"
          :key="imageKey"
          :data-viewer-key="imageKey"
          :src="currentImage.src || undefined"
          :alt="currentImage.alt || title"
          :width="imageSizeStyle ? currentImage.width : undefined"
          :height="imageSizeStyle ? currentImage.height : undefined"
          class="lightbox-image max-w-full touch-none select-none rounded-md object-contain shadow-2xl transition-transform duration-150"
          :style="[imageTransform, imageSizeStyle]"
          draggable="false"
          @dblclick="handleZoom"
          @pointerdown="isImageReady($event) && handleImagePointerDown($event)"
          @pointermove="handleImagePointerMove"
          @pointerup="handleImagePointerUp"
          @pointercancel="handleImagePointerCancel"
          @lostpointercapture="handleImagePointerCancel"
          @imagestatechange="onImageStateChange"
        >

        <div class="absolute bottom-10 flex flex-col items-center justify-center gap-3">
          <button
            v-if="imageState === 'error' || retrying"
            ref="retryButton"
            type="button"
            class="min-h-11 rounded-md border border-current bg-[var(--nv-media-control-bg)] px-4 py-2 text-sm text-[var(--nv-on-media)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
            :class="retrying ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:brightness-90 active:brightness-75'"
            :aria-disabled="retrying ? 'true' : undefined"
            @click="retryImage"
          >
            {{ retrying ? t('common.imageRetrying') : t('common.imageRetry') }}
          </button>
        </div>
        <span role="status" aria-live="polite" aria-atomic="true" class="sr-only">{{ statusLabel }}</span>

        <button
          v-if="hasMultipleImages"
          type="button"
          class="absolute right-0 z-10 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[var(--nv-media-control-bg)] p-2 text-[var(--nv-on-media)] transition hover:brightness-90 active:brightness-75 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-on-media)]"
          :aria-label="t('common.next')"
          @click="go(1)"
        >
          <ChevronRight class="h-6 w-6" aria-hidden="true" />
        </button>

        <div
          v-if="hasMultipleImages"
          class="absolute bottom-0 rounded-full bg-[var(--nv-media-control-bg)] px-3 py-1 text-xs font-medium text-[var(--nv-on-media)]"
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

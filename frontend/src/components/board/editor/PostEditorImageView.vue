<script setup lang="ts">
import { vImageState } from '@/directives/imageState'
import { computed, inject, nextTick, onBeforeUnmount, ref, watch, type CSSProperties } from 'vue'
import { getPostEditorImageQueue } from '@/features/board/posts/editor/postEditorImageQueue'
import { postEditorImageReplacementKey } from '@/features/board/posts/editor/usePostEditorImageReplacement'
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3'
import { AlignCenter, AlignLeft, AlignRight, Undo2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import { resolveAuthenticatedFileRequestPath } from '@/utils/authenticatedFile'
import { resolveLegacyPostEditorImageCssWidth } from '@/utils/postEditorImageLayout'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { isComposingKeyboardEvent } from '@/utils/keyboard'

type ImageAlignment = 'inline' | 'left' | 'center' | 'right'

const MINIMUM_IMAGE_WIDTH_PX = 80
const MINIMUM_IMAGE_WIDTH_PERCENT = 25

const props = defineProps(nodeViewProps)
const { t } = useI18n()
const replacement = inject(postEditorImageReplacementKey, null)
const showDescription = ref(false)
const descriptionAlt = ref('')
const descriptionCaption = ref('')

function openDescription() {
  descriptionAlt.value = String(props.node.attrs.alt ?? '')
  descriptionCaption.value = String(props.node.attrs.caption ?? '')
  showDescription.value = true
}

function saveDescription() {
  if (!props.editor.isEditable || typeof props.getPos() !== 'number') return
  props.updateAttributes({ alt: descriptionAlt.value, caption: descriptionCaption.value || null })
  void closeDescription()
}

async function closeDescription() {
  showDescription.value = false
  await nextTick()
  const position = props.getPos()
  if (!props.editor.isDestroyed && typeof position === 'number') {
    props.editor.chain().focus().setNodeSelection(position).run()
  }
}

function onDescriptionEnter(event: KeyboardEvent) {
  if (isComposingKeyboardEvent(event)) return
  event.preventDefault()
  saveDescription()
}
const resizingWidthPercent = ref<number | null>(null)
const imageControlsRef = ref<HTMLElement | null>(null)
const controlsShift = ref(0)
const controlsMaxWidth = ref<number | null>(null)
const measuredImageWidthPercent = ref<number | null>(null)
const measuredContainerWidth = ref<number | null>(null)

const authenticatedImageUrl = ref<string | null>(null)
const imagePending = ref(false)
const imageFailed = ref(false)
const imageRetrying = ref(false)
const imageGeneration = ref(0)
const imageRef = ref<HTMLImageElement | null>(null)
const imageQueue = getPostEditorImageQueue(props.editor)
let imageVisibilityObserver: IntersectionObserver | null = null
const retryButtonRef = ref<InstanceType<typeof BaseButton> | null>(null)
const imageSource = computed(() => String(props.node.attrs.src ?? ''))
const imageRequestPath = computed(() => resolveAuthenticatedFileRequestPath(imageSource.value))
const displayedImageSource = computed(() => imageRequestPath.value
  ? authenticatedImageUrl.value ?? undefined
  : imageSource.value || undefined)
let imageController: AbortController | null = null
let imageScope: AuthSessionScope | null = null
let imageDisposed = false

function releaseAuthenticatedImage() {
  imageGeneration.value += 1
  imageVisibilityObserver?.disconnect()
  imageVisibilityObserver = null
  imageController?.abort()
  imageController = null
  imageScope?.cancel()
  imageScope?.dispose()
  imageScope = null
  if (authenticatedImageUrl.value) URL.revokeObjectURL(authenticatedImageUrl.value)
  authenticatedImageUrl.value = null
  imagePending.value = false
  imageFailed.value = false
  imageRetrying.value = false
}

async function loadAuthenticatedImage(isRetry = false) {
  releaseAuthenticatedImage()
  imageRetrying.value = isRetry
  const path = imageRequestPath.value
  if (!path) return
  imagePending.value = true
  const scope = createAuthSessionScope(useAuthStore())
  imageScope = scope
  const controller = new AbortController()
  imageController = controller
  if (!isRetry && props.node.attrs.loading !== 'eager') {
    await nextTick()
    if (controller.signal.aborted || !scope.isCurrent()) return
    const image = imageRef.value
    const Observer = image?.ownerDocument.defaultView?.IntersectionObserver
    if (image && Observer) {
      const observer = new Observer((entries) => {
        if (imageVisibilityObserver !== observer || !entries.some(entry => entry.isIntersecting)) return
        observer.disconnect()
        imageVisibilityObserver = null
        void downloadAuthenticatedImage(path, scope, controller, false)
      }, { rootMargin: '600px' })
      imageVisibilityObserver = observer
      observer.observe(image)
      return
    }
  }
  await downloadAuthenticatedImage(path, scope, controller, isRetry)
}

async function downloadAuthenticatedImage(path: string, scope: AuthSessionScope, controller: AbortController, priority: boolean) {
  try {
    const response = await imageQueue.run(async () => {
      scope.assertCurrent()
      const { default: api } = await import('@/api')
      scope.assertCurrent()
      return api.get<Blob>(path, scope.withRequestConfig({
        responseType: 'blob',
        signal: controller.signal,
        skipGlobalErrorHandler: true,
      }))
    }, scope.requestConfig.signal, priority)
    if (!controller.signal.aborted && scope.isCurrent()) {
      authenticatedImageUrl.value = URL.createObjectURL(response.data)
    }
  } catch {
    if (!controller.signal.aborted && scope.isCurrent()) imageFailed.value = true
  } finally {
    scope.dispose()
    if (imageScope === scope) imageScope = null
    if (imageController === controller) {
      imageController = null
      imagePending.value = false
      if (imageFailed.value) imageRetrying.value = false
    }
  }
}

function handleImageResult(event: Event) {
  const image = event.target
  if (!(image instanceof HTMLImageElement)
    || image.dataset.requestGeneration !== String(imageGeneration.value)
    || imagePending.value || !displayedImageSource.value
    || image.getAttribute('src') !== displayedImageSource.value) return
  const state = event.type === 'error' ? 'error'
    : event.type === 'load' && image.naturalWidth > 0 ? 'loaded' : image.dataset.imageState
  if (state !== 'loaded' && state !== 'error') return
  if (state === 'loaded' && imageRetrying.value && retryButtonRef.value?.$el === document.activeElement) {
    const position = props.getPos()
    if (!props.editor.isDestroyed && typeof position === 'number') {
      props.editor.commands.setNodeSelection(position)
      props.editor.view.focus()
    }
  }
  imageFailed.value = state === 'error'
  imageRetrying.value = false
}

function retryImage() {
  if (imagePending.value || imageRetrying.value || !imageFailed.value) return
  void loadAuthenticatedImage(true)
}

watch(imageSource, () => { void loadAuthenticatedImage() }, { immediate: true, flush: 'sync' })
const stopImageSessionBoundary = subscribeAuthSessionBoundary(() => {
  releaseAuthenticatedImage()
  const generation = imageGeneration.value
  // Capture the new session after the boundary notification finishes; scopes
  // registered during the notification would be cancelled by that same event.
  void nextTick(() => {
    if (!imageDisposed && imageGeneration.value === generation) void loadAuthenticatedImage()
  })
})

let removeResizeListeners: (() => void) | null = null
let removeControlPositionListeners: (() => void) | null = null
let controlPositionFrame: number | null = null
let controlPositionObserver: ResizeObserver | null = null

const imageAlignment = computed<ImageAlignment>(() => {
  const alignment = props.node.attrs.alignment
  return alignment === 'left' || alignment === 'center' || alignment === 'right'
    ? alignment
    : 'inline'
})

const imageWidthPercent = computed(() => {
  if (resizingWidthPercent.value != null) return resizingWidthPercent.value
  const styleWidth = String(props.node.attrs.styleWidth ?? '')
  if (!styleWidth.endsWith('%')) return null
  const percent = Number.parseFloat(styleWidth)
  return Number.isFinite(percent) && percent > 0 && percent <= 100 ? percent : null
})

const legacyImageCssWidth = computed(() => resolveLegacyPostEditorImageCssWidth(props.node.attrs.width))

const currentImageWidthPercent = computed(() => {
  if (imageWidthPercent.value != null) return imageWidthPercent.value
  return measuredImageWidthPercent.value ?? fallbackImageWidthPercent()
})

const wrapperStyle = computed(() => {
  if (imageWidthPercent.value != null) return { width: `${imageWidthPercent.value}%` }
  const styleWidth = String(props.node.attrs.styleWidth ?? '')
  if (styleWidth) return { width: styleWidth }
  return legacyImageCssWidth.value == null ? undefined : { width: legacyImageCssWidth.value }
})

const hasIntrinsicImageWidth = computed(() => wrapperStyle.value == null)

const minimumImageWidthPercent = computed(() => {
  const containerWidth = measuredContainerWidth.value ?? editorContentWidth()
  return minimumWidthPercent(containerWidth)
})

const accessibleMinimumImageWidthPercent = computed(() => {
  return Math.min(minimumImageWidthPercent.value, currentImageWidthPercent.value)
})

const controlsStyle = computed(() => ({
  maxWidth: controlsMaxWidth.value == null ? undefined : `${controlsMaxWidth.value}px`,
  '--post-editor-image-controls-shift': `${controlsShift.value}px`,
} as CSSProperties))

function editorContentWidth(): number {
  const editorRoot = props.editor.view.dom
  const computedStyle = getComputedStyle(editorRoot)
  const horizontalPadding = Number.parseFloat(computedStyle.paddingLeft || '0')
    + Number.parseFloat(computedStyle.paddingRight || '0')
  const width = editorRoot.clientWidth - horizontalPadding
  return width > 0 ? width : 1
}

function currentImageNode(): HTMLElement | null {
  return imageControlsRef.value?.closest<HTMLElement>('.post-editor-image-node') ?? null
}

function imageContainerWidth(imageNode = currentImageNode()): number {
  const parentWidth = imageNode?.parentElement?.getBoundingClientRect().width ?? 0
  return parentWidth > 0 ? parentWidth : editorContentWidth()
}

function fallbackImageWidthPercent(): number {
  const containerWidth = measuredContainerWidth.value ?? editorContentWidth()
  const styleWidth = String(props.node.attrs.styleWidth ?? '')
  if (styleWidth.toLowerCase().endsWith('px')) {
    return normalizePercent(Math.min(100, (Number.parseFloat(styleWidth) / containerWidth) * 100))
  }
  const legacyWidth = legacyImageCssWidth.value ?? ''
  if (legacyWidth.endsWith('%')) {
    return normalizePercent(Math.min(100, Number.parseFloat(legacyWidth)))
  }
  if (legacyWidth.toLowerCase().endsWith('px')) {
    return normalizePercent(Math.min(100, (Number.parseFloat(legacyWidth) / containerWidth) * 100))
  }
  return 100
}

function measureImageWidthPercent(imageNode = currentImageNode()): number {
  if (imageWidthPercent.value != null) return imageWidthPercent.value
  const nodeWidth = imageNode?.getBoundingClientRect().width ?? 0
  const containerWidth = imageContainerWidth(imageNode)
  if (nodeWidth > 0) {
    return normalizePercent(Math.min(100, (nodeWidth / containerWidth) * 100))
  }
  return fallbackImageWidthPercent()
}

function refreshImageMeasurements() {
  const imageNode = currentImageNode()
  measuredContainerWidth.value = imageContainerWidth(imageNode)
  measuredImageWidthPercent.value = measureImageWidthPercent(imageNode)
}

function setImageWidth(percent: number | null) {
  if (percent == null) {
    props.updateAttributes({ styleWidth: null, width: null, height: null })
    return
  }

  const normalizedPercent = clampImageWidthPercent(percent)
  props.updateAttributes({
    styleWidth: `${normalizedPercent}%`,
    width: null,
    height: null,
  })
}

function setImageAlignment(alignment: ImageAlignment) {
  props.updateAttributes({ alignment })
}

function assignImageControls(value: unknown) {
  const controls = value instanceof HTMLElement ? value : null
  if (imageControlsRef.value === controls) return
  imageControlsRef.value = controls
  measuredImageWidthPercent.value = null
  measuredContainerWidth.value = null
  refreshControlPositionObserverTargets()
  scheduleControlPosition()
}

function selectImage() {
  const position = props.getPos()
  if (typeof position === 'number') {
    props.editor.commands.setNodeSelection(position)
  }
}

function startResize(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()

  cancelActiveResize()
  const imageNode = (event.currentTarget as HTMLElement).closest<HTMLElement>('.post-editor-image-node')
  const startWidth = imageNode?.getBoundingClientRect().width ?? editorContentWidth()
  const startX = event.clientX
  const pointerId = event.pointerId
  const maxWidth = imageContainerWidth(imageNode)
  measuredContainerWidth.value = maxWidth

  const handlePointerMove = (moveEvent: PointerEvent) => {
    if (moveEvent.pointerId !== pointerId) return
    const delta = moveEvent.clientX - startX
    const nextWidth = imageAlignment.value === 'center'
      ? startWidth + (delta * 2)
      : imageAlignment.value === 'right'
        ? startWidth - delta
        : startWidth + delta
    resizingWidthPercent.value = clampImageWidthPercent((nextWidth / maxWidth) * 100, maxWidth)
    scheduleControlPosition()
  }

  const finishResize = () => {
    const percent = resizingWidthPercent.value
    cancelActiveResize()
    if (percent != null) {
      props.updateAttributes({ styleWidth: `${percent}%`, width: null, height: null })
    }
  }

  const handlePointerEnd = (endEvent: PointerEvent) => {
    if (endEvent.pointerId !== pointerId) return
    finishResize()
  }

  const handlePointerCancel = (cancelEvent: PointerEvent) => {
    if (cancelEvent.pointerId !== pointerId) return
    cancelActiveResize()
  }

  document.addEventListener('pointermove', handlePointerMove)
  document.addEventListener('pointerup', handlePointerEnd)
  document.addEventListener('pointercancel', handlePointerCancel)
  window.addEventListener('blur', cancelActiveResize)
  removeResizeListeners = () => {
    document.removeEventListener('pointermove', handlePointerMove)
    document.removeEventListener('pointerup', handlePointerEnd)
    document.removeEventListener('pointercancel', handlePointerCancel)
    window.removeEventListener('blur', cancelActiveResize)
    removeResizeListeners = null
  }
}

function cancelActiveResize() {
  resizingWidthPercent.value = null
  removeResizeListeners?.()
}

function resizeWithKeyboard(event: KeyboardEvent) {
  const imageNode = currentImageNode()
  const maxWidth = imageContainerWidth(imageNode)
  const currentPercent = measureImageWidthPercent(imageNode)
  measuredContainerWidth.value = maxWidth
  measuredImageWidthPercent.value = currentPercent
  let nextPercent: number | null = null

  if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
    nextPercent = currentPercent - 5
  } else if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
    nextPercent = currentPercent + 5
  } else if (event.key === 'Home') {
    nextPercent = minimumWidthPercent(maxWidth)
  } else if (event.key === 'End') {
    nextPercent = 100
  }

  if (nextPercent == null) return
  event.preventDefault()
  event.stopPropagation()
  props.updateAttributes({
    styleWidth: `${clampImageWidthPercent(nextPercent, maxWidth)}%`,
    width: null,
    height: null,
  })
}

function normalizePercent(value: number): number {
  return Math.round(value * 10) / 10
}

function minimumWidthPercent(containerWidth: number): number {
  const safeContainerWidth = containerWidth > 0 ? containerWidth : 1
  const pixelMinimumPercent = (Math.min(MINIMUM_IMAGE_WIDTH_PX, safeContainerWidth) / safeContainerWidth) * 100
  return normalizePercent(Math.min(MINIMUM_IMAGE_WIDTH_PERCENT, pixelMinimumPercent))
}

function clampImageWidthPercent(
  percent: number,
  containerWidth = measuredContainerWidth.value ?? editorContentWidth(),
): number {
  return normalizePercent(Math.min(100, Math.max(minimumWidthPercent(containerWidth), percent)))
}

function updateControlPosition() {
  const controls = imageControlsRef.value
  if (!controls || !props.selected) return
  const imageNode = controls.closest<HTMLElement>('.post-editor-image-node')
  if (!imageNode) return
  refreshImageMeasurements()

  const editorRect = props.editor.view.dom.getBoundingClientRect()
  const viewportLeft = 8
  const viewportRight = window.innerWidth - 8
  const minLeft = Math.max(editorRect.left + 8, viewportLeft)
  const maxRight = Math.min(editorRect.right - 8, viewportRight)
  const availableWidth = Math.max(1, maxRight - minLeft)
  controlsMaxWidth.value = availableWidth
  controls.style.maxWidth = `${availableWidth}px`

  const controlsRect = controls.getBoundingClientRect()
  const unshiftedLeft = controlsRect.left - controlsShift.value
  const unshiftedRight = controlsRect.right - controlsShift.value
  if (unshiftedLeft < minLeft) {
    controlsShift.value = minLeft - unshiftedLeft
  } else if (unshiftedRight > maxRight) {
    controlsShift.value = maxRight - unshiftedRight
  } else {
    controlsShift.value = 0
  }
}

function scheduleControlPosition() {
  if (controlPositionFrame != null) return
  controlPositionFrame = window.requestAnimationFrame(() => {
    controlPositionFrame = null
    updateControlPosition()
  })
}

function refreshControlPositionObserverTargets() {
  if (!controlPositionObserver) return
  controlPositionObserver.disconnect()

  const controls = imageControlsRef.value
  const imageNode = controls?.closest<HTMLElement>('.post-editor-image-node')
  const targets = new Set<HTMLElement>([props.editor.view.dom])
  if (controls) targets.add(controls)
  if (imageNode) targets.add(imageNode)
  if (imageNode?.parentElement) targets.add(imageNode.parentElement)
  targets.forEach((element) => controlPositionObserver?.observe(element))
}

function startControlPositioning() {
  if (removeControlPositionListeners) return
  window.addEventListener('resize', scheduleControlPosition)
  window.addEventListener('scroll', scheduleControlPosition, true)
  if (typeof ResizeObserver !== 'undefined') {
    controlPositionObserver = new ResizeObserver(() => {
      refreshImageMeasurements()
      scheduleControlPosition()
    })
    refreshControlPositionObserverTargets()
  }
  removeControlPositionListeners = () => {
    window.removeEventListener('resize', scheduleControlPosition)
    window.removeEventListener('scroll', scheduleControlPosition, true)
    controlPositionObserver?.disconnect()
    controlPositionObserver = null
    removeControlPositionListeners = null
  }
  void nextTick(scheduleControlPosition)
}

function stopControlPositioning() {
  cancelActiveResize()
  removeControlPositionListeners?.()
  if (controlPositionFrame != null) {
    window.cancelAnimationFrame(controlPositionFrame)
    controlPositionFrame = null
  }
  controlsShift.value = 0
  controlsMaxWidth.value = null
  measuredImageWidthPercent.value = null
  measuredContainerWidth.value = null
}

watch(() => props.selected, (selected) => {
  if (selected) startControlPositioning()
  else stopControlPositioning()
}, { immediate: true })
watch([imageAlignment, wrapperStyle], () => {
  measuredImageWidthPercent.value = null
  measuredContainerWidth.value = null
  scheduleControlPosition()
})

onBeforeUnmount(() => {
  imageDisposed = true
  stopImageSessionBoundary()
  releaseAuthenticatedImage()
  stopControlPositioning()
})
</script>

<template>
  <NodeViewWrapper
    as="span"
    class="post-editor-image-node"
    :class="[
      `post-editor-image-node--${imageAlignment}`,
      { 'post-editor-image-node--intrinsic': hasIntrinsicImageWidth },
      { 'post-editor-image-node--selected': props.selected },
    ]"
    :style="wrapperStyle"
  >
    <img
      ref="imageRef"
      :key="`image-${imageGeneration}`"
      v-image-state="{ pending: imagePending, failed: imageFailed }"
      :data-request-generation="imageGeneration"
      :src="displayedImageSource"
      :alt="props.node.attrs.alt ?? ''"
      :title="props.node.attrs.title ?? undefined"
      :loading="props.node.attrs.loading ?? undefined"
      class="tiptap-image-inline max-w-full h-auto align-baseline"
      :class="imageAlignment === 'inline' ? undefined : `tiptap-image-align-${imageAlignment}`"
      data-drag-handle
      @click.stop="selectImage"
      @load="handleImageResult"
      @error="handleImageResult"
      @imagestatechange="handleImageResult"
    >
    <span v-if="imageFailed || imageRetrying" contenteditable="false" class="block py-2" @mousedown.prevent.stop @click.stop>
      <BaseButton
        ref="retryButtonRef"
        size="sm"
        variant="secondary"
        :aria-disabled="imageRetrying || undefined"
        :aria-busy="imageRetrying || undefined"
        @click="retryImage"
      >
        {{ imageRetrying ? t('common.imageRetrying') : t('common.imageRetry') }}
      </BaseButton>
    </span>
    <span v-if="props.node.attrs.caption" class="post-editor-image-caption" contenteditable="false" @click.stop="selectImage">{{ props.node.attrs.caption }}</span>

    <span
      v-if="props.selected"
      :ref="assignImageControls"
      class="post-editor-image-controls"
      :style="controlsStyle"
      contenteditable="false"
      @mousedown.prevent.stop
      @click.stop
    >
      <button
        v-for="percent in [25, 50, 75, 100]"
        :key="percent"
        type="button"
        class="post-editor-image-control post-editor-image-control--size"
        :title="t('board.writePost.imageControls.sizePercent', { percent })"
        :aria-label="t('board.writePost.imageControls.sizePercent', { percent })"
        :data-image-width="percent"
        @click="setImageWidth(percent)"
      >
        {{ percent }}%
      </button>
      <button
        type="button"
        class="post-editor-image-control post-editor-image-control--size"
        :title="t('board.writePost.imageControls.originalSize')"
        :aria-label="t('board.writePost.imageControls.originalSize')"
        data-image-width="original"
        @click="setImageWidth(null)"
      >
        {{ t('board.writePost.imageControls.original') }}
      </button>

      <span class="post-editor-image-control-divider" aria-hidden="true" />
      <button type="button" class="post-editor-image-control post-editor-image-control--size" :disabled="!props.editor.isEditable" @click="openDescription">{{ t('board.writePost.imageControls.description') }}</button>
      <button v-if="replacement" type="button" class="post-editor-image-control post-editor-image-control--size" :disabled="!props.editor.isEditable || replacement.busy.value" @click="replacement.request(props.getPos)">{{ replacement.busy.value ? t('board.writePost.advanced.replacingImage') : t('board.writePost.advanced.replaceImage') }}</button>

      <button
        v-for="item in [
          { alignment: 'left', label: 'alignLeft', icon: AlignLeft },
          { alignment: 'center', label: 'alignCenter', icon: AlignCenter },
          { alignment: 'right', label: 'alignRight', icon: AlignRight },
          { alignment: 'inline', label: 'alignInline', icon: Undo2 },
        ] as const"
        :key="item.alignment"
        type="button"
        class="post-editor-image-control"
        :class="{ 'post-editor-image-control--active': imageAlignment === item.alignment }"
        :title="t(`board.writePost.imageControls.${item.label}`)"
        :aria-label="t(`board.writePost.imageControls.${item.label}`)"
        :aria-pressed="imageAlignment === item.alignment"
        :data-image-align="item.alignment"
        @click="setImageAlignment(item.alignment)"
      >
        <component :is="item.icon" :size="16" aria-hidden="true" />
      </button>
    </span>

    <span
      v-if="props.selected"
      class="post-editor-image-resize-handle"
      :class="{ 'post-editor-image-resize-handle--left': imageAlignment === 'right' }"
      contenteditable="false"
      role="slider"
      tabindex="0"
      :aria-label="t('board.writePost.imageControls.resize')"
      aria-orientation="horizontal"
      :aria-valuemin="accessibleMinimumImageWidthPercent"
      :aria-valuemax="100"
      :aria-valuenow="currentImageWidthPercent"
      @pointerdown="startResize"
      @keydown="resizeWithKeyboard"
    />
    <BaseModal :is-open="showDescription" :title="t('board.writePost.imageControls.description')" size="md" @close="closeDescription">
      <div class="flex flex-col gap-4">
        <BaseInput :model-value="descriptionAlt" :label="t('board.writePost.imageControls.alt')" @update:model-value="descriptionAlt = String($event)" @keydown.enter="onDescriptionEnter" />
        <p class="text-xs nv-text-muted">{{ t('board.writePost.imageControls.altHint') }}</p>
        <BaseInput :model-value="descriptionCaption" :label="t('board.writePost.imageControls.caption')" @update:model-value="descriptionCaption = String($event)" @keydown.enter="onDescriptionEnter" />
      </div>
      <template #footer>
        <BaseButton variant="secondary" @click="closeDescription">{{ t('common.cancel') }}</BaseButton>
        <BaseButton :disabled="!props.editor.isEditable" @click="saveDescription">{{ t('common.save') }}</BaseButton>
      </template>
    </BaseModal>
  </NodeViewWrapper>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch, type CSSProperties } from 'vue'
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3'
import { AlignCenter, AlignLeft, AlignRight, Undo2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { resolveLegacyPostEditorImageCssWidth } from '@/utils/postEditorImageLayout'

type ImageAlignment = 'inline' | 'left' | 'center' | 'right'

const MINIMUM_IMAGE_WIDTH_PX = 80
const MINIMUM_IMAGE_WIDTH_PERCENT = 25

const props = defineProps(nodeViewProps)
const { t } = useI18n()
const resizingWidthPercent = ref<number | null>(null)
const imageControlsRef = ref<HTMLElement | null>(null)
const controlsShift = ref(0)
const controlsMaxWidth = ref<number | null>(null)
const measuredImageWidthPercent = ref<number | null>(null)
const measuredContainerWidth = ref<number | null>(null)

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
      :src="props.node.attrs.src"
      :alt="props.node.attrs.alt ?? ''"
      :title="props.node.attrs.title ?? undefined"
      :loading="props.node.attrs.loading ?? undefined"
      class="tiptap-image-inline max-w-full h-auto align-baseline"
      :class="imageAlignment === 'inline' ? undefined : `tiptap-image-align-${imageAlignment}`"
      data-drag-handle
      @click.stop="selectImage"
    >

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
  </NodeViewWrapper>
</template>

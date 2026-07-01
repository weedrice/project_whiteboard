<script setup lang="ts">
import { computed, onBeforeUnmount, toRef } from 'vue'
import PostEditorColorPopover from '@/components/board/editor/PostEditorColorPopover.vue'
import PostEditorContentArea from '@/components/board/editor/PostEditorContentArea.vue'
import PostEditorImageAltPopover from '@/components/board/editor/PostEditorImageAltPopover.vue'
import PostEditorLinkPopover from '@/components/board/editor/PostEditorLinkPopover.vue'
import PostEditorPopoverMask from '@/components/board/editor/PostEditorPopoverMask.vue'
import PostEditorSlashMenu from '@/components/board/editor/PostEditorSlashMenu.vue'
import PostEditorTablePopover from '@/components/board/editor/PostEditorTablePopover.vue'
import PostEditorToolbar from '@/components/board/editor/PostEditorToolbar.vue'
import { colorLabelKeys, colorPresets, fontSizes, lineHeights, slashActions } from '@/components/board/editor/postEditorOptions'
import '@/components/board/editor/editor.css'
import { useEditorImageUpload } from '@/composables/useEditorImageUpload'
import { focusPostEditorAtPointer, usePostEditorInstance } from '@/composables/usePostEditorInstance'
import { usePostEditorImageAltCommands } from '@/composables/usePostEditorImageAltCommands'
import { usePostEditorImageFiles } from '@/composables/usePostEditorImageFiles'
import { usePostEditorImageUploadState } from '@/composables/usePostEditorImageUploadState'
import { usePostEditorLinkCommands } from '@/composables/usePostEditorLinkCommands'
import { usePostEditorListCommands } from '@/composables/usePostEditorListCommands'
import { usePostEditorPopovers } from '@/composables/usePostEditorPopovers'
import { usePostEditorSlashMenu } from '@/composables/usePostEditorSlashMenu'
import { usePostEditorSlashActions } from '@/composables/usePostEditorSlashActions'
import { usePostEditorTableCommands } from '@/composables/usePostEditorTableCommands'
import { usePostEditorTextCommands } from '@/composables/usePostEditorTextCommands'
import { usePostEditorUploadedImages, type UploadedEditorImage } from '@/composables/usePostEditorUploadedImages'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import type { EmoticonImage } from '@/types/emoticon'
import { IMAGE_UPLOAD_ACCEPT } from '@/utils/imageUploadPolicy'
import logger from '@/utils/logger'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'open-video'): void
  (e: 'open-emoticon'): void
  (e: 'file-uploaded', fileId: number): void
}>()

const { t } = useI18n()
const toastStore = useToastStore()
const themeStore = useThemeStore()
const modelValue = toRef(props, 'modelValue')

const { isUploadingImage, validateImageFile, uploadImage, abortImageUpload, isAbortUploadError } = useEditorImageUpload()
const {
  showColorPanel,
  showLinkPopover,
  showTablePopover,
  showSlashMenu,
  showImageAltPopover,
  slashPopoverRef,
  colorPanelRef,
  linkPopoverRef,
  tablePopoverRef,
  imageAltPopoverRef,
  colorTriggerElement,
  slashPosition,
  colorPosition,
  linkPosition,
  tablePosition,
  imageAltPosition,
  closeFloatingMenus,
} = usePostEditorPopovers()
const {
  slashActiveIndex,
  openSlashMenu,
  toggleSlashMenu,
  moveSlashSelection,
  setSlashSelection,
} = usePostEditorSlashMenu({
  showSlashMenu,
  slashPosition,
  closeFloatingMenus,
})
let openImageAltPopoverFromEditor = (_target: HTMLImageElement, _alt: string, _nodePos: number) => {}
const editor = usePostEditorInstance({
  modelValue,
  onUpdateHtml: (html) => emit('update:modelValue', html),
  openSlashMenu,
  openImageAltPopover: (target, alt, nodePos) => openImageAltPopoverFromEditor(target, alt, nodePos),
})
const {
  imageUploadQueue,
  hasImageUploadError,
  failedImageCount,
  failedImageFiles,
  currentUploadingImageName,
  imageUploadQueueCount,
  retryImageUpload,
  retryFailedImageUpload,
  dismissImageUploadError,
  dismissFailedImageUpload,
  cancelImageUpload,
} = usePostEditorImageUploadState<UploadedEditorImage>({
  validate: validateImageFile,
  upload: uploadImage,
  isAbort: isAbortUploadError,
  onUploaded: (uploaded, file) => {
    insertUploadedImage(uploaded, file)
  },
  onValidationError: (validationError) => {
    reportImageValidationError(validationError)
  },
  onFailed: (error) => {
    logger.error('Image upload failed:', error)
    toastStore.addToast(t('common.messages.uploadFailed'), 'error')
  },
  abort: abortImageUpload,
})

const {
  fileIds,
  insertUploadedImage,
  disposeUploadedImagePreviews,
} = usePostEditorUploadedImages(editor, (fileId) => emit('file-uploaded', fileId))

const {
  currentTextColor,
  isDefaultColor,
  currentFontSize,
  currentLineHeight,
  activeTextAlign,
  applyFontSize,
  applyLineHeight,
  applyHorizontalRule,
  setTextAlign,
} = usePostEditorTextCommands(editor)
const colorPresetLabels = computed(() => Object.fromEntries(
  colorPresets.map((color, index) => [
    color,
    t(`board.writePost.colorLabels.${colorLabelKeys[index]}`),
  ]),
))

const {
  linkUrl,
  linkText,
  openLinkPopover,
  closeLinkPopover,
  applyLink,
  removeLink,
} = usePostEditorLinkCommands({
  editor,
  showLinkPopover,
  linkPosition,
  closeFloatingMenus,
  t,
  addToast: toastStore.addToast,
})

const {
  imageAltText,
  openImageAltPopover,
  closeImageAltPopover,
  applyImageAlt,
  clearImageAlt,
} = usePostEditorImageAltCommands({
  editor,
  showImageAltPopover,
  imageAltPosition,
  closeFloatingMenus,
})

openImageAltPopoverFromEditor = openImageAltPopover

const {
  tableRows,
  tableCols,
  tableHeaderRow,
  openTablePopover,
  closeTablePopover,
  applyTable,
} = usePostEditorTableCommands({
  editor,
  showTablePopover,
  tablePosition,
  closeFloatingMenus,
})

const {
  saveListSelection,
  applyBulletList,
  applyOrderedList,
} = usePostEditorListCommands(editor)

const {
  applySlashAction,
} = usePostEditorSlashActions({
  editor,
  showSlashMenu,
  applyBulletList,
  openLinkPopover,
  openTablePopover,
})

const {
  imageInput,
  isDraggingImage,
  triggerImageUpload,
  onImageChange,
  onEditorPaste,
  onEditorDrop,
  onEditorDragEnter,
  onEditorDragLeave,
} = usePostEditorImageFiles(imageUploadQueue)

function setDefaultColor() {
  editor.value?.chain().focus().unsetColor().run()
  closeColorPanel()
}

function setPresetColor(color: string) {
  editor.value?.chain().focus().setColor(color).run()
  closeColorPanel()
}

function toggleColorPanel(anchor?: HTMLElement) {
  if (showColorPanel.value) {
    closeColorPanel(anchor)
    return
  }
  showSlashMenu.value = false
  slashPosition.clearAnchor()
  colorTriggerElement.value = anchor ?? null
  colorPosition.setAnchor(anchor)
  showColorPanel.value = true
}

function closeColorPanel(focusTarget = colorTriggerElement.value) {
  showColorPanel.value = false
  colorPosition.clearAnchor()
  colorTriggerElement.value = null
  if (focusTarget instanceof HTMLElement) {
    focusTarget.focus()
  }
}

function onContentAreaClick(event: MouseEvent) {
  const instance = editor.value
  if (!instance) return
  focusPostEditorAtPointer(instance, event)
}

function reportImageValidationError(validationError: 'type' | 'size') {
  if (validationError === 'type') {
    toastStore.addToast(t('common.messages.badRequest'), 'warning')
    return
  }
  toastStore.addToast(t('common.messages.fileSizeExceeded'), 'warning')
}

function setVideo(src: string) {
  editor.value?.chain().focus().setVideo({ src }).run()
}

function setEmoticon(image: EmoticonImage) {
  editor.value?.chain().focus().setImage({
    src: image.imageUrl,
    alt: ':emoticon:',
    title: ':emoticon:',
  }).run()
}

defineExpose({
  editor,
  setVideo,
  setEmoticon,
  fileIds,
})

onBeforeUnmount(() => {
  imageUploadQueue.dispose()
  disposeUploadedImagePreviews()
  editor.value?.destroy()
})
</script>

<template>
  <div class="tiptap-editor-wrap flex min-h-0 flex-1 flex-col">
    <input ref="imageInput" type="file" :accept="IMAGE_UPLOAD_ACCEPT" multiple class="hidden" @change="onImageChange">

    <PostEditorToolbar
      v-if="editor"
      :editor="editor"
      :is-uploading-image="isUploadingImage"
      :has-image-upload-error="hasImageUploadError"
      :current-uploading-image-name="currentUploadingImageName"
      :image-upload-queue-count="imageUploadQueueCount"
      :failed-image-count="failedImageCount"
      :failed-image-files="failedImageFiles"
      :font-sizes="fontSizes"
      :line-heights="lineHeights"
      :current-font-size="currentFontSize"
      :current-line-height="currentLineHeight"
      :current-text-color="currentTextColor"
      :is-default-color="isDefaultColor"
      :is-dark="themeStore.isDark"
      :show-slash-menu="showSlashMenu"
      :show-table-popover="showTablePopover"
      :show-color-panel="showColorPanel"
      :active-text-align="activeTextAlign"
      @toggle-bold="editor.chain().focus().toggleBold().run()"
      @toggle-italic="editor.chain().focus().toggleItalic().run()"
      @toggle-underline="editor.chain().focus().toggleUnderline().run()"
      @toggle-strike="editor.chain().focus().toggleStrike().run()"
      @open-link="openLinkPopover"
      @upload-image="triggerImageUpload"
      @open-video="emit('open-video')"
      @open-emoticon="emit('open-emoticon')"
      @save-list-selection="saveListSelection"
      @bullet-list="applyBulletList"
      @ordered-list="applyOrderedList"
      @font-size="applyFontSize"
      @line-height="applyLineHeight"
      @custom-text-color="setPresetColor"
      @toggle-color-panel="toggleColorPanel"
      @align="setTextAlign"
      @toggle-slash-menu="toggleSlashMenu"
      @open-table="openTablePopover"
      @horizontal-rule="applyHorizontalRule"
      @retry-image-upload="retryImageUpload"
      @retry-failed-image-upload="retryFailedImageUpload"
      @cancel-image-upload="cancelImageUpload"
      @dismiss-image-upload-error="dismissImageUploadError"
      @dismiss-failed-image-upload="dismissFailedImageUpload"
    />

    <PostEditorPopoverMask :open="showSlashMenu" @close="showSlashMenu = false">
      <div id="editor-slash-dialog" ref="slashPopoverRef" class="link-popover slash-popover" :style="slashPosition.popoverStyle.value" role="dialog" aria-modal="true" aria-labelledby="editor-slash-dialog-title">
        <div class="mb-3">
          <p class="text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">{{ t('board.writePost.toolbar.slashMenu') }}</p>
          <h3 id="editor-slash-dialog-title" class="text-base font-semibold text-[var(--nv-ink)]">{{ t('board.writePost.toolbar.insertBlock') }}</h3>
        </div>
        <PostEditorSlashMenu
          :actions="slashActions"
          :active-index="slashActiveIndex"
          @select="applySlashAction"
          @move="moveSlashSelection"
          @set-active="setSlashSelection"
          @close="showSlashMenu = false"
        />
      </div>
    </PostEditorPopoverMask>

    <Teleport to="body">
      <div v-if="showColorPanel" id="editor-color-dialog" ref="colorPanelRef" class="color-panel" :style="colorPosition.popoverStyle.value" role="dialog" aria-labelledby="editor-color-dialog-title" @keydown.enter.stop @keydown.escape.stop.prevent="closeColorPanel()">
        <p id="editor-color-dialog-title" class="sr-only">{{ t('board.writePost.toolbar.textColor') }}</p>
        <PostEditorColorPopover
          :colors="colorPresets"
          :labels="colorPresetLabels"
          :current-text-color="currentTextColor"
          :is-default-color="isDefaultColor"
          @default-color="setDefaultColor"
          @preset-color="setPresetColor"
          @custom-color="setPresetColor"
        />
      </div>
    </Teleport>

    <PostEditorPopoverMask :open="showLinkPopover" @close="closeLinkPopover">
      <div ref="linkPopoverRef" class="link-popover" :style="linkPosition.popoverStyle.value" role="dialog" aria-modal="true" aria-labelledby="editor-link-dialog-title">
        <h3 id="editor-link-dialog-title" class="sr-only">{{ t('board.writePost.toolbar.linkDialog') }}</h3>
        <PostEditorLinkPopover
          :url="linkUrl"
          :text="linkText"
          :can-remove="editor?.isActive('link') ?? false"
          @apply="applyLink"
          @close="closeLinkPopover"
          @remove="removeLink"
        />
      </div>
    </PostEditorPopoverMask>

    <PostEditorPopoverMask :open="showImageAltPopover" @close="closeImageAltPopover">
      <div ref="imageAltPopoverRef" class="link-popover image-alt-popover" :style="imageAltPosition.popoverStyle.value" role="dialog" aria-modal="true" aria-labelledby="editor-image-alt-dialog-title">
        <h3 id="editor-image-alt-dialog-title" class="sr-only">{{ t('board.writePost.imageAlt.title') }}</h3>
        <PostEditorImageAltPopover
          :alt="imageAltText"
          @apply="applyImageAlt"
          @clear="clearImageAlt"
          @close="closeImageAltPopover"
        />
      </div>
    </PostEditorPopoverMask>

    <PostEditorPopoverMask :open="showTablePopover" @close="closeTablePopover">
      <div ref="tablePopoverRef" class="link-popover table-popover" :style="tablePosition.popoverStyle.value" role="dialog" aria-modal="true" aria-labelledby="editor-table-dialog-title">
        <h3 id="editor-table-dialog-title" class="sr-only">{{ t('board.writePost.toolbar.tableDialog') }}</h3>
        <PostEditorTablePopover
          :rows="tableRows"
          :cols="tableCols"
          :header-row="tableHeaderRow"
          @update:rows="tableRows = $event"
          @update:cols="tableCols = $event"
          @update:header-row="tableHeaderRow = $event"
          @apply="applyTable"
          @close="closeTablePopover"
        />
      </div>
    </PostEditorPopoverMask>

    <PostEditorContentArea
      :editor="editor"
      :is-dragging-image="isDraggingImage"
      :drop-image-hint="t('board.writePost.dropImageHint')"
      @content-mousedown="onContentAreaClick"
      @content-paste="onEditorPaste"
      @content-drop="onEditorDrop"
      @content-dragenter="onEditorDragEnter"
      @content-dragleave="onEditorDragLeave"
    />
  </div>
</template>

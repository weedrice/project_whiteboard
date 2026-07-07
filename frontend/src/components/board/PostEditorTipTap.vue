<script setup lang="ts">
import { computed, onBeforeUnmount, toRef } from 'vue'
import PostEditorContentArea from '@/components/board/editor/PostEditorContentArea.vue'
import PostEditorFloatingPanels from '@/components/board/editor/PostEditorFloatingPanels.vue'
import PostEditorToolbar from '@/components/board/editor/PostEditorToolbar.vue'
import { codeBlockLanguages, colorLabelKeys, colorPresets, fontSizes, lineHeights, slashActions } from '@/components/board/editor/postEditorOptions'
import '@/components/board/editor/editor.css'
import { useEditorImageUpload } from '@/composables/useEditorImageUpload'
import { focusPostEditorAtPointer, usePostEditorInstance } from '@/features/board/posts/editor/usePostEditorInstance'
import { usePostEditorImageAltCommands } from '@/features/board/posts/editor/usePostEditorImageAltCommands'
import { usePostEditorColorPanel } from '@/features/board/posts/editor/usePostEditorColorPanel'
import { usePostEditorFloatingPanelRefs } from '@/features/board/posts/editor/usePostEditorFloatingPanelRefs'
import { usePostEditorImageFiles } from '@/features/board/posts/editor/usePostEditorImageFiles'
import { usePostEditorImageUploadState } from '@/features/board/posts/editor/usePostEditorImageUploadState'
import { usePostEditorLinkCommands } from '@/features/board/posts/editor/usePostEditorLinkCommands'
import { usePostEditorListCommands } from '@/features/board/posts/editor/usePostEditorListCommands'
import { usePostEditorPopovers } from '@/features/board/posts/editor/usePostEditorPopovers'
import { usePostEditorSlashMenu } from '@/features/board/posts/editor/usePostEditorSlashMenu'
import { usePostEditorSlashActions } from '@/features/board/posts/editor/usePostEditorSlashActions'
import { usePostEditorTableCommands } from '@/features/board/posts/editor/usePostEditorTableCommands'
import { usePostEditorTextCommands } from '@/features/board/posts/editor/usePostEditorTextCommands'
import { usePostEditorUploadedImages, type UploadedEditorImage } from '@/features/board/posts/editor/usePostEditorUploadedImages'
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
const currentCodeBlockLanguage = computed(() => {
  const language = editor.value?.getAttributes('codeBlock').language
  return typeof language === 'string' ? language : ''
})

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
  closeColorPanel,
  setDefaultColor,
  setPresetColor,
  toggleColorPanel,
} = usePostEditorColorPanel({
  editor,
  showColorPanel,
  showSlashMenu,
  slashPosition,
  colorPosition,
  colorTriggerElement,
})

const {
  assignSlashPopover,
  assignColorPanel,
  assignLinkPopover,
  assignImageAltPopover,
  assignTablePopover,
} = usePostEditorFloatingPanelRefs({
  slashPopoverRef,
  colorPanelRef,
  linkPopoverRef,
  imageAltPopoverRef,
  tablePopoverRef,
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

function applyCodeBlockLanguage(language: string) {
  editor.value?.chain().focus().updateAttributes('codeBlock', {
    language: language || null,
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
      :code-block-languages="codeBlockLanguages"
      :current-font-size="currentFontSize"
      :current-line-height="currentLineHeight"
      :current-code-block-language="currentCodeBlockLanguage"
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
      @code-block-language="applyCodeBlockLanguage"
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

    <PostEditorFloatingPanels
      :show-slash-menu="showSlashMenu"
      :show-color-panel="showColorPanel"
      :show-link-popover="showLinkPopover"
      :show-image-alt-popover="showImageAltPopover"
      :show-table-popover="showTablePopover"
      :assign-slash-popover="assignSlashPopover"
      :assign-color-panel="assignColorPanel"
      :assign-link-popover="assignLinkPopover"
      :assign-image-alt-popover="assignImageAltPopover"
      :assign-table-popover="assignTablePopover"
      :slash-position="slashPosition"
      :color-position="colorPosition"
      :link-position="linkPosition"
      :image-alt-position="imageAltPosition"
      :table-position="tablePosition"
      :slash-actions="slashActions"
      :slash-active-index="slashActiveIndex"
      :color-presets="colorPresets"
      :color-preset-labels="colorPresetLabels"
      :current-text-color="currentTextColor"
      :is-default-color="isDefaultColor"
      :link-url="linkUrl"
      :link-text="linkText"
      :can-remove-link="editor?.isActive('link') ?? false"
      :image-alt-text="imageAltText"
      :table-rows="tableRows"
      :table-cols="tableCols"
      :table-header-row="tableHeaderRow"
      @close-slash-menu="showSlashMenu = false"
      @select-slash-action="applySlashAction"
      @move-slash-selection="moveSlashSelection"
      @set-slash-selection="setSlashSelection"
      @set-default-color="setDefaultColor"
      @set-preset-color="setPresetColor"
      @close-color-panel="closeColorPanel()"
      @apply-link="applyLink"
      @remove-link="removeLink"
      @close-link-popover="closeLinkPopover"
      @apply-image-alt="applyImageAlt"
      @clear-image-alt="clearImageAlt"
      @close-image-alt-popover="closeImageAltPopover"
      @update:table-rows="tableRows = $event"
      @update:table-cols="tableCols = $event"
      @update:table-header-row="tableHeaderRow = $event"
      @apply-table="applyTable"
      @close-table-popover="closeTablePopover"
    />

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

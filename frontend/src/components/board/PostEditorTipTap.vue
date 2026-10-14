<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import PostEditorContentArea from '@/components/board/editor/PostEditorContentArea.vue'
import PostEditorFloatingPanels from '@/components/board/editor/PostEditorFloatingPanels.vue'
import PostEditorToolbar from '@/components/board/editor/PostEditorToolbar.vue'
import PostEditorDocumentTools from '@/components/board/editor/PostEditorDocumentTools.vue'
import PostEditorSelectionTools from '@/components/board/editor/PostEditorSelectionTools.vue'
import { useDialogLifecycle } from '@/composables/useDialogLifecycle'
import { pasteWithPostEditorMode, type PostEditorPasteMode } from '@/features/board/posts/editor/postEditorPaste'
import { usePostEditorImageReplacement } from '@/features/board/posts/editor/usePostEditorImageReplacement'
import { codeBlockLanguages, colorLabelKeys, colorPresets, fontSizes, lineHeights, slashActions } from '@/components/board/editor/postEditorOptions'
import '@/components/board/editor/editor.css'
import { useEditorImageUpload } from '@/features/board/posts/editor/useEditorImageUpload'
import { focusPostEditorAtPointer, usePostEditorInstance } from '@/features/board/posts/editor/usePostEditorInstance'
import { usePostEditorColorPanel } from '@/features/board/posts/editor/usePostEditorColorPanel'
import { usePostEditorFloatingPanelRefs } from '@/features/board/posts/editor/usePostEditorFloatingPanelRefs'
import { usePostEditorImageFiles } from '@/features/board/posts/editor/usePostEditorImageFiles'
import { pastePostEditorCode } from '@/features/board/posts/editor/postEditorCodePaste'
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
import { isComposingKeyboardEvent } from '@/utils/keyboard'
import { moveFromSelectedRawHtmlBlock } from '@/extensions/tiptap-raw-html-block'
import { encodeSandboxedPostHtml, requiresPreservedPostHtml } from '@/utils/postHtmlSandbox'

const props = defineProps<{
  modelValue: string
  uploadOwnerIdentity?: string
  pollEnabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'open-video'): void
  (e: 'open-emoticon'): void
  (e: 'open-poll'): void
  (e: 'file-uploaded', fileId: number): void
  (e: 'upload-pending', pending: boolean): void
}>()

const { t } = useI18n()
const toastStore = useToastStore()
const themeStore = useThemeStore()
const modelValue = toRef(props, 'modelValue')
const pasteMode = ref<PostEditorPasteMode>('original')
const focused = ref(false)
const editorWrap = ref<HTMLElement | null>(null)
const { isTopDialog: isFocusDialogTop } = useDialogLifecycle({
  isOpen: focused,
  dialogRef: editorWrap,
  close: () => { focused.value = false },
  closeOnEscape: () => {
    const active = document.activeElement
    if (active?.closest('.post-editor-document-panel, .post-editor-selection-tools')) return false
    const editorElement = editor.value?.view.dom
    // An open suggestion owns Escape; otherwise it should exit focus mode before block selection.
    return !editorElement?.contains(active) || editorElement.getAttribute('aria-expanded') !== 'true'
  },
  initialFocus: 'container',
})
function exitFocusMode(event: KeyboardEvent) {
  if (!focused.value) return
  event.stopPropagation()
  if (event.defaultPrevented || isComposingKeyboardEvent(event)) return
  event.preventDefault()
  focused.value = false
}
const isRawHtmlBlockSelected = ref(false)
const uploadOwnerIdentity = toRef(props, 'uploadOwnerIdentity')
const availableSlashActions = computed(() => slashActions.filter(action => {
  if (action === 'poll' && props.pollEnabled === false) return false
  return !['taskList', 'info', 'warning', 'details'].includes(action)
    || (!isRawHtmlBlockSelected.value && !editor.value?.isActive('codeBlock'))
}))

const { isUploadingImage, validateImageFile, uploadImage, abortImageUpload, isAbortUploadError } = useEditorImageUpload(
  undefined,
  uploadOwnerIdentity,
)
const {
  showColorPanel,
  showLinkPopover,
  showTablePopover,
  showSlashMenu,
  slashPopoverRef,
  colorPanelRef,
  linkPopoverRef,
  tablePopoverRef,
  isSlashDialogTop,
  isColorDialogTop,
  isLinkDialogTop,
  isTableDialogTop,
  slashPosition,
  colorPosition,
  linkPosition,
  tablePosition,
  closeFloatingMenus,
} = usePostEditorPopovers()
const {
  slashActiveIndex,
  openSlashMenu,
  toggleSlashMenu,
  moveSlashSelection,
  setSlashSelection,
} = usePostEditorSlashMenu({
  actions: availableSlashActions,
  showSlashMenu,
  slashPosition,
  closeFloatingMenus,
})
const editor = usePostEditorInstance({
  modelValue,
  onUpdateHtml: (html) => emit('update:modelValue', html),
  openSlashMenu,
  onRawHtmlBlockSelectionChange: (selected) => {
    isRawHtmlBlockSelected.value = selected
  },
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
  ownerIdentity: uploadOwnerIdentity,
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
  replaceUploadedImage,
} = usePostEditorUploadedImages(editor, (fileId) => emit('file-uploaded', fileId))
const { input: replacementInput, busy: replacingImage, currentFileName: replacingImageName, onChange: onReplacementChange, cancel: cancelReplacement } = usePostEditorImageReplacement({
  editor, ownerIdentity: uploadOwnerIdentity, replace: replaceUploadedImage,
  invalid: reportImageValidationError,
  failed: () => toastStore.addToast(t('common.messages.uploadFailed'), 'error'),
})
watch(
  () => imageUploadQueue.isProcessing.value || imageUploadQueueCount.value > 0 || replacingImage.value,
  (pending) => emit('upload-pending', pending),
  { immediate: true, flush: 'sync' },
)

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
  openPollEditor: () => emit('open-poll'),
  detailsTitle: () => t('board.writePost.advanced.detailsTitle'),
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
})

const {
  assignSlashPopover,
  assignColorPanel,
  assignLinkPopover,
  assignTablePopover,
} = usePostEditorFloatingPanelRefs({
  slashPopoverRef,
  colorPanelRef,
  linkPopoverRef,
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
} = usePostEditorImageFiles(imageUploadQueue, (html) => {
  if (!requiresPreservedPostHtml(html)) return false
  prepareInsertionAfterRawHtmlBlock()
  editor.value?.chain().focus().insertContent(encodeSandboxedPostHtml(html)).run()
  return true
}, (event) => pasteWithPostEditorMode(editor.value, event, pasteMode.value) || pastePostEditorCode(editor.value, event))

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

function prepareInsertionAfterRawHtmlBlock() {
  const instance = editor.value
  if (instance) moveFromSelectedRawHtmlBlock(instance, 1)
}

function triggerImageUploadAtSelection() {
  prepareInsertionAfterRawHtmlBlock()
  triggerImageUpload()
}

function openVideoAtSelection() {
  prepareInsertionAfterRawHtmlBlock()
  emit('open-video')
}

function openEmoticonAtSelection() {
  prepareInsertionAfterRawHtmlBlock()
  emit('open-emoticon')
}

function toggleSlashMenuAtSelection(trigger: HTMLElement) {
  prepareInsertionAfterRawHtmlBlock()
  toggleSlashMenu(trigger)
}

function openTableAtSelection(trigger: HTMLElement) {
  prepareInsertionAfterRawHtmlBlock()
  openTablePopover(trigger)
}

function insertHorizontalRuleAtSelection() {
  prepareInsertionAfterRawHtmlBlock()
  applyHorizontalRule()
}

function setVideo(src: string) {
  prepareInsertionAfterRawHtmlBlock()
  editor.value?.chain().focus().setVideo({ src }).run()
}

function setEmoticon(image: EmoticonImage) {
  prepareInsertionAfterRawHtmlBlock()
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

function toggleCodeBlockAtSelection() {
  prepareInsertionAfterRawHtmlBlock()
  editor.value?.chain().focus().toggleCodeBlock().run()
}

defineExpose({
  editor,
  setVideo,
  setEmoticon,
  fileIds,
})

onBeforeUnmount(() => {
  imageUploadQueue.dispose()
  emit('upload-pending', false)
  disposeUploadedImagePreviews()
  editor.value?.destroy()
})
</script>

<template>
  <div ref="editorWrap" class="tiptap-editor-wrap flex min-h-0 flex-1 flex-col" :class="{ 'post-editor-focus': focused }" :role="focused ? 'dialog' : undefined" :aria-modal="isFocusDialogTop ? 'true' : undefined" :aria-label="focused ? t('board.writePost.advanced.focus') : undefined" @keydown.esc="exitFocusMode">
    <input ref="imageInput" type="file" data-ui-native="file" :accept="IMAGE_UPLOAD_ACCEPT" multiple class="hidden" @change="onImageChange">
    <input ref="replacementInput" type="file" data-ui-native="file" :accept="IMAGE_UPLOAD_ACCEPT" class="hidden" data-image-replacement @change="onReplacementChange" @cancel="cancelReplacement">
    <PostEditorDocumentTools v-if="editor" v-model:paste-mode="pasteMode" v-model:focused="focused" :editor="editor" />
    <PostEditorSelectionTools v-if="editor" :editor="editor" :focused="focused" @link="openLinkPopover" @color="toggleColorPanel" />

    <PostEditorToolbar
      v-if="editor"
      :editor="editor"
      :is-uploading-image="isUploadingImage"
      :is-replacing-image="replacingImage"
      :replacing-image-name="replacingImageName"
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
      :is-raw-html-block-selected="isRawHtmlBlockSelected"
      @toggle-bold="editor.chain().focus().toggleBold().run()"
      @toggle-italic="editor.chain().focus().toggleItalic().run()"
      @toggle-underline="editor.chain().focus().toggleUnderline().run()"
      @toggle-strike="editor.chain().focus().toggleStrike().run()"
      @open-link="openLinkPopover"
      @upload-image="triggerImageUploadAtSelection"
      @open-video="openVideoAtSelection"
      @open-emoticon="openEmoticonAtSelection"
      @save-list-selection="saveListSelection"
      @bullet-list="applyBulletList"
      @ordered-list="applyOrderedList"
      @font-size="applyFontSize"
      @line-height="applyLineHeight"
      @code-block-language="applyCodeBlockLanguage"
      @toggle-code-block="toggleCodeBlockAtSelection"
      @exit-code-block="editor.chain().focus().exitCode().run()"
      @toggle-color-panel="toggleColorPanel"
      @align="setTextAlign"
      @toggle-slash-menu="toggleSlashMenuAtSelection"
      @open-table="openTableAtSelection"
      @horizontal-rule="insertHorizontalRuleAtSelection"
      @retry-image-upload="retryImageUpload"
      @retry-failed-image-upload="retryFailedImageUpload"
      @cancel-image-upload="cancelImageUpload"
      @cancel-image-replacement="cancelReplacement"
      @dismiss-image-upload-error="dismissImageUploadError"
      @dismiss-failed-image-upload="dismissFailedImageUpload"
    />

    <PostEditorFloatingPanels
      v-if="editor"
      :show-slash-menu="showSlashMenu"
      :show-color-panel="showColorPanel"
      :show-link-popover="showLinkPopover"
      :show-table-popover="showTablePopover"
      :is-slash-dialog-top="isSlashDialogTop"
      :is-color-dialog-top="isColorDialogTop"
      :is-link-dialog-top="isLinkDialogTop"
      :is-table-dialog-top="isTableDialogTop"
      :assign-slash-popover="assignSlashPopover"
      :assign-color-panel="assignColorPanel"
      :assign-link-popover="assignLinkPopover"
      :assign-table-popover="assignTablePopover"
      :slash-position="slashPosition"
      :color-position="colorPosition"
      :link-position="linkPosition"
      :table-position="tablePosition"
      :slash-actions="availableSlashActions"
      :slash-active-index="slashActiveIndex"
      :color-presets="colorPresets"
      :color-preset-labels="colorPresetLabels"
      :current-text-color="currentTextColor"
      :is-default-color="isDefaultColor"
      :link-url="linkUrl"
      :link-text="linkText"
      :can-remove-link="editor?.isActive('link') ?? false"
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

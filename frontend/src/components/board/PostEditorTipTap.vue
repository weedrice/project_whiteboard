<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useEditor } from '@tiptap/vue-3'
import PostEditorColorPopover from '@/components/board/editor/PostEditorColorPopover.vue'
import PostEditorContentArea from '@/components/board/editor/PostEditorContentArea.vue'
import PostEditorImageAltPopover from '@/components/board/editor/PostEditorImageAltPopover.vue'
import PostEditorLinkPopover from '@/components/board/editor/PostEditorLinkPopover.vue'
import PostEditorPopoverMask from '@/components/board/editor/PostEditorPopoverMask.vue'
import PostEditorSlashMenu from '@/components/board/editor/PostEditorSlashMenu.vue'
import PostEditorTablePopover from '@/components/board/editor/PostEditorTablePopover.vue'
import PostEditorToolbar from '@/components/board/editor/PostEditorToolbar.vue'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { colorLabelKeys, colorPresets, fontSizes, lineHeights, slashActions, type SlashAction } from '@/components/board/editor/postEditorOptions'
import { escapeHtmlAttr, escapeHtmlText } from '@/components/board/editor/postEditorHtml'
import { hasCandidateImageFiles, isCandidateImageFile } from '@/components/board/editor/postEditorImageFiles'
import '@/components/board/editor/editor.css'
import { useEditorImageUpload } from '@/composables/useEditorImageUpload'
import { usePostEditorImageUploadState } from '@/composables/usePostEditorImageUploadState'
import { usePostEditorPopovers } from '@/composables/usePostEditorPopovers'
import { usePostEditorTextCommands } from '@/composables/usePostEditorTextCommands'
import { usePostEditorUploadedImages, type UploadedEditorImage } from '@/composables/usePostEditorUploadedImages'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import type { EmoticonImage } from '@/types/emoticon'
import logger from '@/utils/logger'
import { toSafePostLinkUrl } from '@/utils/postForm'

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
const linkUrl = ref('')
const linkText = ref('')
const imageAltText = ref('')
const tableRows = ref(3)
const tableCols = ref(3)
const tableHeaderRow = ref(true)
const savedListSelection = ref<{ from: number; to: number } | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const isDraggingImage = ref(false)
const slashActiveIndex = ref(0)

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

const editor = useEditor({
  content: props.modelValue || '',
  editable: true,
  editorProps: {
    attributes: {
      class: 'nv-rich-content prose prose-sm dark:prose-invert max-w-none min-h-[280px] px-4 py-4 focus:outline-none',
    },
    handleDOMEvents: {
      click: (_view, event) => {
        const link = (event.target as HTMLElement)?.closest?.('a[href]')
        if (!link) return false
        if (event.ctrlKey || event.metaKey) return false
        event.preventDefault()
        return true
      },
      keydown: (_view, event) => {
        const instance = editor.value
        if (!instance || event.key !== '/') {
          return false
        }
        const selection = instance.state.selection
        if (!selection?.$from) {
          return false
        }
        const isCollapsedSelection = selection.from === selection.to
        const parentText = selection.$from.parent?.textContent ?? ''
        const textBeforeCursor = parentText.slice(0, selection.$from.parentOffset)
        const shouldOpenSlashMenu = isCollapsedSelection
          && parentText.trim().length === 0
          && textBeforeCursor.trim().length === 0

        if (shouldOpenSlashMenu) {
          event.preventDefault()
          openSlashMenu()
          return true
        }
        return false
      },
    },
    handleClickOn: (_view, _pos, node, nodePos, event) => {
      if (node.type.name !== 'image') return false
      const target = event.target instanceof HTMLElement ? event.target.closest('img') : null
      if (!(target instanceof HTMLImageElement)) return false
      openImageAltPopover(target, node.attrs.alt ?? '', nodePos)
      return false
    },
  },
  extensions: createPostEditorExtensions(),
  onUpdate: ({ editor: instance }) => {
    emit('update:modelValue', instance.getHTML())
  },
})

const {
  fileIds,
  insertUploadedImage,
  disposeUploadedImagePreviews,
} = usePostEditorUploadedImages(editor, (fileId) => emit('file-uploaded', fileId))

watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value) return
    const current = editor.value.getHTML()
    if (value !== current) {
      editor.value.commands.setContent(value || '', { emitUpdate: false })
    }
  },
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

function openSlashMenu(anchor?: HTMLElement) {
  closeFloatingMenus()
  slashPosition.setAnchor(anchor)
  slashActiveIndex.value = 0
  showSlashMenu.value = true
}

function toggleSlashMenu(anchor?: HTMLElement) {
  if (showSlashMenu.value) {
    showSlashMenu.value = false
    return
  }
  openSlashMenu(anchor)
}

function moveSlashSelection(direction: 1 | -1) {
  slashActiveIndex.value = (slashActiveIndex.value + direction + slashActions.length) % slashActions.length
}

function setSlashSelection(index: number) {
  slashActiveIndex.value = Math.max(0, Math.min(slashActions.length - 1, index))
}

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

function openLinkPopover(anchor?: HTMLElement) {
  closeFloatingMenus()
  linkPosition.setAnchor(anchor)
  const attrs = editor.value?.getAttributes('link')
  linkUrl.value = attrs?.href ?? ''
  const { from, to } = editor.value?.state.selection ?? {}
  const selectedText = from !== undefined && to !== undefined && from < to
    ? editor.value?.state.doc.textBetween(from, to, ' ') ?? ''
    : ''
  linkText.value = selectedText
  showLinkPopover.value = true
}

function closeLinkPopover() {
  showLinkPopover.value = false
  linkPosition.clearAnchor()
  linkUrl.value = ''
  linkText.value = ''
}

function openImageAltPopover(anchor: HTMLElement, alt = '', nodePos?: number) {
  closeFloatingMenus()
  imageAltPosition.setAnchor(anchor)
  imageAltText.value = alt
  showImageAltPopover.value = true
  if (typeof nodePos === 'number') {
    editor.value?.commands.setTextSelection(nodePos)
  }
}

function closeImageAltPopover() {
  showImageAltPopover.value = false
  imageAltPosition.clearAnchor()
  imageAltText.value = ''
}

function applyImageAlt(value = imageAltText.value) {
  editor.value?.chain().focus().updateAttributes('image', { alt: value.trim() }).run()
  closeImageAltPopover()
}

function clearImageAlt() {
  editor.value?.chain().focus().updateAttributes('image', { alt: null }).run()
  closeImageAltPopover()
}

function applyLink(nextUrl = linkUrl.value, nextText = linkText.value) {
  linkUrl.value = nextUrl
  linkText.value = nextText
  const url = nextUrl.trim()
  const displayText = nextText.trim()
  if (!url) {
    toastStore.addToast(t('board.writePost.linkUrlPrompt'), 'error')
    return
  }
  const safeUrl = toSafePostLinkUrl(url)
  if (!safeUrl) {
    toastStore.addToast(t('board.writePost.invalidLinkUrl'), 'error')
    return
  }
  const text = displayText || url
  const { from, to } = editor.value?.state.selection ?? {}
  const hasSelection = from !== undefined && to !== undefined && from < to
  if (hasSelection) {
    editor.value?.chain().focus().setLink({ href: safeUrl }).run()
  } else {
    editor.value?.chain().focus().insertContent(`<a href="${escapeHtmlAttr(safeUrl)}" class="tiptap-link">${escapeHtmlText(text)}</a>`).run()
  }
  closeLinkPopover()
}

function removeLink() {
  editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
  closeLinkPopover()
}

function openTablePopover(anchor?: HTMLElement) {
  closeFloatingMenus()
  tablePosition.setAnchor(anchor)
  tableRows.value = 3
  tableCols.value = 3
  tableHeaderRow.value = true
  showTablePopover.value = true
}

function closeTablePopover() {
  showTablePopover.value = false
  tablePosition.clearAnchor()
}

function applyTable() {
  const rows = Math.max(1, Math.min(20, Math.floor(Number(tableRows.value)) || 3))
  const cols = Math.max(1, Math.min(10, Math.floor(Number(tableCols.value)) || 3))
  editor.value?.chain().focus().insertTable({ rows, cols, withHeaderRow: tableHeaderRow.value }).run()
  closeTablePopover()
}

function saveListSelection() {
  const instance = editor.value
  if (!instance) return
  const { from, to } = instance.state.selection
  savedListSelection.value = from !== to ? { from, to } : null
}

function applyListToggle(type: 'bullet' | 'ordered') {
  const instance = editor.value
  if (!instance) return
  const chain = instance.chain().focus()
  const saved = savedListSelection.value
  if (saved) {
    chain.setTextSelection({ from: saved.from, to: saved.to })
    savedListSelection.value = null
  }

  if (type === 'bullet') {
    chain.toggleBulletList().run()
  } else {
    chain.toggleOrderedList().run()
  }
}

function applyBulletList() {
  applyListToggle('bullet')
}

function applyOrderedList() {
  applyListToggle('ordered')
}

function onContentAreaClick(event: MouseEvent) {
  const instance = editor.value
  if (!instance) return
  const currentTarget = event.currentTarget as HTMLElement
  const target = event.target as Node
  if (!currentTarget.contains(target)) return

  const view = instance.view
  const isClickOnEditorRoot = view.dom === target || view.dom.contains(target)
  const position = view.posAtCoords({ left: event.clientX, top: event.clientY })
  if (position != null) {
    if (!isClickOnEditorRoot) event.preventDefault()
    view.focus()
    instance.commands.setTextSelection(position.pos)
    return
  }
  const size = instance.state.doc.content.size
  if (size > 0) {
    event.preventDefault()
    view.focus()
    instance.commands.setTextSelection(Math.max(0, size - 1))
  }
}

function triggerImageUpload() {
  imageInput.value?.click()
}

function reportImageValidationError(validationError: 'type' | 'size') {
  if (validationError === 'type') {
    toastStore.addToast(t('common.messages.badRequest'), 'warning')
    return
  }
  toastStore.addToast(t('common.messages.fileSizeExceeded'), 'warning')
}

function queueImageFiles(files: File[]) {
  const candidateFiles = files.filter(isCandidateImageFile)
  if (candidateFiles.length === 0) return false
  imageUploadQueue.enqueueFiles(candidateFiles)
  return true
}

async function onImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length === 0) return

  try {
    queueImageFiles(files)
  } finally {
    input.value = ''
  }
}

function onEditorPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (queueImageFiles(files)) {
    event.preventDefault()
  }
}

function onEditorDrop(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.files ?? [])
  isDraggingImage.value = false
  if (queueImageFiles(files)) {
    event.preventDefault()
  }
}

function onEditorDragEnter(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter((file): file is File => Boolean(file))
  if (files.length > 0 && hasCandidateImageFiles(files)) {
    isDraggingImage.value = true
  }
}

function onEditorDragLeave(event: DragEvent) {
  const currentTarget = event.currentTarget as HTMLElement
  const relatedTarget = event.relatedTarget as Node | null
  if (!relatedTarget || !currentTarget.contains(relatedTarget)) {
    isDraggingImage.value = false
  }
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

function applySlashAction(action: SlashAction) {
  switch (action) {
    case 'heading':
      editor.value?.chain().focus().toggleHeading({ level: 2 }).run()
      break
    case 'quote':
      editor.value?.chain().focus().setBlockquote().run()
      break
    case 'list':
      applyBulletList()
      break
    case 'link':
      openLinkPopover()
      break
    case 'table':
      openTablePopover()
      break
    case 'codeBlock':
      editor.value?.chain().focus().toggleCodeBlock().run()
      break
    case 'divider':
      editor.value?.chain().focus().setHorizontalRule().run()
      break
  }
  showSlashMenu.value = false
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
    <input ref="imageInput" type="file" accept=".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp" multiple class="hidden" @change="onImageChange">

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

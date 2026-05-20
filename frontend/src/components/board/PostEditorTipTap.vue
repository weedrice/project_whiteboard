<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import Highlight from '@tiptap/extension-highlight'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import TextAlign from '@tiptap/extension-text-align'
import { TableKit } from '@tiptap/extension-table'
import HorizontalRule from '@tiptap/extension-horizontal-rule'
import { FontSize, LineHeight } from '@tiptap/extension-text-style'
import { Video } from '@/extensions/tiptap-video'
import PostEditorAdvancedPopover from '@/components/board/editor/PostEditorAdvancedPopover.vue'
import PostEditorColorPopover from '@/components/board/editor/PostEditorColorPopover.vue'
import PostEditorLinkPopover from '@/components/board/editor/PostEditorLinkPopover.vue'
import PostEditorSlashMenu from '@/components/board/editor/PostEditorSlashMenu.vue'
import PostEditorTablePopover from '@/components/board/editor/PostEditorTablePopover.vue'
import PostEditorToolbar from '@/components/board/editor/PostEditorToolbar.vue'
import { useEditorImageUpload } from '@/composables/useEditorImageUpload'
import { usePopoverFocus } from '@/composables/usePopoverFocus'
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

type SlashAction = 'image' | 'quote' | 'list' | 'link' | 'divider'

const { t } = useI18n()
const toastStore = useToastStore()
const themeStore = useThemeStore()
const fileIds = ref<number[]>([])
const showColorPanel = ref(false)
const showLinkPopover = ref(false)
const showTablePopover = ref(false)
const showAdvancedMenu = ref(false)
const showSlashMenu = ref(false)
const linkUrl = ref('')
const linkText = ref('')
const tableRows = ref(3)
const tableCols = ref(3)
const tableHeaderRow = ref(true)
const savedListSelection = ref<{ from: number; to: number } | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const failedImageFile = ref<File | null>(null)
const slashActiveIndex = ref(0)
const slashPopoverRef = ref<HTMLElement | null>(null)
const advancedPopoverRef = ref<HTMLElement | null>(null)
const colorPanelRef = ref<HTMLElement | null>(null)
const linkPopoverRef = ref<HTMLElement | null>(null)
const tablePopoverRef = ref<HTMLElement | null>(null)

const { isUploadingImage, validateImageFile, uploadImage, abortImageUpload, isAbortUploadError } = useEditorImageUpload()
usePopoverFocus(slashPopoverRef, showSlashMenu)
usePopoverFocus(advancedPopoverRef, showAdvancedMenu)
usePopoverFocus(colorPanelRef, showColorPanel)
usePopoverFocus(linkPopoverRef, showLinkPopover)
usePopoverFocus(tablePopoverRef, showTablePopover)

const EditorImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      fileId: {
        default: null,
        parseHTML: (element: HTMLElement) => element.getAttribute('data-file-id'),
        renderHTML: (attributes: { fileId?: string | number | null }) => (
          attributes.fileId ? { 'data-file-id': String(attributes.fileId) } : {}
        ),
      },
      serverSrc: {
        default: null,
        parseHTML: (element: HTMLElement) => element.getAttribute('data-server-src'),
        renderHTML: (attributes: { serverSrc?: string | null }) => (
          attributes.serverSrc ? { 'data-server-src': attributes.serverSrc } : {}
        ),
      },
    }
  },
})

const colorPresets = [
  '#000000', '#374151', '#6b7280', '#9ca3af',
  '#ef4444', '#f97316', '#eab308', '#22c55e',
  '#3b82f6', '#8b5cf6', '#ec4899', '#14b8a6',
  '#ffffff', '#1f2937', '#4b5563', '#d1d5db',
]
const colorLabelKeys = [
  'black', 'gray', 'muted', 'lightGray',
  'red', 'orange', 'yellow', 'green',
  'blue', 'purple', 'pink', 'teal',
  'white', 'dark', 'slate', 'paleGray',
]
const slashActions: SlashAction[] = ['image', 'quote', 'list', 'link', 'divider']
const fontSizes = ['12px', '14px', '16px', '18px', '24px']
const lineHeights = ['1', '1.25', '1.5', '1.75', '2']

const editor = useEditor({
  content: props.modelValue || '',
  editable: true,
  editorProps: {
    attributes: {
      class: 'prose prose-sm dark:prose-invert max-w-none min-h-[280px] px-4 py-4 focus:outline-none',
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
  },
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3, 4, 5, 6] },
    }),
    Underline,
    TextStyle,
    Color.configure({ types: ['textStyle'] }),
    Highlight.configure({ multicolor: true }),
    FontSize.configure({ types: ['textStyle'] }),
    LineHeight.configure({ types: ['textStyle'] }),
    Link.configure({
      openOnClick: false,
      HTMLAttributes: {
        rel: 'noopener noreferrer',
        target: '_blank',
        class: 'tiptap-link',
      },
    }),
    EditorImage.configure({
      inline: true,
      allowBase64: false,
      HTMLAttributes: { class: 'tiptap-image-inline max-w-full h-auto align-baseline' },
    }),
    TextAlign.configure({
      types: ['heading', 'paragraph', 'tableCell', 'tableHeader'],
    }),
    TableKit.configure({
      table: {
        resizable: true,
        handleWidth: 6,
        cellMinWidth: 40,
      },
    }),
    HorizontalRule,
    Video,
  ],
  onUpdate: ({ editor: instance }) => {
    emit('update:modelValue', instance.getHTML())
  },
})

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

const currentTextColor = computed(() => editor.value?.getAttributes('textStyle').color || '')
const isDefaultColor = computed(() => !currentTextColor.value)
const currentFontSize = computed(() => editor.value?.getAttributes('textStyle').fontSize || '')
const currentLineHeight = computed(() => editor.value?.getAttributes('textStyle').lineHeight || '')
const currentHighlightColor = computed(() => editor.value?.getAttributes('highlight').color || '#fef08a')
const hasImageUploadError = computed(() => failedImageFile.value !== null)
const activeTextAlign = computed<'left' | 'center' | 'right' | 'justify' | ''>(() => {
  if (isTextAlignActive('left')) return 'left'
  if (isTextAlignActive('center')) return 'center'
  if (isTextAlignActive('right')) return 'right'
  if (isTextAlignActive('justify')) return 'justify'
  return ''
})
const colorPresetLabels = computed(() => Object.fromEntries(
  colorPresets.map((color, index) => [
    color,
    t(`board.writePost.colorLabels.${colorLabelKeys[index]}`),
  ]),
))

function closeFloatingMenus() {
  showAdvancedMenu.value = false
  showSlashMenu.value = false
}

function openSlashMenu() {
  closeFloatingMenus()
  slashActiveIndex.value = 0
  showSlashMenu.value = true
}

function toggleSlashMenu() {
  if (showSlashMenu.value) {
    showSlashMenu.value = false
    return
  }
  openSlashMenu()
}

function moveSlashSelection(direction: 1 | -1) {
  slashActiveIndex.value = (slashActiveIndex.value + direction + slashActions.length) % slashActions.length
}

function setSlashSelection(index: number) {
  slashActiveIndex.value = Math.max(0, Math.min(slashActions.length - 1, index))
}

function setDefaultColor() {
  editor.value?.chain().focus().unsetColor().run()
  showColorPanel.value = false
}

function setPresetColor(color: string) {
  editor.value?.chain().focus().setColor(color).run()
  showColorPanel.value = false
}

function toggleColorPanel() {
  showColorPanel.value = !showColorPanel.value
}

function closeColorPanel() {
  showColorPanel.value = false
}

function openLinkPopover() {
  closeFloatingMenus()
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
  linkUrl.value = ''
  linkText.value = ''
}

function escapeHtmlAttr(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeHtmlText(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
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

function openTablePopover() {
  closeFloatingMenus()
  tableRows.value = 3
  tableCols.value = 3
  tableHeaderRow.value = true
  showTablePopover.value = true
}

function closeTablePopover() {
  showTablePopover.value = false
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

function applyBulletList() {
  const instance = editor.value
  if (!instance) return
  const saved = savedListSelection.value
  if (saved) {
    instance.chain().focus().setTextSelection({ from: saved.from, to: saved.to }).toggleBulletList().run()
    savedListSelection.value = null
    return
  }
  instance.chain().focus().toggleBulletList().run()
}

function applyOrderedList() {
  const instance = editor.value
  if (!instance) return
  const saved = savedListSelection.value
  if (saved) {
    instance.chain().focus().setTextSelection({ from: saved.from, to: saved.to }).toggleOrderedList().run()
    savedListSelection.value = null
    return
  }
  instance.chain().focus().toggleOrderedList().run()
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

async function uploadSelectedImage(file: File) {
  const validationError = validateImageFile(file)
  if (validationError === 'type') {
    toastStore.addToast(t('common.messages.badRequest'), 'warning')
    failedImageFile.value = null
    return
  }
  if (validationError === 'size') {
    toastStore.addToast(t('common.messages.fileSizeExceeded'), 'warning')
    failedImageFile.value = null
    return
  }

  try {
    failedImageFile.value = null
    const uploaded = await uploadImage(file)
    if (uploaded) {
      if (typeof uploaded.fileId === 'number') {
        fileIds.value.push(uploaded.fileId)
        emit('file-uploaded', uploaded.fileId)
      }
      const serverAttributes = typeof uploaded.fileId === 'number'
        ? ` data-file-id="${uploaded.fileId}"`
        : ''
      editor.value?.chain().focus().insertContent(`<img src="${escapeHtmlAttr(uploaded.url)}"${serverAttributes}>`).run()
    }
  } catch (error: unknown) {
    if (isAbortUploadError(error)) {
      return
    }
    failedImageFile.value = file
    logger.error('Image upload failed:', error)
    toastStore.addToast(t('common.messages.uploadFailed'), 'error')
  }
}

async function onImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  try {
    await uploadSelectedImage(file)
  } finally {
    input.value = ''
  }
}

function retryImageUpload() {
  if (failedImageFile.value) {
    void uploadSelectedImage(failedImageFile.value)
  }
}

function dismissImageUploadError() {
  failedImageFile.value = null
}

function cancelImageUpload() {
  abortImageUpload()
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
    case 'image':
      triggerImageUpload()
      break
    case 'quote':
      editor.value?.chain().focus().toggleBlockquote().run()
      break
    case 'list':
      applyBulletList()
      break
    case 'link':
      openLinkPopover()
      break
    case 'divider':
      editor.value?.chain().focus().setHorizontalRule().run()
      break
  }
  showSlashMenu.value = false
}

function applyFontSize(value: string) {
  if (!editor.value) return
  if (value) {
    editor.value.chain().focus().setFontSize(value).run()
    return
  }
  editor.value.chain().focus().unsetFontSize().run()
}

function applyLineHeight(value: string) {
  if (!editor.value) return
  if (value) {
    editor.value.chain().focus().setLineHeight(value).run()
    return
  }
  editor.value.chain().focus().unsetLineHeight().run()
}

function applyHighlightColor(value: string) {
  editor.value?.chain().focus().setHighlight({ color: value }).run()
}

function applyHorizontalRule() {
  editor.value?.chain().focus().setHorizontalRule().run()
}

function setTextAlign(align: 'left' | 'center' | 'right' | 'justify') {
  editor.value?.chain().focus().setTextAlign(align).run()
}

function isTextAlignActive(align: 'left' | 'center' | 'right' | 'justify') {
  return editor.value?.isActive({ textAlign: align }) ?? false
}

defineExpose({
  editor,
  setVideo,
  setEmoticon,
  fileIds,
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<template>
  <div class="tiptap-editor-wrap flex min-h-0 flex-1 flex-col">
    <input ref="imageInput" type="file" accept=".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp" class="hidden" @change="onImageChange">

    <PostEditorToolbar
      v-if="editor"
      :editor="editor"
      :is-uploading-image="isUploadingImage"
      :has-image-upload-error="hasImageUploadError"
      :show-slash-menu="showSlashMenu"
      :show-advanced-menu="showAdvancedMenu"
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
      @toggle-slash-menu="toggleSlashMenu"
      @toggle-advanced-menu="showAdvancedMenu = !showAdvancedMenu"
      @retry-image-upload="retryImageUpload"
      @cancel-image-upload="cancelImageUpload"
      @dismiss-image-upload-error="dismissImageUploadError"
    />

    <Teleport to="body">
      <div v-if="showSlashMenu" class="link-popover-mask" @click.self="showSlashMenu = false" @keydown.enter.stop @keydown.escape.stop.prevent="showSlashMenu = false">
        <div id="editor-slash-dialog" ref="slashPopoverRef" class="link-popover slash-popover" role="dialog" aria-modal="true" aria-labelledby="editor-slash-dialog-title">
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
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showAdvancedMenu" class="link-popover-mask" @click.self="showAdvancedMenu = false" @keydown.enter.stop @keydown.escape.stop.prevent="showAdvancedMenu = false">
        <div id="editor-advanced-dialog" ref="advancedPopoverRef" class="link-popover advanced-popover" role="dialog" aria-modal="true" aria-labelledby="editor-advanced-dialog-title">
          <div class="mb-3">
            <p class="text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">{{ t('board.writePost.toolbar.advanced') }}</p>
            <h3 id="editor-advanced-dialog-title" class="text-base font-semibold text-[var(--nv-ink)]">{{ t('board.writePost.toolbar.formattingTools') }}</h3>
          </div>
          <PostEditorAdvancedPopover
            :font-sizes="fontSizes"
            :line-heights="lineHeights"
            :current-font-size="currentFontSize"
            :current-line-height="currentLineHeight"
            :current-highlight-color="currentHighlightColor"
            :current-text-color="currentTextColor"
            :is-default-color="isDefaultColor"
            :is-dark="themeStore.isDark"
            :show-color-panel="showColorPanel"
            :show-table-popover="showTablePopover"
            :active-text-align="activeTextAlign"
            @font-size="applyFontSize"
            @line-height="applyLineHeight"
            @highlight-color="applyHighlightColor"
            @toggle-color-panel="toggleColorPanel"
            @open-table="openTablePopover"
            @horizontal-rule="applyHorizontalRule"
            @align="setTextAlign"
          />
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showColorPanel" class="link-popover-mask" @click.self="closeColorPanel" @keydown.enter.stop @keydown.escape.stop.prevent="closeColorPanel">
        <div id="editor-color-dialog" ref="colorPanelRef" class="color-panel" role="dialog" aria-modal="true" aria-labelledby="editor-color-dialog-title">
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
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showLinkPopover" class="link-popover-mask" @click.self="closeLinkPopover" @keydown.enter.stop @keydown.escape.stop.prevent="closeLinkPopover">
        <div ref="linkPopoverRef" class="link-popover" role="dialog" aria-modal="true" aria-labelledby="editor-link-dialog-title">
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
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showTablePopover" class="link-popover-mask" @click.self="closeTablePopover" @keydown.enter.stop @keydown.escape.stop.prevent="closeTablePopover">
        <div ref="tablePopoverRef" class="link-popover table-popover" role="dialog" aria-modal="true" aria-labelledby="editor-table-dialog-title">
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
      </div>
    </Teleport>

    <div class="tiptap-content flex-1 min-h-0 overflow-auto cursor-text" @mousedown="onContentAreaClick">
      <EditorContent :editor="editor" />
    </div>
  </div>
</template>

<style>
.tiptap-btn {
  width: 2.2rem;
  height: 2.2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.85rem;
  border: 1px solid transparent;
  color: var(--nv-ink-soft);
  background: transparent;
  font-size: 0.75rem;
  font-weight: 600;
  transition: all 0.15s ease;
}

.tiptap-btn:hover {
  background: var(--nv-elevated);
  color: var(--nv-ink);
}

.tiptap-btn.active {
  border-color: color-mix(in srgb, var(--nv-accent) 28%, transparent);
  background: color-mix(in srgb, var(--nv-accent) 14%, var(--nv-surface));
  color: var(--nv-accent);
}

.tiptap-btn-pill {
  width: auto;
  padding: 0 0.8rem;
}

.tiptap-toolbar-group {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface) 88%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  display: inline-flex;
  gap: 0.15rem;
  padding: 0.2rem;
}

.image-upload-status {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface) 92%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 999px;
  color: var(--nv-ink-soft);
  display: inline-flex;
  font-size: 0.75rem;
  gap: 0.4rem;
  min-height: 2rem;
  padding: 0.25rem 0.65rem;
}

.image-upload-spinner {
  animation: tiptap-spin 0.75s linear infinite;
  border: 2px solid color-mix(in srgb, var(--nv-accent) 20%, transparent);
  border-top-color: var(--nv-accent);
  border-radius: 999px;
  height: 0.9rem;
  width: 0.9rem;
}

.image-upload-status-btn {
  border: 0;
  color: var(--nv-accent);
  font-weight: 600;
  background: transparent;
}

@keyframes tiptap-spin {
  to {
    transform: rotate(360deg);
  }
}

.tiptap-select {
  height: 2.25rem;
  min-width: 7rem;
  padding: 0 0.75rem;
  border-radius: 0.85rem;
  border: 1px solid var(--nv-line);
  background: var(--nv-elevated);
  color: var(--nv-ink);
}

.tiptap-color-input {
  border-radius: 0.85rem;
  border: 1px solid var(--nv-line);
  background: transparent;
  padding: 0.15rem;
}

.tiptap-color-trigger {
  font-weight: 700;
  font-size: 14px;
}

.tiptap-color-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  line-height: 1;
}

.tiptap-color-bar {
  display: block;
  width: 14px;
  height: 3px;
  border-radius: 999px;
}
</style>

<style>
.tiptap-editor-wrap .tiptap-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.tiptap-editor-wrap .tiptap-content > div,
.tiptap-editor-wrap .tiptap-content .ProseMirror {
  flex: 1;
  min-height: 100%;
  overflow-wrap: anywhere;
}

.tiptap-editor-wrap .ProseMirror ul {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.tiptap-editor-wrap .ProseMirror ol {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.tiptap-editor-wrap .ProseMirror li {
  display: list-item;
  margin: 0.25em 0;
}

.tiptap-editor-wrap .ProseMirror table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  border: 1px solid var(--nv-line);
}

.tiptap-editor-wrap .ProseMirror td,
.tiptap-editor-wrap .ProseMirror th {
  border: 1px solid var(--nv-line);
  vertical-align: top;
  box-sizing: border-box;
  position: relative;
  min-width: var(--table-cell-min-width, 40px);
}

.tiptap-editor-wrap .ProseMirror .tableWrapper {
  overflow-x: auto;
}

.tiptap-editor-wrap .ProseMirror .column-resize-handle {
  position: absolute;
  right: -3px;
  top: 0;
  bottom: 0;
  width: 6px;
  z-index: 20;
  background-color: var(--nv-accent);
  cursor: col-resize;
  pointer-events: auto;
}

.tiptap-editor-wrap .ProseMirror.resize-cursor {
  cursor: col-resize;
}

.tiptap-editor-wrap .ProseMirror a.tiptap-link,
.tiptap-editor-wrap .ProseMirror a[href] {
  color: color-mix(in srgb, var(--nv-accent) 82%, white 18%);
  text-decoration: underline;
  cursor: pointer;
}

.tiptap-video-wrapper {
  position: relative;
  padding-bottom: 56.25%;
  height: 0;
  overflow: hidden;
  max-width: 100%;
}

.tiptap-video-wrapper iframe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.tiptap-image-inline {
  display: inline-block;
  vertical-align: baseline;
}

.tiptap-editor-wrap .ProseMirror img.ProseMirror-selectednode,
.tiptap-editor-wrap .ProseMirror .ProseMirror-selectednode img,
.tiptap-editor-wrap .ProseMirror .ProseMirror-selectednode:has(> img) {
  outline: 2px solid var(--nv-accent);
  outline-offset: 2px;
  border-radius: 8px;
}

.link-popover-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(9, 12, 22, 0.28);
}

.link-popover {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  min-width: 320px;
  max-width: min(90vw, 460px);
  padding: 1rem 1.1rem;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.25rem;
  box-shadow: var(--nv-shadow-soft);
}

.slash-popover,
.advanced-popover {
  width: min(90vw, 440px);
}

.slash-action-btn {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  padding: 0.75rem 0.95rem;
  font-size: 0.95rem;
  color: var(--nv-ink);
  background: var(--nv-elevated);
}

.slash-action-btn--active,
.slash-action-btn:hover,
.slash-action-btn:focus-visible {
  border-color: color-mix(in srgb, var(--nv-accent) 35%, transparent);
  background: color-mix(in srgb, var(--nv-accent) 12%, var(--nv-elevated));
  color: var(--nv-accent);
  outline: none;
}

.link-popover-row {
  margin-bottom: 0.75rem;
}

.link-popover-label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--nv-ink-soft);
}

.link-popover-input {
  display: block;
  width: 100%;
  padding: 0.7rem 0.8rem;
  font-size: 0.875rem;
  border: 1px solid var(--nv-line);
  border-radius: 0.9rem;
  background: var(--nv-elevated);
  color: var(--nv-ink);
  box-sizing: border-box;
}

.link-popover-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--nv-line);
}

.link-popover-remove {
  margin-right: auto;
  font-size: 0.875rem;
  color: var(--nv-danger);
  background: none;
  border: none;
  cursor: pointer;
}

.color-panel {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 220px;
  padding: 12px;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  box-shadow: var(--nv-shadow-soft);
  z-index: 10000;
}

.color-panel-default {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-bottom: 8px;
  padding: 6px 8px;
  border-radius: 0.7rem;
  border: 1px solid var(--nv-line);
  background: transparent;
  color: var(--nv-ink-soft);
}

.color-panel-default--active {
  border-color: color-mix(in srgb, var(--nv-accent) 35%, transparent);
  background: color-mix(in srgb, var(--nv-accent) 14%, var(--nv-surface));
  color: var(--nv-accent);
}

.color-panel-default-swatch {
  display: inline-flex;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid var(--nv-line);
  position: relative;
  flex-shrink: 0;
}

.color-panel-default-light,
.color-panel-default-dark {
  position: absolute;
  inset: 0;
}

.color-panel-default-light {
  background: #111827;
  clip-path: polygon(0 0, 100% 0, 0 100%);
}

.color-panel-default-dark {
  background: #f3f4f6;
  clip-path: polygon(100% 0, 100% 100%, 0 100%);
}

.color-panel-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  margin-bottom: 8px;
}

.color-panel-swatch {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 0.45rem;
  border: 2px solid transparent;
  cursor: pointer;
}

.color-panel-swatch--active {
  border-color: var(--nv-accent);
}

.color-panel-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid var(--nv-line);
}

.color-panel-custom-label {
  font-size: 12px;
  color: var(--nv-muted);
  white-space: nowrap;
}

.color-panel-custom-input {
  width: 28px;
  height: 28px;
  border-radius: 0.45rem;
  border: 1px solid var(--nv-line);
  padding: 0;
  background: transparent;
  cursor: pointer;
}
</style>

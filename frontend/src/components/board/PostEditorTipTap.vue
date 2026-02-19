<script setup lang="ts">
import { watch, ref, computed, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
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
import { TextAlignStart, TextAlignCenter, TextAlignEnd, TextAlignJustify } from 'lucide-vue-next'
import axios from '@/api'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { EmoticonImage } from '@/types/emoticon'
import { useThemeStore } from '@/stores/theme'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'open-video'): void
  (e: 'open-emoticon'): void
}>()

const { t } = useI18n()
const toastStore = useToastStore()
const themeStore = useThemeStore()
const fileIds = ref<number[]>([])

const editor = useEditor({
  content: props.modelValue || '',
  editable: true,
  editorProps: {
    attributes: {
      class: 'prose prose-sm dark:prose-invert max-w-none min-h-[200px] px-3 py-2 sm:px-4 sm:py-3 focus:outline-none',
    },
    handleDOMEvents: {
      click: (_view, event) => {
        const link = (event.target as HTMLElement)?.closest?.('a[href]')
        if (!link) return false
        if (event.ctrlKey || event.metaKey) return false
        event.preventDefault()
        return true
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
    Image.configure({
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
  onUpdate: ({ editor: e }) => {
    emit('update:modelValue', e.getHTML())
  },
})

// 외부에서 modelValue가 바뀌었을 때(예: HTML 탭에서 편집 후 전환) 에디터에 반영
watch(
  () => props.modelValue,
  val => {
    if (!editor.value) return
    const current = editor.value.getHTML()
    if (val !== current) {
      editor.value.commands.setContent(val || '', { emitUpdate: false })
    }
  }
)

const fontSizes = ['12px', '14px', '16px', '18px', '24px']
const lineHeights = ['1', '1.25', '1.5', '1.75', '2']

// 텍스트 색상 picker
const showColorPanel = ref(false)
const colorPresets = [
  '#000000', '#374151', '#6b7280', '#9ca3af',
  '#ef4444', '#f97316', '#eab308', '#22c55e',
  '#3b82f6', '#8b5cf6', '#ec4899', '#14b8a6',
  '#ffffff', '#1f2937', '#4b5563', '#d1d5db',
  '#dc2626', '#ea580c', '#ca8a04', '#16a34a',
  '#2563eb', '#7c3aed', '#db2777', '#0d9488',
]

const currentTextColor = computed(() => editor.value?.getAttributes('textStyle').color || '')
const isDefaultColor = computed(() => !currentTextColor.value)

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

const showLinkPopover = ref(false)
const linkUrl = ref('')
const linkText = ref('')

const showTablePopover = ref(false)
const tableRows = ref(3)
const tableCols = ref(3)
const tableHeaderRow = ref(true)

function openLinkPopover() {
  const attrs = editor.value?.getAttributes('link')
  linkUrl.value = attrs?.href ?? ''
  const { from, to } = editor.value?.state.selection ?? {}
  const text = from !== undefined && to !== undefined && from < to
    ? editor.value?.state.doc.textBetween(from, to, ' ') ?? ''
    : ''
  linkText.value = text
  showLinkPopover.value = true
}

function closeLinkPopover() {
  showLinkPopover.value = false
  linkUrl.value = ''
  linkText.value = ''
}

function applyLink() {
  const url = linkUrl.value.trim()
  const displayText = linkText.value.trim()
  if (!url) {
    toastStore.addToast(t('board.writePost.linkUrlPrompt') || '링크 URL을 입력해 주세요.', 'error')
    return
  }
  const safeUrl = url.startsWith('http://') || url.startsWith('https://') ? url : `https://${url}`
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

const savedListSelection = ref<{ from: number; to: number } | null>(null)

function saveListSelection() {
  const e = editor.value
  if (!e) return
  const { from, to } = e.state.selection
  if (from !== to) savedListSelection.value = { from, to }
  else savedListSelection.value = null
}

function applyBulletList() {
  const e = editor.value
  if (!e) return
  const saved = savedListSelection.value
  if (saved) {
    e.chain().focus().setTextSelection({ from: saved.from, to: saved.to }).toggleBulletList().run()
    savedListSelection.value = null
  } else {
    e.chain().focus().toggleBulletList().run()
  }
}

function applyOrderedList() {
  const e = editor.value
  if (!e) return
  const saved = savedListSelection.value
  if (saved) {
    e.chain().focus().setTextSelection({ from: saved.from, to: saved.to }).toggleOrderedList().run()
    savedListSelection.value = null
  } else {
    e.chain().focus().toggleOrderedList().run()
  }
}

function onContentAreaClick(event: MouseEvent) {
  const ed = editor.value
  if (!ed) return
  const el = event.currentTarget as HTMLElement
  const target = event.target as Node
  if (!el.contains(target)) return
  const view = ed.view
  const isClickOnEditorRoot = view.dom === target || view.dom.contains(target)
  const coords = { left: event.clientX, top: event.clientY }
  const pos = view.posAtCoords(coords)
  if (pos != null) {
    if (!isClickOnEditorRoot) event.preventDefault()
    view.focus()
    ed.commands.setTextSelection(pos.pos)
  } else {
    const size = ed.state.doc.content.size
    if (size > 0) {
      event.preventDefault()
      view.focus()
      ed.commands.setTextSelection(Math.max(0, size - 1))
    }
  }
}

function escapeHtmlAttr(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeHtmlText(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const imageInput = ref<HTMLInputElement | null>(null)
function triggerImageUpload() {
  imageInput.value?.click()
}
async function onImageChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await axios.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (res.data.success) {
      const { url, fileId } = res.data.data
      fileIds.value.push(fileId)
      editor.value?.chain().focus().setImage({ src: url }).run()
    }
  } catch (err: unknown) {
    logger.error('Image upload failed:', err)
    toastStore.addToast(t('common.messages.uploadFailed'), 'error')
  }
  input.value = ''
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
  editor.value?.destroy()
})
</script>

<template>
  <div class="tiptap-editor-wrap flex flex-col flex-1 min-h-0">
    <input ref="imageInput" type="file" accept="image/*" class="hidden" @change="onImageChange" />
    <div v-if="editor"
      class="tiptap-toolbar flex flex-wrap items-center gap-1 p-2 border-b border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800">
      <!-- 인라인: 볼드, 이탤릭, 밑줄, 취소선 -->
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('bold') }" title="Bold"
        @mousedown.prevent @click="editor.chain().focus().toggleBold().run()">
        <span class="font-bold">B</span>
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('italic') }" title="Italic"
        @mousedown.prevent @click="editor.chain().focus().toggleItalic().run()">
        <span class="italic">I</span>
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('underline') }" title="Underline"
        @mousedown.prevent @click="editor.chain().focus().toggleUnderline().run()">
        <span class="underline">U</span>
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('strike') }" title="Strikethrough"
        @mousedown.prevent @click="editor.chain().focus().toggleStrike().run()">
        <span class="line-through">S</span>
      </button>
      <span class="w-px h-6 bg-gray-300 dark:bg-gray-600 mx-0.5" />
      <!-- 폰트 사이즈 -->
      <select class="tiptap-select text-xs" :value="editor.getAttributes('textStyle').fontSize || ''"
        @change="(e) => { const v = (e.target as HTMLSelectElement).value; v ? editor?.chain().focus().setFontSize(v).run() : editor?.chain().focus().unsetFontSize().run() }">
        <option value="">{{ t('board.writePost.fontSize') || '크기' }}</option>
        <option v-for="s in fontSizes" :key="s" :value="s">{{ s }}</option>
      </select>
      <!-- 줄 간격 -->
      <select class="tiptap-select text-xs" :value="editor.getAttributes('textStyle').lineHeight || ''"
        @change="(e) => { const v = (e.target as HTMLSelectElement).value; v ? editor?.chain().focus().setLineHeight(v).run() : editor?.chain().focus().unsetLineHeight().run() }">
        <option value="">{{ t('board.writePost.lineHeight') || '줄간격' }}</option>
        <option v-for="h in lineHeights" :key="h" :value="h">{{ h }}</option>
      </select>
      <span class="w-px h-6 bg-gray-300 dark:bg-gray-600 mx-0.5" />
      <!-- 텍스트 색상 -->
      <div class="relative inline-block">
        <button type="button" class="tiptap-btn tiptap-color-trigger" title="텍스트 색상" @mousedown.prevent
          @click="toggleColorPanel">
          <span class="tiptap-color-indicator">
            A
            <span class="tiptap-color-bar"
              :style="{ backgroundColor: isDefaultColor ? (themeStore.isDark ? '#f3f4f6' : '#111827') : currentTextColor }" />
          </span>
        </button>
        <!-- 색상 패널 드롭다운 -->
        <Teleport to="body">
          <div v-if="showColorPanel" class="link-popover-mask" @click.self="closeColorPanel">
            <div class="color-panel" role="dialog" aria-label="텍스트 색상 선택">
              <!-- 기본 색상 버튼 -->
              <button type="button" class="color-panel-default"
                :class="{ 'color-panel-default--active': isDefaultColor }" @click="setDefaultColor">
                <span class="color-panel-default-swatch">
                  <span class="color-panel-default-light" />
                  <span class="color-panel-default-dark" />
                </span>
                <span>기본</span>
              </button>
              <!-- 프리셋 색상 그리드 -->
              <div class="color-panel-grid">
                <button v-for="c in colorPresets" :key="c" type="button" class="color-panel-swatch"
                  :class="{ 'color-panel-swatch--active': currentTextColor === c }" :style="{ backgroundColor: c }"
                  :title="c" @click="setPresetColor(c)" />
              </div>
              <!-- 커스텀 색상 -->
              <div class="color-panel-custom">
                <label class="color-panel-custom-label">커스텀:</label>
                <input type="color" :value="currentTextColor || '#000000'" class="color-panel-custom-input"
                  @input="(e) => { editor?.chain().focus().setColor((e.target as HTMLInputElement).value).run() }">
              </div>
            </div>
          </div>
        </Teleport>
      </div>
      <!-- 배경(하이라이트) 색상 -->
      <div class="relative inline-block">
        <input type="color" :value="editor.getAttributes('highlight').color || '#fef08a'"
          class="tiptap-color-input w-6 h-6 cursor-pointer"
          @input="(e) => editor?.chain().focus().setHighlight({ color: (e.target as HTMLInputElement).value }).run()">
      </div>
      <span class="w-px h-6 bg-gray-300 dark:bg-gray-600 mx-0.5" />
      <!-- 링크 -->
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('link') }" title="Link"
        @mousedown.prevent @click="openLinkPopover">
        🔗
      </button>
      <!-- 이미지 -->
      <button type="button" class="tiptap-btn" title="Image" @mousedown.prevent @click="triggerImageUpload">
        🖼
      </button>
      <!-- 동영상 -->
      <button type="button" class="tiptap-btn" title="Video" @mousedown.prevent @click="emit('open-video')">
        ▶
      </button>
      <span class="w-px h-6 bg-gray-300 dark:bg-gray-600 mx-0.5" />
      <!-- 표 -->
      <button type="button" class="tiptap-btn" title="Table" @mousedown.prevent @click="openTablePopover">
        ⊞
      </button>
      <!-- 정렬: 왼쪽 / 가운데 / 오른쪽 / 양끝 -->
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive({ textAlign: 'left' }) }"
        :title="t('board.writePost.alignLeft')" @mousedown.prevent
        @click="editor.chain().focus().setTextAlign('left').run()">
        <TextAlignStart :size="16" />
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive({ textAlign: 'center' }) }"
        :title="t('board.writePost.alignCenter')" @mousedown.prevent
        @click="editor.chain().focus().setTextAlign('center').run()">
        <TextAlignCenter :size="16" />
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive({ textAlign: 'right' }) }"
        :title="t('board.writePost.alignRight')" @mousedown.prevent
        @click="editor.chain().focus().setTextAlign('right').run()">
        <TextAlignEnd :size="16" />
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive({ textAlign: 'justify' }) }"
        :title="t('board.writePost.alignJustify')" @mousedown.prevent
        @click="editor.chain().focus().setTextAlign('justify').run()">
        <TextAlignJustify :size="16" />
      </button>
      <span class="w-px h-6 bg-gray-300 dark:bg-gray-600 mx-0.5" />
      <!--
        리스트 동작 원리 (TipTap toggleBulletList / toggleOrderedList):
        1. 커서가 있는 블록의 blockRange를 구함 ($from.blockRange($to)).
        2. 이미 리스트 안에 있으면: 같은 타입이면 리스트 해제(liftListItem), 다른 리스트 타입이면 해당 타입으로 변경.
        3. 리스트 밖이면: can().wrapInList(listType)으로 현재 블록을 리스트로 감쌀 수 있는지 확인.
        4. 감쌀 수 없으면 clearNodes()로 블록을 기본(paragraph)으로 바꾼 뒤 wrapInList 실행.
        - 블록(paragraph, heading 등) 안에 커서가 있어야 하며, 테이블 셀/blockquote 등 특수 블록 안에서는 동작하지 않을 수 있음.
      -->
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('bulletList') }" title="Bullet list"
        @mousedown.prevent="saveListSelection" @click="applyBulletList">
        •
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('orderedList') }" title="Ordered list"
        @mousedown.prevent="saveListSelection" @click="applyOrderedList">
        1.
      </button>
      <!-- 수평선 -->
      <button type="button" class="tiptap-btn" title="Horizontal rule" @mousedown.prevent
        @click="editor.chain().focus().setHorizontalRule().run()">
        —
      </button>
      <!-- 노비콘 -->
      <button type="button" class="tiptap-btn" title="Emoticon" @mousedown.prevent @click="emit('open-emoticon')">
        😊
      </button>
    </div>

    <!-- 링크 입력 팝오버 -->
    <Teleport to="body">
      <div v-if="showLinkPopover" class="link-popover-mask" @click.self="closeLinkPopover">
        <div class="link-popover" role="dialog" aria-label="링크 삽입">
          <div class="link-popover-row">
            <label class="link-popover-label">{{ t('board.writePost.linkUrlPrompt') }}</label>
            <input v-model="linkUrl" type="url" class="link-popover-input" :placeholder="'https://...'"
              @keydown.enter.prevent="applyLink" @keydown.escape="closeLinkPopover" />
          </div>
          <div class="link-popover-row">
            <label class="link-popover-label">{{ t('board.writePost.linkDisplayText') }}</label>
            <input v-model="linkText" type="text" class="link-popover-input"
              :placeholder="t('board.writePost.linkDisplayText')" @keydown.enter.prevent="applyLink"
              @keydown.escape="closeLinkPopover" />
          </div>
          <div class="link-popover-actions">
            <BaseButton type="button" variant="secondary" size="sm" @click="closeLinkPopover">
              {{ t('common.cancel') }}
            </BaseButton>
            <button v-if="editor?.isActive('link')" type="button" class="link-popover-remove" @click="removeLink">
              {{ t('board.writePost.linkRemove') }}
            </button>
            <BaseButton type="button" variant="primary" size="sm" @click="applyLink">
              {{ t('board.writePost.linkInsert') }}
            </BaseButton>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 표 삽입 팝오버 -->
    <Teleport to="body">
      <div v-if="showTablePopover" class="link-popover-mask" @click.self="closeTablePopover">
        <div class="link-popover table-popover" role="dialog" aria-label="표 삽입">
          <div class="link-popover-row">
            <label class="link-popover-label">{{ t('board.writePost.tableRows') }}</label>
            <input v-model.number="tableRows" type="number" min="1" max="20" class="link-popover-input"
              @keydown.enter.prevent="applyTable" @keydown.escape="closeTablePopover" />
          </div>
          <div class="link-popover-row">
            <label class="link-popover-label">{{ t('board.writePost.tableCols') }}</label>
            <input v-model.number="tableCols" type="number" min="1" max="10" class="link-popover-input"
              @keydown.enter.prevent="applyTable" @keydown.escape="closeTablePopover" />
          </div>
          <div class="link-popover-row flex items-center gap-2">
            <input id="table-header-row" v-model="tableHeaderRow" type="checkbox" class="rounded border-gray-300" />
            <label for="table-header-row" class="link-popover-label !mb-0">{{ t('board.writePost.tableHeaderRow')
            }}</label>
          </div>
          <div class="link-popover-actions">
            <BaseButton type="button" variant="secondary" size="sm" @click="closeTablePopover">
              {{ t('common.cancel') }}
            </BaseButton>
            <BaseButton type="button" variant="primary" size="sm" @click="applyTable">
              {{ t('board.writePost.tableInsert') }}
            </BaseButton>
          </div>
        </div>
      </div>
    </Teleport>

    <div class="tiptap-content flex-1 min-h-0 overflow-auto border-t border-gray-200 dark:border-gray-600 cursor-text"
      @mousedown="onContentAreaClick">
      <EditorContent :editor="editor" />
    </div>
  </div>
</template>

<style scoped>
.tiptap-btn {
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.25rem;
  border: 1px solid transparent;
  color: #374151;
  background: transparent;
}

.tiptap-btn:hover {
  background: #e5e7eb;
}

.dark .tiptap-btn {
  color: #d1d5db;
}

.dark .tiptap-btn:hover {
  background: #4b5563;
}

.tiptap-btn.active {
  background: #e0e7ff;
  color: #4338ca;
  border-color: #a5b4fc;
}

.dark .tiptap-btn.active {
  background: rgba(67, 56, 202, 0.4);
  color: #a5b4fc;
  border-color: #4338ca;
}

.tiptap-select {
  height: 2rem;
  padding: 0 0.5rem;
  border-radius: 0.25rem;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #111827;
}

.dark .tiptap-select {
  border-color: #4b5563;
  background: #374151;
  color: #f9fafb;
}

.tiptap-color-input {
  border-radius: 0.25rem;
  border: 1px solid #d1d5db;
  background: transparent;
  padding: 0;
}

.dark .tiptap-color-input {
  border-color: #4b5563;
}

/* 색상 트리거 버튼 */
.tiptap-color-trigger {
  font-weight: 700;
  font-size: 14px;
  position: relative;
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
  border-radius: 1px;
  transition: background-color 0.15s;
}
</style>

<style>
/* 편집 영역이 빈 공간까지 채워져 빈 곳 클릭 시에도 커서가 잡히도록 */
.tiptap-editor-wrap .tiptap-content {
  display: flex;
  flex-direction: column;
}

.tiptap-editor-wrap .tiptap-content>div {
  flex: 1;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.tiptap-editor-wrap .tiptap-content .ProseMirror {
  flex: 1;
  min-height: 100%;
}

/* 리스트(ul/ol) 표시: 불릿·번호가 보이도록 */
.tiptap-editor-wrap .ProseMirror ul {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.tiptap-editor-wrap .ProseMirror ul ul {
  list-style-type: circle;
}

.tiptap-editor-wrap .ProseMirror ol {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.tiptap-editor-wrap .ProseMirror ol ol {
  list-style-type: lower-alpha;
}

.tiptap-editor-wrap .ProseMirror li {
  display: list-item;
  margin: 0.25em 0;
}

/* 표 테두리: 라이트/다크 테마에 맞춤 */
.tiptap-editor-wrap .ProseMirror table {
  border-collapse: collapse;
  border: 1px solid #e5e7eb;
}

.tiptap-editor-wrap .ProseMirror table td,
.tiptap-editor-wrap .ProseMirror table th {
  border: 1px solid #e5e7eb;
}

.dark .tiptap-editor-wrap .ProseMirror table {
  border-color: #4b5563;
}

.dark .tiptap-editor-wrap .ProseMirror table td,
.dark .tiptap-editor-wrap .ProseMirror table th {
  border-color: #4b5563;
}

/* 표 컬럼 리사이즈: 테마 색상 + 드래그 가능 */
.tiptap-editor-wrap .ProseMirror .tableWrapper {
  overflow-x: auto;
}

.tiptap-editor-wrap .ProseMirror table {
  table-layout: fixed;
  width: 100%;
}

.tiptap-editor-wrap .ProseMirror td,
.tiptap-editor-wrap .ProseMirror th {
  vertical-align: top;
  box-sizing: border-box;
  position: relative;
  min-width: var(--table-cell-min-width, 40px);
}

.tiptap-editor-wrap .ProseMirror .column-resize-handle {
  position: absolute;
  right: -3px;
  top: 0;
  bottom: 0;
  width: 6px;
  z-index: 20;
  background-color: #6366f1;
  cursor: col-resize;
  pointer-events: auto;
}

.dark .tiptap-editor-wrap .ProseMirror .column-resize-handle {
  background-color: #818cf8;
}

.tiptap-editor-wrap .ProseMirror.resize-cursor {
  cursor: col-resize;
}

/* 링크 서식: 에디터 내 링크 가시성 + Ctrl/Cmd+클릭 시에만 이동 */
.tiptap-editor-wrap .ProseMirror a.tiptap-link,
.tiptap-editor-wrap .ProseMirror a[href] {
  color: #2563eb;
  text-decoration: underline;
  cursor: pointer;
}

.tiptap-editor-wrap .ProseMirror a.tiptap-link:hover,
.tiptap-editor-wrap .ProseMirror a[href]:hover {
  color: #1d4ed8;
}

.dark .tiptap-editor-wrap .ProseMirror a.tiptap-link,
.dark .tiptap-editor-wrap .ProseMirror a[href] {
  color: #60a5fa;
}

.dark .tiptap-editor-wrap .ProseMirror a.tiptap-link:hover,
.dark .tiptap-editor-wrap .ProseMirror a[href]:hover {
  color: #93c5fd;
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
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.tiptap-image-inline {
  display: inline-block;
  vertical-align: baseline;
}

/* 이미지 선택 시 블록 표시 (노드 선택 시 outline) */
.tiptap-editor-wrap .ProseMirror img.ProseMirror-selectednode,
.tiptap-editor-wrap .ProseMirror .ProseMirror-selectednode img {
  outline: 2px solid #4f46e5;
  outline-offset: 2px;
  border-radius: 2px;
}

.tiptap-editor-wrap .ProseMirror .ProseMirror-selectednode:has(> img) {
  outline: 2px solid #4f46e5;
  outline-offset: 2px;
  border-radius: 2px;
  display: inline-block;
}

.dark .tiptap-editor-wrap .ProseMirror img.ProseMirror-selectednode,
.dark .tiptap-editor-wrap .ProseMirror .ProseMirror-selectednode img,
.dark .tiptap-editor-wrap .ProseMirror .ProseMirror-selectednode:has(> img) {
  outline-color: #818cf8;
}
</style>

<style scoped>
.link-popover-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.3);
}

.link-popover {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  min-width: 320px;
  max-width: 90vw;
  padding: 1rem 1.25rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 10px 25px -5px rgb(0 0 0 / 0.15);
}

.dark .link-popover {
  background: #1f2937;
  border-color: #4b5563;
}

.link-popover-row {
  margin-bottom: 0.75rem;
}

.link-popover-label {
  display: block;
  font-size: 0.75rem;
  font-weight: 500;
  color: #374151;
  margin-bottom: 0.25rem;
}

.dark .link-popover-label {
  color: #d1d5db;
}

.link-popover-input {
  display: block;
  width: 100%;
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  box-sizing: border-box;
}

.dark .link-popover-input {
  background: #374151;
  border-color: #4b5563;
  color: #f3f4f6;
}

.link-popover-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e5e7eb;
}

.dark .link-popover-actions {
  border-top-color: #4b5563;
}

.link-popover-remove {
  margin-right: auto;
  font-size: 0.875rem;
  color: #dc2626;
  background: none;
  border: none;
  cursor: pointer;
}

.link-popover-remove:hover {
  text-decoration: underline;
}

/* 색상 패널 (Teleport로 body에 렌더링) */
.color-panel {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  padding: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
  width: 220px;
  z-index: 10000;
}

.dark .color-panel {
  background: #1f2937;
  border-color: #4b5563;
}

.color-panel-default {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border-radius: 0.375rem;
  border: 1px solid #e5e7eb;
  background: transparent;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 8px;
}

.color-panel-default:hover {
  background: #f3f4f6;
}

.dark .color-panel-default {
  border-color: #4b5563;
  color: #d1d5db;
}

.dark .color-panel-default:hover {
  background: #374151;
}

.color-panel-default--active {
  border-color: #6366f1;
  background: #eef2ff;
}

.dark .color-panel-default--active {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.2);
}

.color-panel-default-swatch {
  display: inline-flex;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid #d1d5db;
  position: relative;
  flex-shrink: 0;
}

.dark .color-panel-default-swatch {
  border-color: #6b7280;
}

.color-panel-default-light {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: #111827;
  clip-path: polygon(0 0, 100% 0, 0 100%);
}

.color-panel-default-dark {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
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
  border-radius: 0.25rem;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.1);
}

.color-panel-swatch:hover {
  transform: scale(1.15);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}

.color-panel-swatch--active {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.4);
}

.color-panel-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}

.dark .color-panel-custom {
  border-top-color: #4b5563;
}

.color-panel-custom-label {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}

.dark .color-panel-custom-label {
  color: #9ca3af;
}

.color-panel-custom-input {
  width: 28px;
  height: 28px;
  border-radius: 0.25rem;
  border: 1px solid #d1d5db;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.dark .color-panel-custom-input {
  border-color: #4b5563;
}
</style>

<script setup lang="ts">
import type { Editor } from '@tiptap/core'
import {
  Image as ImageIcon,
  Link2,
  List as ListIcon,
  ListOrdered,
  SeparatorHorizontal,
  Smile,
  Table2,
  TextAlignCenter,
  TextAlignEnd,
  TextAlignJustify,
  TextAlignStart,
  Video as VideoIcon,
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import PostEditorImageUploadStatus from '@/components/board/editor/PostEditorImageUploadStatus.vue'

withDefaults(defineProps<{
  editor: Editor
  isUploadingImage: boolean
  hasImageUploadError: boolean
  currentUploadingImageName: string
  imageUploadQueueCount: number
  failedImageCount: number
  failedImageFiles: File[]
  fontSizes: string[]
  lineHeights: string[]
  codeBlockLanguages: readonly string[]
  currentFontSize: string
  currentLineHeight: string
  currentCodeBlockLanguage: string
  currentTextColor: string
  isDefaultColor: boolean
  isDark: boolean
  showSlashMenu: boolean
  showTablePopover: boolean
  showColorPanel: boolean
  activeTextAlign: 'left' | 'center' | 'right' | 'justify' | ''
  isRawHtmlBlockSelected?: boolean
}>(), {
  isRawHtmlBlockSelected: false,
})

const emit = defineEmits<{
  (e: 'toggle-bold'): void
  (e: 'toggle-italic'): void
  (e: 'toggle-underline'): void
  (e: 'toggle-strike'): void
  (e: 'open-link', trigger: HTMLElement): void
  (e: 'upload-image'): void
  (e: 'open-video'): void
  (e: 'open-emoticon'): void
  (e: 'save-list-selection'): void
  (e: 'bullet-list'): void
  (e: 'ordered-list'): void
  (e: 'font-size', value: string): void
  (e: 'line-height', value: string): void
  (e: 'code-block-language', value: string): void
  (e: 'toggle-color-panel', trigger: HTMLElement): void
  (e: 'align', value: 'left' | 'center' | 'right' | 'justify'): void
  (e: 'toggle-slash-menu', trigger: HTMLElement): void
  (e: 'open-table', trigger: HTMLElement): void
  (e: 'horizontal-rule'): void
  (e: 'retry-image-upload'): void
  (e: 'retry-failed-image-upload', file: File): void
  (e: 'cancel-image-upload'): void
  (e: 'dismiss-image-upload-error'): void
  (e: 'dismiss-failed-image-upload', file: File): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="tiptap-toolbar flex flex-col gap-2 border-b border-[var(--nv-line)] bg-[var(--nv-surface-2)] p-2">
    <p v-if="isRawHtmlBlockSelected" class="tiptap-toolbar-context" role="status">
      {{ t('board.writePost.rawHtmlBlock.selectionHint') }}
    </p>
    <div class="tiptap-toolbar-row tiptap-toolbar-row--scrollable">
      <div class="tiptap-toolbar-group tiptap-toolbar-group--format">
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('bold') }" :title="t('board.writePost.toolbar.bold')" :aria-label="t('board.writePost.toolbar.bold')" :aria-pressed="editor.isActive('bold')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('toggle-bold')">
          <span class="font-bold">B</span>
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('italic') }" :title="t('board.writePost.toolbar.italic')" :aria-label="t('board.writePost.toolbar.italic')" :aria-pressed="editor.isActive('italic')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('toggle-italic')">
          <span class="italic">I</span>
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('underline') }" :title="t('board.writePost.toolbar.underline')" :aria-label="t('board.writePost.toolbar.underline')" :aria-pressed="editor.isActive('underline')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('toggle-underline')">
          <span class="underline">U</span>
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('strike') }" :title="t('board.writePost.toolbar.strikethrough')" :aria-label="t('board.writePost.toolbar.strikethrough')" :aria-pressed="editor.isActive('strike')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('toggle-strike')">
          <span class="line-through">S</span>
        </button>
        <select class="tiptap-select text-xs" :value="currentFontSize" :aria-label="t('board.writePost.fontSize')" :disabled="isRawHtmlBlockSelected" @change="emit('font-size', ($event.target as HTMLSelectElement).value)">
          <option value="">{{ t('board.writePost.fontSize') || 'Font size' }}</option>
          <option v-for="size in fontSizes" :key="size" :value="size">{{ size }}</option>
        </select>
        <select class="tiptap-select text-xs" :value="currentLineHeight" :aria-label="t('board.writePost.lineHeight')" :disabled="isRawHtmlBlockSelected" @change="emit('line-height', ($event.target as HTMLSelectElement).value)">
          <option value="">{{ t('board.writePost.lineHeight') || 'Line height' }}</option>
          <option v-for="height in lineHeights" :key="height" :value="height">{{ height }}</option>
        </select>
        <button type="button" class="tiptap-btn tiptap-color-trigger" :title="t('board.writePost.toolbar.textColor')" :aria-label="t('board.writePost.toolbar.textColor')" aria-haspopup="dialog" :aria-expanded="showColorPanel" aria-controls="editor-color-dialog" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('toggle-color-panel', $event.currentTarget as HTMLElement)">
          <span class="tiptap-color-indicator">
            A
            <span class="tiptap-color-bar" :style="{ backgroundColor: isDefaultColor ? (isDark ? '#f3f4f6' : '#111827') : currentTextColor }" />
          </span>
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'left' }" :title="t('board.writePost.alignLeft')" :aria-label="t('board.writePost.alignLeft')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('align', 'left')">
          <TextAlignStart :size="16" />
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'center' }" :title="t('board.writePost.alignCenter')" :aria-label="t('board.writePost.alignCenter')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('align', 'center')">
          <TextAlignCenter :size="16" />
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'right' }" :title="t('board.writePost.alignRight')" :aria-label="t('board.writePost.alignRight')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('align', 'right')">
          <TextAlignEnd :size="16" />
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'justify' }" :title="t('board.writePost.alignJustify')" :aria-label="t('board.writePost.alignJustify')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('align', 'justify')">
          <TextAlignJustify :size="16" />
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('bulletList') }" :title="t('board.writePost.toolbar.bulletList')" :aria-label="t('board.writePost.toolbar.bulletList')" :aria-pressed="editor.isActive('bulletList')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent="emit('save-list-selection')" @click="emit('bullet-list')">
          <ListIcon class="h-4 w-4" aria-hidden="true" />
        </button>
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('orderedList') }" :title="t('board.writePost.toolbar.orderedList')" :aria-label="t('board.writePost.toolbar.orderedList')" :aria-pressed="editor.isActive('orderedList')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent="emit('save-list-selection')" @click="emit('ordered-list')">
          <ListOrdered class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </div>

    <div class="tiptap-toolbar-row tiptap-toolbar-row--secondary tiptap-toolbar-row--scrollable">
      <div class="tiptap-toolbar-group">
        <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('link') }" :title="t('board.writePost.toolbar.link')" :aria-label="t('board.writePost.toolbar.link')" :aria-pressed="editor.isActive('link')" :disabled="isRawHtmlBlockSelected" @mousedown.prevent @click="emit('open-link', $event.currentTarget as HTMLElement)">
          <Link2 class="h-4 w-4" aria-hidden="true" />
        </button>
        <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.image')" :aria-label="t('board.writePost.toolbar.image')" :disabled="isUploadingImage" @mousedown.prevent @click="emit('upload-image')">
          <ImageIcon class="h-4 w-4" aria-hidden="true" />
        </button>
        <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.video')" :aria-label="t('board.writePost.toolbar.video')" @mousedown.prevent @click="emit('open-video')">
          <VideoIcon class="h-4 w-4" aria-hidden="true" />
        </button>
        <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.emoticon')" :aria-label="t('board.writePost.toolbar.emoticon')" @mousedown.prevent @click="emit('open-emoticon')">
          <Smile class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <div class="tiptap-toolbar-group">
        <select
          v-if="editor.isActive('codeBlock')"
          class="tiptap-select text-xs"
          :value="currentCodeBlockLanguage"
          :aria-label="t('board.writePost.codeBlock.language')"
          @change="emit('code-block-language', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">{{ t('board.writePost.codeBlock.auto') }}</option>
          <option v-for="language in codeBlockLanguages.filter(Boolean)" :key="language" :value="language">
            {{ language }}
          </option>
        </select>
        <button type="button" class="tiptap-btn tiptap-btn-pill" :title="t('board.writePost.toolbar.slashMenu')" :aria-label="t('board.writePost.toolbar.slashMenu')" aria-haspopup="dialog" :aria-expanded="showSlashMenu" aria-controls="editor-slash-dialog" @mousedown.prevent @click="emit('toggle-slash-menu', $event.currentTarget as HTMLElement)">
          {{ t('board.writePost.toolbar.insertBlock') }}
        </button>
        <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.tableDialog')" :aria-label="t('board.writePost.toolbar.tableDialog')" aria-haspopup="dialog" :aria-expanded="showTablePopover" aria-controls="editor-table-dialog" @mousedown.prevent @click="emit('open-table', $event.currentTarget as HTMLElement)">
          <Table2 :size="16" aria-hidden="true" />
        </button>
        <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.divider')" :aria-label="t('board.writePost.toolbar.divider')" @mousedown.prevent @click="emit('horizontal-rule')">
          <SeparatorHorizontal :size="16" aria-hidden="true" />
        </button>
      </div>
    </div>

    <PostEditorImageUploadStatus
      :is-uploading-image="isUploadingImage"
      :has-image-upload-error="hasImageUploadError"
      :current-uploading-image-name="currentUploadingImageName"
      :image-upload-queue-count="imageUploadQueueCount"
      :failed-image-count="failedImageCount"
      :failed-image-files="failedImageFiles"
      @retry-image-upload="emit('retry-image-upload')"
      @retry-failed-image-upload="(file) => emit('retry-failed-image-upload', file)"
      @cancel-image-upload="emit('cancel-image-upload')"
      @dismiss-image-upload-error="emit('dismiss-image-upload-error')"
      @dismiss-failed-image-upload="(file) => emit('dismiss-failed-image-upload', file)"
    />
  </div>
</template>

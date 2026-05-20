<script setup lang="ts">
import { TextAlignCenter, TextAlignEnd, TextAlignJustify, TextAlignStart } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

defineProps<{
  fontSizes: string[]
  lineHeights: string[]
  currentFontSize: string
  currentLineHeight: string
  currentHighlightColor: string
  currentTextColor: string
  isDefaultColor: boolean
  isDark: boolean
  showColorPanel: boolean
  showTablePopover: boolean
  activeTextAlign: 'left' | 'center' | 'right' | 'justify' | ''
}>()

const emit = defineEmits<{
  (e: 'font-size', value: string): void
  (e: 'line-height', value: string): void
  (e: 'highlight-color', value: string): void
  (e: 'toggle-color-panel'): void
  (e: 'open-table'): void
  (e: 'horizontal-rule'): void
  (e: 'align', value: 'left' | 'center' | 'right' | 'justify'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="grid gap-3">
    <div class="flex flex-wrap items-center gap-2">
      <select class="tiptap-select text-xs" :value="currentFontSize" :aria-label="t('board.writePost.fontSize')" @change="emit('font-size', ($event.target as HTMLSelectElement).value)">
        <option value="">{{ t('board.writePost.fontSize') || 'Font size' }}</option>
        <option v-for="size in fontSizes" :key="size" :value="size">{{ size }}</option>
      </select>
      <select class="tiptap-select text-xs" :value="currentLineHeight" :aria-label="t('board.writePost.lineHeight')" @change="emit('line-height', ($event.target as HTMLSelectElement).value)">
        <option value="">{{ t('board.writePost.lineHeight') || 'Line height' }}</option>
        <option v-for="height in lineHeights" :key="height" :value="height">{{ height }}</option>
      </select>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <div class="relative inline-block">
        <button type="button" class="tiptap-btn tiptap-color-trigger" :title="t('board.writePost.toolbar.textColor')" :aria-label="t('board.writePost.toolbar.textColor')" aria-haspopup="dialog" :aria-expanded="showColorPanel" aria-controls="editor-color-dialog" @mousedown.prevent @click="emit('toggle-color-panel')">
          <span class="tiptap-color-indicator">
            A
            <span class="tiptap-color-bar" :style="{ backgroundColor: isDefaultColor ? (isDark ? '#f3f4f6' : '#111827') : currentTextColor }" />
          </span>
        </button>
      </div>
      <input type="color" :value="currentHighlightColor" class="tiptap-color-input w-9 h-9 cursor-pointer" :aria-label="t('board.writePost.toolbar.textColor')" @input="emit('highlight-color', ($event.target as HTMLInputElement).value)">
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.tableDialog')" :aria-label="t('board.writePost.toolbar.tableDialog')" aria-haspopup="dialog" :aria-expanded="showTablePopover" aria-controls="editor-table-dialog" @mousedown.prevent @click="emit('open-table')">Tbl</button>
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.divider')" :aria-label="t('board.writePost.toolbar.divider')" @mousedown.prevent @click="emit('horizontal-rule')">HR</button>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'left' }" :title="t('board.writePost.alignLeft')" :aria-label="t('board.writePost.alignLeft')" @mousedown.prevent @click="emit('align', 'left')">
        <TextAlignStart :size="16" />
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'center' }" :title="t('board.writePost.alignCenter')" :aria-label="t('board.writePost.alignCenter')" @mousedown.prevent @click="emit('align', 'center')">
        <TextAlignCenter :size="16" />
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'right' }" :title="t('board.writePost.alignRight')" :aria-label="t('board.writePost.alignRight')" @mousedown.prevent @click="emit('align', 'right')">
        <TextAlignEnd :size="16" />
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: activeTextAlign === 'justify' }" :title="t('board.writePost.alignJustify')" :aria-label="t('board.writePost.alignJustify')" @mousedown.prevent @click="emit('align', 'justify')">
        <TextAlignJustify :size="16" />
      </button>
    </div>
  </div>
</template>

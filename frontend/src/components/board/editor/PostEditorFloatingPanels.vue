<script setup lang="ts">
import type { CSSProperties, Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PostEditorColorPopover from '@/components/board/editor/PostEditorColorPopover.vue'
import PostEditorLinkPopover from '@/components/board/editor/PostEditorLinkPopover.vue'
import PostEditorPopoverMask from '@/components/board/editor/PostEditorPopoverMask.vue'
import PostEditorSlashMenu from '@/components/board/editor/PostEditorSlashMenu.vue'
import PostEditorTablePopover from '@/components/board/editor/PostEditorTablePopover.vue'
import type { SlashAction } from '@/components/board/editor/postEditorOptions'

type FloatingPanelPosition = {
  popoverStyle: Ref<CSSProperties>
}

const props = defineProps<{
  showSlashMenu: boolean
  showColorPanel: boolean
  showLinkPopover: boolean
  showTablePopover: boolean
  assignSlashPopover: (value: HTMLElement | null) => void
  assignColorPanel: (value: HTMLElement | null) => void
  assignLinkPopover: (value: HTMLElement | null) => void
  assignTablePopover: (value: HTMLElement | null) => void
  slashPosition: FloatingPanelPosition
  colorPosition: FloatingPanelPosition
  linkPosition: FloatingPanelPosition
  tablePosition: FloatingPanelPosition
  slashActions: SlashAction[]
  slashActiveIndex: number
  colorPresets: string[]
  colorPresetLabels: Record<string, string>
  currentTextColor: string
  isDefaultColor: boolean
  linkUrl: string
  linkText: string
  canRemoveLink: boolean
  tableRows: number
  tableCols: number
  tableHeaderRow: boolean
}>()

const emit = defineEmits<{
  (e: 'close-slash-menu'): void
  (e: 'select-slash-action', action: SlashAction): void
  (e: 'move-slash-selection', direction: 1 | -1): void
  (e: 'set-slash-selection', index: number): void
  (e: 'set-default-color'): void
  (e: 'set-preset-color', color: string): void
  (e: 'close-color-panel'): void
  (e: 'apply-link', url: string, text: string): void
  (e: 'remove-link'): void
  (e: 'close-link-popover'): void
  (e: 'update:tableRows', value: number): void
  (e: 'update:tableCols', value: number): void
  (e: 'update:tableHeaderRow', value: boolean): void
  (e: 'apply-table'): void
  (e: 'close-table-popover'): void
}>()

const { t } = useI18n()

function getElementRef(value: unknown): HTMLElement | null {
  return value instanceof HTMLElement ? value : null
}
</script>

<template>
  <PostEditorPopoverMask :open="showSlashMenu" @close="emit('close-slash-menu')">
    <div
      id="editor-slash-dialog"
      :ref="(value) => props.assignSlashPopover(getElementRef(value))"
      class="link-popover slash-popover"
      :style="slashPosition.popoverStyle.value"
      role="dialog"
      aria-modal="true"
      aria-labelledby="editor-slash-dialog-title"
    >
      <div class="mb-3">
        <p class="text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">{{ t('board.writePost.toolbar.slashMenu') }}</p>
        <h3 id="editor-slash-dialog-title" class="text-base font-semibold text-[var(--nv-ink)]">{{ t('board.writePost.toolbar.insertBlock') }}</h3>
      </div>
      <PostEditorSlashMenu
        :actions="slashActions"
        :active-index="slashActiveIndex"
        @select="emit('select-slash-action', $event)"
        @move="emit('move-slash-selection', $event)"
        @set-active="emit('set-slash-selection', $event)"
        @close="emit('close-slash-menu')"
      />
    </div>
  </PostEditorPopoverMask>

  <Teleport to="body">
    <div
      v-if="showColorPanel"
      id="editor-color-dialog"
      :ref="(value) => props.assignColorPanel(getElementRef(value))"
      class="color-panel"
      :style="colorPosition.popoverStyle.value"
      role="dialog"
      aria-modal="true"
      aria-labelledby="editor-color-dialog-title"
      @keydown.enter.stop
      @keydown.escape.stop.prevent="emit('close-color-panel')"
    >
      <p id="editor-color-dialog-title" class="sr-only">{{ t('board.writePost.toolbar.textColor') }}</p>
      <PostEditorColorPopover
        :colors="colorPresets"
        :labels="colorPresetLabels"
        :current-text-color="currentTextColor"
        :is-default-color="isDefaultColor"
        @default-color="emit('set-default-color')"
        @preset-color="emit('set-preset-color', $event)"
        @custom-color="emit('set-preset-color', $event)"
      />
    </div>
  </Teleport>

  <PostEditorPopoverMask :open="showLinkPopover" @close="emit('close-link-popover')">
    <div
      :ref="(value) => props.assignLinkPopover(getElementRef(value))"
      class="link-popover"
      :style="linkPosition.popoverStyle.value"
      role="dialog"
      aria-modal="true"
      aria-labelledby="editor-link-dialog-title"
    >
      <h3 id="editor-link-dialog-title" class="sr-only">{{ t('board.writePost.toolbar.linkDialog') }}</h3>
      <PostEditorLinkPopover
        :url="linkUrl"
        :text="linkText"
        :can-remove="canRemoveLink"
        @apply="(url, text) => emit('apply-link', url, text)"
        @close="emit('close-link-popover')"
        @remove="emit('remove-link')"
      />
    </div>
  </PostEditorPopoverMask>

  <PostEditorPopoverMask :open="showTablePopover" @close="emit('close-table-popover')">
    <div
      :ref="(value) => props.assignTablePopover(getElementRef(value))"
      class="link-popover table-popover"
      :style="tablePosition.popoverStyle.value"
      role="dialog"
      aria-modal="true"
      aria-labelledby="editor-table-dialog-title"
    >
      <h3 id="editor-table-dialog-title" class="sr-only">{{ t('board.writePost.toolbar.tableDialog') }}</h3>
      <PostEditorTablePopover
        :rows="tableRows"
        :cols="tableCols"
        :header-row="tableHeaderRow"
        @update:rows="emit('update:tableRows', $event)"
        @update:cols="emit('update:tableCols', $event)"
        @update:header-row="emit('update:tableHeaderRow', $event)"
        @apply="emit('apply-table')"
        @close="emit('close-table-popover')"
      />
    </div>
  </PostEditorPopoverMask>
</template>

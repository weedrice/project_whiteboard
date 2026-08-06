<script setup lang="ts">
import { defineAsyncComponent, type ComponentPublicInstance } from 'vue'
import BaseSegmentedControl, { type SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import PostVideoUrlPopover from '@/components/board/PostVideoUrlPopover.vue'
import type { EmoticonImage } from '@/types/emoticon'
import { POST_CONTENT_MAX_LENGTH } from '@/utils/postForm'

const PostEditorTipTap = defineAsyncComponent(() => import('@/components/board/PostEditorTipTap.vue'))

defineProps<{
  modelValue: string
  editorViewMode: string
  editorViewOptions: SegmentedControlOption[]
  uploadOwnerIdentity: string
  pollEnabled?: boolean
  showVideoPopover: boolean
  showEmoticonPicker: boolean
  videoUrl: string
  videoPopoverStyle: { top: string; left: string }
  assignTiptapEditor: (value: Element | ComponentPublicInstance | null) => void
  assignEditorWrapper: (value: Element | ComponentPublicInstance | null) => void
  assignVideoPopover: (value: Element | ComponentPublicInstance | null) => void
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'update:editorViewMode', value: string): void
  (event: 'update:showEmoticonPicker', value: boolean): void
  (event: 'update:videoUrl', value: string): void
  (event: 'openVideo'): void
  (event: 'closeVideo'): void
  (event: 'insertVideo'): void
  (event: 'selectEmoticon', image: EmoticonImage): void
  (event: 'fileUploaded', fileId: number): void
  (event: 'openPoll'): void
}>()
</script>

<template>
  <div class="nv-compose-editor-section mt-4">
    <div class="flex items-center justify-between rounded-t-xl border border-[var(--nv-line)] border-b-0 bg-[var(--nv-elevated)] px-3 py-2">
      <div class="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
        <span>{{ $t('board.writePost.sections.editor') }}</span>
      </div>
      <BaseSegmentedControl
        :model-value="editorViewMode"
        :options="editorViewOptions"
        :label="$t('board.writePost.sections.editor')"
        variant="pill"
        @update:model-value="emit('update:editorViewMode', String($event))"
      />
    </div>

    <div class="editor-area-container rounded-b-xl border border-[var(--nv-line)]">
      <div
        v-if="editorViewMode === 'visual'"
        :ref="assignEditorWrapper"
        class="tiptap-editor-wrapper"
      >
        <PostEditorTipTap
          :ref="assignTiptapEditor"
          :model-value="modelValue"
          :upload-owner-identity="uploadOwnerIdentity"
          :poll-enabled="pollEnabled"
          @update:model-value="emit('update:modelValue', $event)"
          @open-video="emit('openVideo')"
          @open-emoticon="emit('update:showEmoticonPicker', true)"
          @file-uploaded="emit('fileUploaded', $event)"
          @open-poll="emit('openPoll')"
        />
        <PostVideoUrlPopover
          :show="showVideoPopover"
          :model-value="videoUrl"
          :popover-style="videoPopoverStyle"
          :assign-popover-ref="assignVideoPopover"
          @update:model-value="emit('update:videoUrl', $event)"
          @close="emit('closeVideo')"
          @submit="emit('insertVideo')"
        />
        <EmoticonPicker
          :show="showEmoticonPicker"
          @select="emit('selectEmoticon', $event)"
          @close="emit('update:showEmoticonPicker', false)"
        />
      </div>

      <div v-else class="html-source-editor-wrap">
        <BaseTextarea
          id="content"
          :model-value="modelValue"
          class="h-full"
          input-class="html-source-textarea"
          :label="$t('board.writePost.sections.editor')"
          hide-label
          :placeholder="$t('board.writePost.htmlSourcePlaceholder')"
          spellcheck="false"
          :maxlength="POST_CONTENT_MAX_LENGTH"
          @update:model-value="emit('update:modelValue', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor-area-container {
  background: var(--nv-surface);
  overflow: hidden;
}

.tiptap-editor-wrapper {
  display: flex;
  height: 26rem;
  min-height: 26rem;
  flex-direction: column;
  overflow: hidden;
}

.html-source-editor-wrap {
  height: 26rem;
  overflow: hidden;
}

@media (min-width: 1024px) {
  .nv-compose-editor-section {
    display: flex;
    flex: 1;
    min-height: 0;
    flex-direction: column;
  }

  .editor-area-container {
    display: flex;
    flex: 1;
    min-height: 26rem;
    flex-direction: column;
  }

  .tiptap-editor-wrapper,
  .html-source-editor-wrap {
    flex: 1;
    height: auto;
  }
}

:deep(.html-source-textarea) {
  display: block;
  width: 100%;
  height: 100%;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--nv-ink);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  box-sizing: border-box;
}
</style>

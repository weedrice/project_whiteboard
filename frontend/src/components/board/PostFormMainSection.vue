<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import PostTags from '@/components/tag/PostTags.vue'
import PostFormEditorSection from '@/components/board/PostFormEditorSection.vue'
import PostFormMetadataPanel from '@/components/board/PostFormMetadataPanel.vue'
import PostPollEditor from '@/components/board/PostPollEditor.vue'
import type { SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import type {
  PostFormMetadataPanelHandlers,
  PostFormMetadataPanelProps,
} from '@/features/board/posts/form/usePostFormMetadataBindings'
import type { EmoticonImage } from '@/types/emoticon'
import type { PostFormPoll } from '@/utils/postForm'

const props = defineProps<{
  title: string
  titleMaxLength: number
  content: string
  titleError?: string
  tags: string[]
  poll: PostFormPoll | null
  pollReadOnly?: boolean
  hideTags?: boolean
  metadataPanelProps: PostFormMetadataPanelProps
  metadataPanelHandlers: PostFormMetadataPanelHandlers
  editorViewMode: string
  editorViewOptions: SegmentedControlOption[]
  uploadOwnerIdentity: string
  showVideoPopover: boolean
  showEmoticonPicker: boolean
  videoUrl: string
  videoPopoverStyle: { top: string; left: string }
  assignTiptapEditor: (value: Element | ComponentPublicInstance | null) => void
  assignEditorWrapper: (value: Element | ComponentPublicInstance | null) => void
  assignVideoPopover: (value: Element | ComponentPublicInstance | null) => void
}>()

const emit = defineEmits<{
  (event: 'update:title', value: string): void
  (event: 'update:content', value: string): void
  (event: 'update:tags', value: string[]): void
  (event: 'update:poll', value: PostFormPoll | null): void
  (event: 'openPoll'): void
  (event: 'update:editorViewMode', value: string): void
  (event: 'update:showEmoticonPicker', value: boolean): void
  (event: 'update:videoUrl', value: string): void
  (event: 'openVideo'): void
  (event: 'closeVideo'): void
  (event: 'insertVideo'): void
  (event: 'selectEmoticon', image: EmoticonImage): void
  (event: 'fileUploaded', fileId: number): void
  (event: 'blurTitle'): void
}>()

function handleOpenPoll() {
  if (!props.pollReadOnly) emit('openPoll')
}
</script>

<template>
  <section class="nv-compose-main">
    <div class="nv-compose-main-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)] sm:p-5">
      <PostFormMetadataPanel
        layout="mobile"
        v-bind="metadataPanelProps"
        v-on="metadataPanelHandlers"
      />

      <BaseInput
        id="title"
        :model-value="title"
        name="title"
        type="text"
        required
        :maxlength="titleMaxLength"
        :placeholder="$t('board.writePost.placeholder.title')"
        :label="$t('common.title')"
        hide-label
        :error="titleError"
        inputClass="!rounded-xl !border-[var(--nv-line)] !bg-[var(--nv-elevated)] !px-4 !py-3 !text-sm sm:!text-base"
        @update:model-value="emit('update:title', String($event))"
        @blur="emit('blurTitle')"
      />

      <PostFormEditorSection
        :model-value="content"
        :editor-view-mode="editorViewMode"
        :editor-view-options="editorViewOptions"
        :upload-owner-identity="uploadOwnerIdentity"
        :poll-enabled="!pollReadOnly"
        :show-video-popover="showVideoPopover"
        :show-emoticon-picker="showEmoticonPicker"
        :video-url="videoUrl"
        :video-popover-style="videoPopoverStyle"
        :assign-tiptap-editor="assignTiptapEditor"
        :assign-editor-wrapper="assignEditorWrapper"
        :assign-video-popover="assignVideoPopover"
        @update:model-value="emit('update:content', $event)"
        @update:editor-view-mode="emit('update:editorViewMode', $event)"
        @update:show-emoticon-picker="emit('update:showEmoticonPicker', $event)"
        @update:video-url="emit('update:videoUrl', $event)"
        @open-video="emit('openVideo')"
        @close-video="emit('closeVideo')"
        @insert-video="emit('insertVideo')"
        @select-emoticon="emit('selectEmoticon', $event)"
        @file-uploaded="emit('fileUploaded', $event)"
        @open-poll="handleOpenPoll"
      />

      <PostPollEditor
        :model-value="poll"
        :read-only="pollReadOnly"
        @update:model-value="emit('update:poll', $event)"
      />

      <div v-if="!hideTags" class="mt-5 lg:hidden">
        <label for="post-tags-input-mobile" class="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
          {{ $t('common.tags') }}
        </label>
        <PostTags
          :model-value="tags"
          input-id="post-tags-input-mobile"
          @update:model-value="emit('update:tags', $event)"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.nv-compose-main {
  min-width: 0;
}

.nv-compose-main-card {
  position: relative;
}

@media (min-width: 1024px) {
  .nv-compose-main,
  .nv-compose-main-card {
    height: 100%;
  }

  .nv-compose-main-card {
    display: flex;
    flex-direction: column;
  }
}
</style>

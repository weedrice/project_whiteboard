<script setup lang="ts">
import type { Editor } from '@tiptap/core'
import { Image as ImageIcon, Video as VideoIcon } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

defineProps<{
  editor: Editor
  isUploadingImage: boolean
  hasImageUploadError: boolean
  showSlashMenu: boolean
  showAdvancedMenu: boolean
}>()

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
  (e: 'toggle-slash-menu', trigger: HTMLElement): void
  (e: 'toggle-advanced-menu', trigger: HTMLElement): void
  (e: 'retry-image-upload'): void
  (e: 'cancel-image-upload'): void
  (e: 'dismiss-image-upload-error'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="tiptap-toolbar flex flex-wrap items-center gap-2 border-b border-[var(--nv-line)] bg-[var(--nv-surface-alt)] p-2">
    <div class="tiptap-toolbar-group">
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('bold') }" :title="t('board.writePost.toolbar.bold')" :aria-label="t('board.writePost.toolbar.bold')" :aria-pressed="editor.isActive('bold')" @mousedown.prevent @click="emit('toggle-bold')">
        <span class="font-bold">B</span>
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('italic') }" :title="t('board.writePost.toolbar.italic')" :aria-label="t('board.writePost.toolbar.italic')" :aria-pressed="editor.isActive('italic')" @mousedown.prevent @click="emit('toggle-italic')">
        <span class="italic">I</span>
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('underline') }" :title="t('board.writePost.toolbar.underline')" :aria-label="t('board.writePost.toolbar.underline')" :aria-pressed="editor.isActive('underline')" @mousedown.prevent @click="emit('toggle-underline')">
        <span class="underline">U</span>
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('strike') }" :title="t('board.writePost.toolbar.strikethrough')" :aria-label="t('board.writePost.toolbar.strikethrough')" :aria-pressed="editor.isActive('strike')" @mousedown.prevent @click="emit('toggle-strike')">
        <span class="line-through">S</span>
      </button>
    </div>

    <div class="tiptap-toolbar-group">
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('link') }" :title="t('board.writePost.toolbar.link')" :aria-label="t('board.writePost.toolbar.link')" :aria-pressed="editor.isActive('link')" @mousedown.prevent @click="emit('open-link', $event.currentTarget as HTMLElement)">
        {{ t('board.writePost.toolbar.link') }}
      </button>
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.image')" :aria-label="t('board.writePost.toolbar.image')" :disabled="isUploadingImage" @mousedown.prevent @click="emit('upload-image')">
        <ImageIcon class="h-4 w-4" aria-hidden="true" />
      </button>
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.video')" :aria-label="t('board.writePost.toolbar.video')" @mousedown.prevent @click="emit('open-video')">
        <VideoIcon class="h-4 w-4" aria-hidden="true" />
      </button>
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.emoticon')" :aria-label="t('board.writePost.toolbar.emoticon')" @mousedown.prevent @click="emit('open-emoticon')">
        :)
      </button>
    </div>

    <div class="tiptap-toolbar-group">
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('bulletList') }" :title="t('board.writePost.toolbar.bulletList')" :aria-label="t('board.writePost.toolbar.bulletList')" :aria-pressed="editor.isActive('bulletList')" @mousedown.prevent="emit('save-list-selection')" @click="emit('bullet-list')">
        UL
      </button>
      <button type="button" class="tiptap-btn" :class="{ active: editor.isActive('orderedList') }" :title="t('board.writePost.toolbar.orderedList')" :aria-label="t('board.writePost.toolbar.orderedList')" :aria-pressed="editor.isActive('orderedList')" @mousedown.prevent="emit('save-list-selection')" @click="emit('ordered-list')">
        1.
      </button>
    </div>

    <div class="tiptap-toolbar-group">
      <button type="button" class="tiptap-btn tiptap-btn-pill" :title="t('board.writePost.toolbar.slashMenu')" :aria-label="t('board.writePost.toolbar.slashMenu')" aria-haspopup="dialog" :aria-expanded="showSlashMenu" aria-controls="editor-slash-dialog" @mousedown.prevent @click="emit('toggle-slash-menu', $event.currentTarget as HTMLElement)">
        {{ t('board.writePost.toolbar.insertBlock') }}
      </button>
      <button type="button" class="tiptap-btn tiptap-btn-pill" :title="t('board.writePost.toolbar.more')" :aria-label="t('board.writePost.toolbar.more')" aria-haspopup="dialog" :aria-expanded="showAdvancedMenu" aria-controls="editor-advanced-dialog" @mousedown.prevent @click="emit('toggle-advanced-menu', $event.currentTarget as HTMLElement)">
        {{ t('board.writePost.toolbar.more') }}
      </button>
    </div>

    <div v-if="isUploadingImage || hasImageUploadError" class="image-upload-status" role="status">
      <template v-if="isUploadingImage">
        <BaseSpinner size="sm" />
        <span>{{ t('board.writePost.upload.uploading') }}</span>
        <button type="button" class="image-upload-status-btn" @click="emit('cancel-image-upload')">
          {{ t('board.writePost.upload.cancel') }}
        </button>
      </template>
      <template v-else>
        <span>{{ t('common.messages.uploadFailed') }}</span>
        <button type="button" class="image-upload-status-btn" @click="emit('retry-image-upload')">
          {{ t('board.writePost.upload.retry') }}
        </button>
        <button type="button" class="image-upload-status-btn" :aria-label="t('board.writePost.upload.dismiss')" @click="emit('dismiss-image-upload-error')">
          x
        </button>
      </template>
    </div>
  </div>
</template>

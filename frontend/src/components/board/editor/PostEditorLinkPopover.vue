<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  url: string
  text: string
  canRemove: boolean
}>()

const emit = defineEmits<{
  (e: 'apply', url: string, text: string): void
  (e: 'close'): void
  (e: 'remove'): void
}>()

const { t } = useI18n()
const localUrl = ref(props.url)
const localText = ref(props.text)

watch(() => props.url, (value) => {
  localUrl.value = value
})

watch(() => props.text, (value) => {
  localText.value = value
})

function apply() {
  emit('apply', localUrl.value, localText.value)
}
</script>

<template>
  <div class="link-popover-row">
    <label for="editor-link-url" class="link-popover-label">{{ t('board.writePost.linkUrlPrompt') }}</label>
    <input
      id="editor-link-url"
      v-model="localUrl"
      type="url"
      name="editorLinkUrl"
      inputmode="url"
      autocomplete="off"
      class="link-popover-input"
      placeholder="https://..."
      @keydown.enter.stop.prevent="apply"
      @keydown.escape.stop.prevent="emit('close')"
    >
  </div>
  <div class="link-popover-row">
    <label for="editor-link-text" class="link-popover-label">{{ t('board.writePost.linkDisplayText') }}</label>
    <input
      id="editor-link-text"
      v-model="localText"
      type="text"
      name="editorLinkText"
      autocomplete="off"
      class="link-popover-input"
      :placeholder="t('board.writePost.linkDisplayText')"
      @keydown.enter.stop.prevent="apply"
      @keydown.escape.stop.prevent="emit('close')"
    >
  </div>
  <div class="link-popover-actions">
    <BaseButton type="button" variant="secondary" size="sm" @click="emit('close')">
      {{ t('common.cancel') }}
    </BaseButton>
    <button v-if="canRemove" type="button" class="link-popover-remove" @click="emit('remove')">
      {{ t('board.writePost.linkRemove') }}
    </button>
    <BaseButton type="button" variant="primary" size="sm" @click="apply">
      {{ t('board.writePost.linkInsert') }}
    </BaseButton>
  </div>
</template>

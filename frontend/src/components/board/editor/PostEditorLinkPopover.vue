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
  (e: 'update:url', value: string): void
  (e: 'update:text', value: string): void
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

function updateUrl(value: string) {
  localUrl.value = value
  emit('update:url', value)
}

function updateText(value: string) {
  localText.value = value
  emit('update:text', value)
}

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
      class="link-popover-input"
      placeholder="https://..."
      @input="updateUrl(($event.target as HTMLInputElement).value)"
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
      class="link-popover-input"
      :placeholder="t('board.writePost.linkDisplayText')"
      @input="updateText(($event.target as HTMLInputElement).value)"
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
    <button type="button" class="btn-primary btn-sm flex items-center justify-center" data-testid="editor-link-insert" @click="apply">
      {{ t('board.writePost.linkInsert') }}
    </button>
  </div>
</template>

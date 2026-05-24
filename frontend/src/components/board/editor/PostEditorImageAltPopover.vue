<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  alt: string
}>()

const emit = defineEmits<{
  (e: 'apply', value: string): void
  (e: 'clear'): void
  (e: 'close'): void
}>()

const { t } = useI18n()
const localAlt = ref(props.alt)

watch(() => props.alt, (value) => {
  localAlt.value = value
})

function apply() {
  emit('apply', localAlt.value)
}
</script>

<template>
  <div class="link-popover-row">
    <label for="editor-image-alt" class="link-popover-label">{{ t('board.writePost.imageAlt.label') }}</label>
    <input
      id="editor-image-alt"
      v-model="localAlt"
      type="text"
      name="editorImageAlt"
      autocomplete="off"
      aria-describedby="editor-image-alt-help"
      class="link-popover-input"
      :placeholder="t('board.writePost.imageAlt.placeholder')"
      @keydown.enter.stop.prevent="apply"
      @keydown.escape.stop.prevent="emit('close')"
    >
    <p id="editor-image-alt-help" class="image-alt-help">{{ t('board.writePost.imageAlt.help') }}</p>
  </div>
  <div class="link-popover-actions">
    <BaseButton type="button" variant="secondary" size="sm" @click="emit('close')">
      {{ t('common.cancel') }}
    </BaseButton>
    <button type="button" class="link-popover-remove" @click="emit('clear')">
      {{ t('board.writePost.imageAlt.clear') }}
    </button>
    <BaseButton type="button" variant="primary" size="sm" @click="apply">
      {{ t('board.writePost.imageAlt.apply') }}
    </BaseButton>
  </div>
</template>

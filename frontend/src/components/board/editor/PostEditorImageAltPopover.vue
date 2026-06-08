<script setup lang="ts">
import { ref, watch } from 'vue'
import PostEditorPopoverActions from './PostEditorPopoverActions.vue'
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
  <PostEditorPopoverActions
    :cancel-label="t('common.cancel')"
    :apply-label="t('board.writePost.imageAlt.apply')"
    @close="emit('close')"
    @apply="apply"
  >
    <template #secondary-action>
      <button type="button" class="link-popover-remove" @click="emit('clear')">
        {{ t('board.writePost.imageAlt.clear') }}
      </button>
    </template>
  </PostEditorPopoverActions>
</template>

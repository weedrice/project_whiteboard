<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  colors: string[]
  labels: Record<string, string>
  currentTextColor: string
  isDefaultColor: boolean
}>()

const emit = defineEmits<{
  (e: 'default-color'): void
  (e: 'preset-color', color: string): void
  (e: 'custom-color', color: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <button
    type="button"
    class="color-panel-default"
    :class="{ 'color-panel-default--active': isDefaultColor }"
    :aria-pressed="isDefaultColor"
    @click="emit('default-color')"
  >
    <span class="color-panel-default-swatch">
      <span class="color-panel-default-light" />
      <span class="color-panel-default-dark" />
    </span>
    <span>{{ t('board.writePost.toolbar.defaultColor') }}</span>
  </button>
  <div class="color-panel-grid">
    <button
      v-for="color in colors"
      :key="color"
      type="button"
      class="color-panel-swatch"
      :class="{ 'color-panel-swatch--active': currentTextColor === color }"
      :style="{ backgroundColor: color }"
      :title="labels[color] ?? color"
      :aria-label="labels[color] ?? color"
      :aria-pressed="currentTextColor === color"
      @click="emit('preset-color', color)"
    />
  </div>
  <div class="color-panel-custom">
    <label for="editor-custom-text-color" class="color-panel-custom-label">{{ t('board.writePost.toolbar.customColor') }}</label>
    <input
      id="editor-custom-text-color"
      name="editorCustomTextColor"
      type="color"
      :value="currentTextColor || '#000000'"
      class="color-panel-custom-input"
      :aria-label="t('board.writePost.toolbar.customColor')"
      autocomplete="off"
      @input="emit('custom-color', ($event.target as HTMLInputElement).value)"
    >
  </div>
</template>

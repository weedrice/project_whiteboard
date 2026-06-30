<script setup lang="ts">
import { ref } from 'vue'
import { applyImageFallback } from '@/utils/imageFallback'
import type { SanitizedHtml } from '@/utils/sanitize'

withDefaults(defineProps<{
  html: SanitizedHtml
  tag?: string
  useImageFallback?: boolean
}>(), {
  tag: 'div',
  useImageFallback: true,
})

const element = ref<HTMLElement | null>(null)

defineExpose({
  element,
})
</script>

<template>
  <component
    :is="tag"
    ref="element"
    v-html="html"
    @error.capture="useImageFallback ? applyImageFallback($event) : undefined"
  />
</template>

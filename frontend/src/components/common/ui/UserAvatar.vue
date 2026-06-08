<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  imageUrl?: string | null
  name?: string | null
  alt?: string
  sizeClass?: string
  imageClass?: string
  fallbackClass?: string
}>(), {
  alt: 'profile',
  sizeClass: 'h-8 w-8',
  imageClass: 'object-cover',
  fallbackClass: 'text-xs font-semibold',
})

defineEmits<{
  (e: 'image-error', event: Event): void
}>()

const fallbackInitial = computed(() => {
  const name = props.name?.trim()
  return name ? name.slice(0, 1).toUpperCase() : '?'
})
</script>

<template>
  <div
    class="flex shrink-0 items-center justify-center overflow-hidden rounded-full nv-avatar-fallback"
    :class="sizeClass"
  >
    <img
      v-if="imageUrl"
      :src="imageUrl"
      :alt="alt"
      class="h-full w-full"
      :class="imageClass"
      @error="$emit('image-error', $event)"
    />
    <span v-else :class="fallbackClass">{{ fallbackInitial }}</span>
  </div>
</template>

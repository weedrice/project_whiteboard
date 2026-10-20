<script setup lang="ts">
import { vImageState } from '@/directives/imageState'
import { computed, ref, watch } from 'vue'

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

const emit = defineEmits<{
  (e: 'image-error', event: Event): void
}>()

const imageFailed = ref(false)
watch(() => props.imageUrl, () => { imageFailed.value = false }, { flush: 'sync' })

function handleImageError(event: Event) {
  imageFailed.value = true
  emit('image-error', event)
}

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
    <img v-image-state
      v-if="imageUrl && !imageFailed"
      :src="imageUrl"
      :alt="alt"
      decoding="async"
      class="h-full w-full"
      :class="imageClass"
      @error="handleImageError"
    />
    <span v-else :class="fallbackClass">{{ fallbackInitial }}</span>
  </div>
</template>

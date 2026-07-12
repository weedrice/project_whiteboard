<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { EmoticonImage } from '@/types/emoticon'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'

defineProps<{
  images: EmoticonImage[]
  emoticonName: string
}>()

const emit = defineEmits<{
  (e: 'select', image: EmoticonImage): void
}>()

const { t } = useI18n()
</script>

<template>
  <div
    class="images-grid"
    role="list"
    :aria-label="`${emoticonName} ${images.length}`"
  >
    <div
      v-for="image in images"
      :key="image.imageId"
      class="emoticon-grid-item"
      role="listitem"
    >
      <button
        type="button"
        :aria-label="t('emoticon.picker.imageSelectAria', { name: emoticonName })"
        class="image-btn"
        @click="emit('select', image)"
      >
        <img
          :src="image.imageUrl || DEFAULT_EMOTICON_IMAGE_URL"
          :alt="emoticonName"
          loading="lazy"
          decoding="async"
          @error="applyImageFallback"
        />
      </button>
    </div>
  </div>
</template>

<style scoped>
.images-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, 1fr);
}

.emoticon-grid-item {
  min-width: 0;
}

.image-btn {
  border-radius: 6px;
  cursor: pointer;
  padding: 6px;
  transition: background-color 0.2s;
  width: 100%;
}

.image-btn:hover {
  background: var(--nv-surface-hover);
}

.image-btn img {
  aspect-ratio: 1;
  object-fit: contain;
  width: 100%;
}

@media (max-width: 639px) {
  .images-grid {
    gap: 6px;
    grid-template-columns: repeat(3, 1fr);
  }

  .image-btn {
    border-radius: 4px;
    padding: 4px;
  }
}
</style>

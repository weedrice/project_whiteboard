<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { EmoticonMaster } from '@/types/emoticon'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'

defineProps<{
  emoticons: EmoticonMaster[]
}>()

const emit = defineEmits<{
  (e: 'select', emoticon: EmoticonMaster): void
}>()

const { t } = useI18n()
</script>

<template>
  <div
    class="emoticons-grid"
    role="list"
    :aria-label="`${t('emoticon.title')} ${emoticons.length}`"
  >
    <div
      v-for="emoticon in emoticons"
      :key="emoticon.emoticonId"
      class="emoticon-grid-item"
      role="listitem"
    >
      <button
        type="button"
        :aria-label="t('emoticon.picker.imageSelectAria', { name: emoticon.name })"
        class="emoticon-btn"
        @click="emit('select', emoticon)"
      >
        <img
          :src="emoticon.thumbnailUrl || emoticon.images?.[0]?.imageUrl || DEFAULT_EMOTICON_IMAGE_URL"
          :alt="emoticon.name"
          loading="lazy"
          decoding="async"
          @error="applyImageFallback"
        />
        <span class="emoticon-name">{{ emoticon.name }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.emoticons-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, 1fr);
}

.emoticon-grid-item {
  min-width: 0;
}

.emoticon-btn {
  align-items: center;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  padding: 8px;
  transition: background-color 0.2s;
  width: 100%;
}

.emoticon-btn:hover {
  background: var(--nv-surface-hover);
}

.emoticon-btn img {
  border-radius: 4px;
  height: 64px;
  object-fit: contain;
  width: 64px;
}

.emoticon-name {
  color: var(--nv-ink-soft);
  font-size: 11px;
  margin-top: 4px;
  max-width: 100%;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 639px) {
  .emoticons-grid {
    gap: 6px;
    grid-template-columns: repeat(2, 1fr);
  }

  .emoticon-btn {
    border-radius: 6px;
    padding: 6px;
  }

  .emoticon-btn img {
    height: 48px;
    width: 48px;
  }

  .emoticon-name {
    font-size: 10px;
    margin-top: 2px;
  }
}
</style>

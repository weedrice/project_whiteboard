<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'
import { formatInteger } from '@/utils/numberFormat'
import type { EmoticonMaster } from '@/types/emoticon'

defineProps<{
  emoticon: Pick<EmoticonMaster, 'emoticonId' | 'name' | 'thumbnailUrl' | 'creatorName' | 'purchaseCount'>
  rank?: number
}>()

const emit = defineEmits<{
  (e: 'select', emoticonId: number): void
}>()

const { t } = useI18n()
</script>

<template>
  <button
    type="button"
    :aria-label="emoticon.name"
    class="relative w-full nv-surface rounded-lg shadow-sm border nv-border overflow-hidden cursor-pointer text-left hover:shadow-md nv-focus-ring transition-shadow"
    @click="emit('select', emoticon.emoticonId)"
  >
    <span
      v-if="rank != null"
      class="absolute top-2 left-2 z-10 w-6 h-6 bg-[var(--nv-accent)] text-white text-xs font-bold rounded-full flex items-center justify-center"
    >
      {{ rank }}
    </span>
    <span class="block aspect-square nv-surface-muted">
      <img
        :src="emoticon.thumbnailUrl || DEFAULT_EMOTICON_IMAGE_URL"
        :alt="emoticon.name"
        loading="lazy"
        decoding="async"
        class="w-full h-full object-contain"
        @error="applyImageFallback"
      />
    </span>
    <span class="block p-3">
      <span class="block text-sm font-medium nv-title truncate">{{ emoticon.name }}</span>
      <span class="block text-xs nv-text-subtle truncate">{{ emoticon.creatorName }}</span>
      <span class="block text-xs nv-accent-text mt-1">
        {{ t('emoticon.list.salesCount', { count: formatInteger(emoticon.purchaseCount) }) }}
      </span>
    </span>
  </button>
</template>

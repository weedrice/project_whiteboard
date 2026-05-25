<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAdBannerPlacement } from '@/composables/useAdBannerPlacement'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  placement?: string
}>(), {
  placement: 'SIDEBAR'
})

const { ad, handleAdClick } = useAdBannerPlacement({
  placement: props.placement,
  fallbackTitle: t('common.advertisement'),
})
</script>

<template>
  <div v-if="ad" class="my-4 text-center">
    <div class="cursor-pointer overflow-hidden rounded-lg shadow-md hover:shadow-lg transition-shadow duration-300"
      :class="placement === 'SIDEBAR' ? 'inline-block' : 'block w-full'" @click="handleAdClick">
      <img v-if="ad.imageUrl" :src="ad.imageUrl" :alt="ad.title" class="max-w-full h-auto object-cover"
        :class="placement === 'SIDEBAR' ? 'w-40 h-[600px]' : 'w-full h-auto'" />
      <div v-else class="bg-gray-100 p-4 flex items-center justify-center text-gray-400 text-sm"
        :class="placement === 'SIDEBAR' ? 'w-40 h-[600px]' : 'w-full h-[120px]'">
        <span>{{ ad.title || $t('common.advertisement') }}</span>
      </div>
    </div>
  </div>
</template>

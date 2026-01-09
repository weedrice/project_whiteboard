<template>
  <div class="text-center py-12">
    <div class="mx-auto h-12 w-12 text-red-400 dark:text-red-500 mb-4">
      <component :is="icon" class="h-full w-full" />
    </div>
    <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
      {{ title || $t('common.error.title') }}
    </h3>
    <p class="text-sm text-gray-500 dark:text-gray-400 mb-6">
      {{ message || $t('common.error.message') }}
    </p>
    <div v-if="showRetry || $slots.action" class="flex justify-center gap-3">
      <BaseButton 
        v-if="showRetry" 
        @click="$emit('retry')" 
        variant="primary"
      >
        {{ $t('common.retry') }}
      </BaseButton>
      <BaseButton 
        v-if="showGoHome" 
        @click="$emit('goHome')" 
        variant="secondary"
      >
        {{ $t('common.goHome') }}
      </BaseButton>
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { AlertCircle } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { Component } from 'vue'

withDefaults(defineProps<{
  title?: string
  message?: string
  icon?: Component
  showRetry?: boolean
  showGoHome?: boolean
}>(), {
  icon: AlertCircle,
  showRetry: false,
  showGoHome: false
})

defineEmits<{
  retry: []
  goHome: []
}>()
</script>

<template>
  <div class="text-center py-12">
    <div class="mx-auto h-12 w-12 text-[var(--nv-danger-text)] mb-4">
      <component :is="icon" class="h-full w-full" aria-hidden="true" />
    </div>
    <h3 class="text-lg font-medium nv-title mb-2">
      {{ title || $t('common.messages.defaultTitle') }}
    </h3>
    <p class="text-sm nv-text-subtle mb-6">
      {{ message || $t('common.error.defaultMessage') }}
    </p>
    <div v-if="showRetry || $slots.action" class="flex justify-center gap-3">
      <BaseButton 
        v-if="showRetry" 
        @click="$emit('retry')" 
        variant="primary"
      >
        {{ $t('common.error.retry') }}
      </BaseButton>
      <BaseButton 
        v-if="showGoHome" 
        @click="$emit('goHome')" 
        variant="secondary"
      >
        {{ $t('common.error.goHome') }}
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
  icon: () => AlertCircle,
  showRetry: false,
  showGoHome: false
})

defineEmits<{
  retry: []
  goHome: []
}>()
</script>

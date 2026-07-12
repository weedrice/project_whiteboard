<template>
  <section class="mx-auto w-full max-w-lg text-center py-12" role="alert" aria-live="polite">
    <p v-if="code" class="nv-kicker mb-3">{{ code }}</p>
    <div v-if="showIcon" class="mx-auto h-12 w-12 text-[var(--nv-danger-text)] mb-4">
      <component :is="icon" class="h-full w-full" aria-hidden="true" />
    </div>
    <h3 class="text-lg font-medium nv-title mb-2">
      {{ title || $t('common.messages.defaultTitle') }}
    </h3>
    <p class="text-sm nv-text-subtle mb-6">
      {{ message || $t('common.error.defaultMessage') }}
    </p>
    <p v-if="notice" class="mb-4 text-xs nv-text-subtle">{{ notice }}</p>
    <div v-if="showRetry || showGoHome || $slots.action || $slots.default" class="flex flex-col justify-center gap-3 sm:flex-row">
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
      <slot></slot>
    </div>
    <slot name="details"></slot>
  </section>
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
  showIcon?: boolean
  code?: string
  notice?: string
}>(), {
  icon: () => AlertCircle,
  showRetry: false,
  showGoHome: false,
  showIcon: true,
})

defineEmits<{
  retry: []
  goHome: []
}>()
</script>

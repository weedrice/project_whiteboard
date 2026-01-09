<template>
  <div class="text-center py-12" :class="containerClass">
    <div v-if="icon" class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-500 mb-4">
      <component :is="icon" class="h-full w-full" />
    </div>
    <h3 class="mt-2 text-sm font-medium text-gray-900 dark:text-gray-100">
      {{ title || $t('common.noData') }}
    </h3>
    <p v-if="description" class="mt-1 text-sm text-gray-500 dark:text-gray-400">
      {{ description }}
    </p>
    <div v-if="$slots.action" class="mt-6">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title?: string
  description?: string
  icon?: Component
  containerClass?: string
}>()
</script>

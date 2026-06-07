<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(defineProps<{
  icon: Component
  label: string
  iconClass?: string
  contentClass?: string
}>(), {
  iconClass: 'h-4 w-4 mr-2',
  contentClass: 'mt-1 text-sm nv-text sm:mt-0',
})
</script>

<template>
  <div class="py-4 sm:py-5 sm:grid sm:grid-cols-[150px_minmax(0,1fr)] sm:items-center sm:gap-2 sm:px-6">
    <dt class="text-sm font-medium nv-text-subtle flex items-center whitespace-nowrap">
      <component :is="icon" :class="iconClass" />
      {{ label }}
    </dt>
    <dd :class="contentClass">
      <slot />
    </dd>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  size?: 'compact' | 'default' | 'hero'
}>(), {
  description: '',
  size: 'default',
})

const titleClass = computed(() => {
  switch (props.size) {
    case 'compact':
      return 'text-lg font-semibold leading-6'
    case 'hero':
      return 'text-2xl font-bold leading-9 sm:text-3xl'
    default:
      return 'text-2xl font-semibold tracking-[-0.04em]'
  }
})
</script>

<template>
  <header class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
    <div class="min-w-0 flex-1">
      <div class="flex min-w-0 items-center gap-2">
        <slot name="icon" />
        <h1 class="min-w-0 nv-title" :class="titleClass">{{ title }}</h1>
      </div>
      <p v-if="description" class="mt-1 max-w-2xl text-sm nv-text-subtle">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="shrink-0">
      <slot name="actions" />
    </div>
  </header>
</template>

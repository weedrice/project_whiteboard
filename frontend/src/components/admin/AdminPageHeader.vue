<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div class="sm:flex sm:items-center sm:justify-between">
    <div class="sm:flex-auto">
      <h1 class="text-xl font-semibold nv-title">{{ title }}</h1>
      <p class="mt-2 text-sm nv-text-muted">{{ description }}</p>
    </div>
    <slot name="actions" />
  </div>
</template>

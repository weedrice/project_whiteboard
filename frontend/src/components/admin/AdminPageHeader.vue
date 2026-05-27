<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div class="sm:flex sm:items-center sm:justify-between">
    <div class="sm:flex-auto">
      <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ title }}</h1>
      <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ description }}</p>
    </div>
    <slot name="actions" />
  </div>
</template>

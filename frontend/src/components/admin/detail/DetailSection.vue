<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <section>
    <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
      {{ title }}
    </h3>
    <slot />
  </section>
</template>

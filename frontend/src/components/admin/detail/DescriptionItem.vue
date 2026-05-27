<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  valueClass?: string
}>(), {
  valueClass: 'mt-1 text-sm text-gray-900 dark:text-white',
})
</script>

<template>
  <div>
    <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ label }}</dt>
    <dd :class="valueClass">
      <slot />
    </dd>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  columnsClass?: string
  gapClass?: string
}>(), {
  columnsClass: 'grid-cols-1 sm:grid-cols-2',
  gapClass: 'gap-4',
})
</script>

<template>
  <dl :class="['grid', columnsClass, gapClass]">
    <slot />
  </dl>
</template>

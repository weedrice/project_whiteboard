<script setup lang="ts">
withDefaults(defineProps<{
  gapClass?: string
}>(), {
  gapClass: 'gap-3',
})
</script>

<template>
  <form :class="['admin-inline-form flex flex-col sm:flex-row sm:items-end', gapClass]">
    <slot />
  </form>
</template>

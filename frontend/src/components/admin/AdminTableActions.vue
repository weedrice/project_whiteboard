<script setup lang="ts">
withDefaults(defineProps<{
  alignClass?: string
  gapClass?: string
  className?: string
}>(), {
  alignClass: 'justify-end',
  gapClass: 'gap-2',
  className: '',
})
</script>

<template>
  <div :class="['flex', alignClass, gapClass, className]">
    <slot />
  </div>
</template>

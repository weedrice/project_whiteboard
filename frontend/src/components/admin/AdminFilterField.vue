<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  forId?: string
  widthClass?: string
}>(), {
  forId: undefined,
  widthClass: 'w-36',
})
</script>

<template>
  <div :class="['filter-item flex flex-col gap-1', widthClass]">
    <label :for="forId" class="filter-label w-full text-left text-xs nv-text-subtle">{{ label }}</label>
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  full?: boolean
  labelClass?: string
  valueClass?: string
}>(), {
  full: false,
  labelClass: 'text-sm font-medium nv-text-subtle',
  valueClass: 'mt-1 text-sm nv-text',
})
</script>

<template>
  <div :class="{ 'sm:col-span-2': full }">
    <dt :class="labelClass">{{ label }}</dt>
    <dd :class="valueClass">
      <slot />
    </dd>
  </div>
</template>

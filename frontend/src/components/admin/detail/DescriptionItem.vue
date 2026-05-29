<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  valueClass?: string
}>(), {
  valueClass: 'mt-1 text-sm nv-text',
})
</script>

<template>
  <div>
    <dt class="text-sm font-medium nv-text-subtle">{{ label }}</dt>
    <dd :class="valueClass">
      <slot />
    </dd>
  </div>
</template>

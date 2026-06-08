<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  compact?: boolean
}>(), {
  compact: false,
})
</script>

<template>
  <section>
    <div
      :class="[
        'mb-4 flex items-center justify-between gap-3',
        compact ? 'border-b nv-border pb-1.5' : '',
      ]"
    >
      <h3 :class="compact ? 'text-sm font-semibold nv-title' : 'text-lg font-medium nv-title'">
        {{ title }}
      </h3>
      <slot name="actions" />
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type AdminFilterFieldWidth = 'default' | 'compact' | 'date' | 'select' | 'search' | 'fluid' | 'wide'

const widthClassBySize: Record<AdminFilterFieldWidth, string> = {
  default: 'w-36',
  compact: 'w-36',
  date: 'w-44',
  select: 'w-44',
  search: 'w-52',
  fluid: 'w-full sm:max-w-xs',
  wide: 'w-full sm:max-w-md',
}

const props = withDefaults(defineProps<{
  label: string
  forId?: string
  width?: AdminFilterFieldWidth
  widthClass?: string
}>(), {
  forId: undefined,
  width: 'default',
  widthClass: undefined,
})

const fieldWidthClass = computed(() => props.widthClass ?? widthClassBySize[props.width])
</script>

<template>
  <div :class="['filter-item flex flex-col gap-1', fieldWidthClass]">
    <label :for="forId" class="filter-label w-full text-left text-xs nv-text-subtle">{{ label }}</label>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type CardTag = 'article' | 'aside' | 'div' | 'section'
type CardPadding = 'none' | 'sm' | 'md' | 'lg'
type CardRadius = 'none' | 'md' | 'lg' | 'xl'
type CardElevation = 'none' | 'sm' | 'md'

const props = withDefaults(defineProps<{
  as?: CardTag
  noPadding?: boolean
  padding?: CardPadding
  radius?: CardRadius
  elevation?: CardElevation
  bordered?: boolean
}>(), {
  as: 'div',
  noPadding: false,
  padding: 'md',
  radius: 'lg',
  elevation: 'sm',
  bordered: false,
})

const shellClass = computed(() => ({
  'border nv-border': props.bordered,
  'rounded-none': props.radius === 'none',
  'rounded-md': props.radius === 'md',
  'rounded-lg': props.radius === 'lg',
  'rounded-xl': props.radius === 'xl',
  'shadow-none': props.elevation === 'none',
  'shadow-sm': props.elevation === 'sm',
  'shadow-md': props.elevation === 'md',
}))

const contentClass = computed(() => {
  if (props.noPadding || props.padding === 'none') return ''
  if (props.padding === 'sm') return 'p-4'
  if (props.padding === 'lg') return 'p-5 sm:p-6'
  return 'px-4 py-5 sm:p-6'
})
</script>

<template>
  <component
    :is="as"
    class="overflow-hidden nv-surface transition-colors duration-200"
    :class="shellClass"
  >
    <div :class="contentClass">
      <slot />
    </div>
  </component>
</template>

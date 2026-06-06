<script setup lang="ts">
import BaseBadge from '@/components/common/ui/BaseBadge.vue'

withDefaults(defineProps<{
  value: boolean
  trueLabel?: string
  falseLabel?: string
  trueVariant?: 'success' | 'danger' | 'warning' | 'info' | 'gray'
  falseVariant?: 'success' | 'danger' | 'warning' | 'info' | 'gray'
}>(), {
  trueLabel: 'Y',
  falseLabel: 'N',
  trueVariant: 'success',
  falseVariant: 'gray',
})
</script>

<template>
  <BaseBadge :variant="value ? trueVariant : falseVariant" size="sm">
    {{ value ? trueLabel : falseLabel }}
  </BaseBadge>
</template>

<script setup lang="ts">
import BaseBadge from '@/components/common/ui/BaseBadge.vue'

withDefaults(defineProps<{
  label: string
  variant?: 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'info' | 'gray'
  statusClass?: string
}>(), {
  variant: 'gray',
  statusClass: '',
})
</script>

<template>
  <BaseBadge class="admin-status-badge" :class="statusClass" :variant="variant" size="sm">
    {{ label }}
  </BaseBadge>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'

const props = defineProps<{
  status: number
}>()

const statusClass = computed(() => {
  if (props.status >= 500) return 'status-500'
  if (props.status >= 400) return 'status-400'
  return 'status-other'
})

const variant = computed(() => {
  if (props.status >= 500) return 'danger'
  if (props.status >= 400) return 'warning'
  return 'success'
})
</script>

<template>
  <BaseBadge class="http-status-badge" :class="statusClass" :variant="variant" size="sm">
    {{ status }}
  </BaseBadge>
</template>

<script setup lang="ts">
import BaseBadge from '@/components/common/ui/BaseBadge.vue'

defineProps<{
  resolved: boolean
  resolvedLabel: string
  unresolvedLabel: string
}>()
</script>

<template>
  <BaseBadge
    class="resolve-badge"
    :class="resolved ? 'resolve-badge--resolved' : 'resolve-badge--unresolved'"
    :variant="resolved ? 'success' : 'danger'"
    size="sm"
  >
    {{ resolved ? resolvedLabel : unresolvedLabel }}
  </BaseBadge>
</template>

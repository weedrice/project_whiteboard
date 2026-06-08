<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  cancelLabel: string
  applyLabel: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'apply'): void
}>()
</script>

<template>
  <div class="link-popover-actions">
    <BaseButton type="button" variant="secondary" size="sm" @click="emit('close')">
      {{ cancelLabel }}
    </BaseButton>
    <slot name="secondary-action" />
    <BaseButton type="button" variant="primary" size="sm" @click="emit('apply')">
      {{ applyLabel }}
    </BaseButton>
  </div>
</template>

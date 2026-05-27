<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  page: number
  totalPages: number
  previousLabel: string
  nextLabel: string
}>()

defineEmits<{
  previous: []
  next: []
}>()
</script>

<template>
  <div v-if="totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
    <BaseButton variant="secondary" size="sm" :disabled="page <= 0" @click="$emit('previous')">
      {{ previousLabel }}
    </BaseButton>
    <BaseButton variant="secondary" size="sm" :disabled="page + 1 >= totalPages" @click="$emit('next')">
      {{ nextLabel }}
    </BaseButton>
  </div>
</template>

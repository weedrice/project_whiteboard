<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const props = withDefaults(defineProps<{
  page: number
  totalPages: number
  previousLabel?: string
  nextLabel?: string
}>(), {})
const { t } = useI18n()
const effectivePreviousLabel = computed(() => props.previousLabel ?? t('common.previous'))
const effectiveNextLabel = computed(() => props.nextLabel ?? t('common.next'))

defineEmits<{
  previous: []
  next: []
}>()
</script>

<template>
  <div v-if="totalPages > 0" class="mt-2 flex items-center justify-end gap-2">
    <BaseButton variant="secondary" size="sm" :disabled="page <= 0" @click="$emit('previous')">
      {{ effectivePreviousLabel }}
    </BaseButton>
    <BaseButton variant="secondary" size="sm" :disabled="page + 1 >= totalPages" @click="$emit('next')">
      {{ effectiveNextLabel }}
    </BaseButton>
  </div>
</template>

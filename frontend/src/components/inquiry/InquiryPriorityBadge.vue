<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseBadge, { type BaseBadgeVariant } from '@/components/common/ui/BaseBadge.vue'
import type { InquiryPriority } from '@/types/inquiry'

const props = defineProps<{
  priority: InquiryPriority
}>()

const { t } = useI18n()
const variantByPriority = {
  URGENT: 'danger',
  HIGH: 'warning',
  NORMAL: 'gray',
} satisfies Record<InquiryPriority, BaseBadgeVariant>
const variant = computed(() => variantByPriority[props.priority])
const labels = computed<Record<InquiryPriority, string>>(() => ({
  URGENT: t('inquiry.priority.URGENT'),
  HIGH: t('inquiry.priority.HIGH'),
  NORMAL: t('inquiry.priority.NORMAL'),
}))
</script>

<template>
  <BaseBadge :variant="variant" size="sm">
    {{ labels[priority] }}
  </BaseBadge>
</template>

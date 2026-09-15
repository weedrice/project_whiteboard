<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseBadge, { type BaseBadgeVariant } from '@/components/common/ui/BaseBadge.vue'
import type { InquiryStatus } from '@/types/inquiry'

const props = defineProps<{
  status: InquiryStatus
}>()

const { t } = useI18n()
const variantByStatus = {
  NEW: 'info',
  IN_PROGRESS: 'warning',
  RESOLVED: 'success',
  CLOSED: 'gray',
} satisfies Record<InquiryStatus, BaseBadgeVariant>
const variant = computed(() => variantByStatus[props.status])
const labels = computed<Record<InquiryStatus, string>>(() => ({
  NEW: t('inquiry.status.NEW'),
  IN_PROGRESS: t('inquiry.status.IN_PROGRESS'),
  RESOLVED: t('inquiry.status.RESOLVED'),
  CLOSED: t('inquiry.status.CLOSED'),
}))
</script>

<template>
  <BaseBadge :variant="variant" size="sm" :data-inquiry-status="status">
    {{ labels[status] }}
  </BaseBadge>
</template>

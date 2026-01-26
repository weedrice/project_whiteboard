<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import { formatDate } from '@/utils/date'
import type { Report } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  isOpen: boolean
  report: Report | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

const statusVariant = computed(() => {
  if (!props.report) return 'gray'
  switch (props.report.status) {
    case 'PENDING':
      return 'warning'
    case 'RESOLVED':
      return 'success'
    case 'REJECTED':
      return 'gray'
    default:
      return 'gray'
  }
})

const targetTypeLabel = computed(() => {
  if (!props.report) return ''
  switch (props.report.targetType) {
    case 'POST':
      return t('common.post')
    case 'COMMENT':
      return t('common.comment')
    case 'USER':
      return t('common.user')
    default:
      return props.report.targetType
  }
})
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.reports.detail.title')" @close="$emit('close')">
    <div v-if="report" class="space-y-6">
      <!-- 신고 정보 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.reports.detail.reportInfo') }}
        </h3>
        <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.id') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ report.reportId }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.reports.table.reporter') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ report.reporterDisplayName }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.target') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">
              {{ targetTypeLabel }} #{{ report.targetId }}
            </dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.status') }}</dt>
            <dd class="mt-1">
              <BaseBadge :variant="statusVariant" size="sm">
                {{ t(`admin.reports.status.${report.status}`) }}
              </BaseBadge>
            </dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.createdAt') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ formatDate(report.createdAt) }}</dd>
          </div>
        </dl>
      </div>

      <!-- 신고 사유 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('common.reason') }}
        </h3>
        <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
          <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ report.reason }}</p>
        </div>
      </div>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import DetailSection from '@/components/admin/detail/DetailSection.vue'
import DescriptionGrid from '@/components/admin/detail/DescriptionGrid.vue'
import DescriptionItem from '@/components/admin/detail/DescriptionItem.vue'
import { formatDate } from '@/utils/date'
import {
  getAdminReportStatusLabel,
  getCommonReportTargetTypeLabel,
  getReportStatusVariant
} from '@/utils/reportDisplay'
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
  return getReportStatusVariant(props.report.status)
})

const targetTypeLabel = computed(() => {
  if (!props.report) return ''
  return getCommonReportTargetTypeLabel(t, props.report.targetType)
})

function getProcessorText(report: Report) {
  if (report.adminId != null) {
    return `ADMIN #${report.adminId}`
  }
  if (report.processorUserId != null) {
    return `USER #${report.processorUserId}`
  }
  return '-'
}

function getReportReasonText(report: Report) {
  return report.contents?.trim() || report.remark?.trim() || '-'
}
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.reports.detail.title')" @close="$emit('close')">
    <div v-if="report" class="space-y-6">
      <DetailSection :title="t('admin.reports.detail.reportInfo')">
        <DescriptionGrid>
          <DescriptionItem :label="t('common.id')">
            {{ report.reportId }}
          </DescriptionItem>
          <DescriptionItem :label="t('admin.reports.table.reporter')">
            {{ report.reporterDisplayName }}
          </DescriptionItem>
          <DescriptionItem :label="t('common.target')">
              <template v-if="report.targetDisplayName != null && report.targetLoginId != null">
                <div class="flex flex-col">
                  <span>{{ report.targetDisplayName }}</span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ report.targetLoginId }}</span>
                </div>
              </template>
              <template v-else>
                {{ targetTypeLabel }} #{{ report.targetId }}
              </template>
          </DescriptionItem>
          <DescriptionItem :label="t('common.status')" value-class="mt-1">
            <BaseBadge :variant="statusVariant" size="sm">
              {{ getAdminReportStatusLabel(t, report.status) }}
            </BaseBadge>
          </DescriptionItem>
          <DescriptionItem :label="t('common.createdAt')">
            {{ formatDate(report.createdAt) }}
          </DescriptionItem>
          <DescriptionItem label="처리자">
            {{ getProcessorText(report) }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>

      <DetailSection :title="t('admin.reports.reasonType')">
        <DescriptionGrid>
          <DescriptionItem
            v-if="report.reasonType"
            :label="t('admin.reports.reasonType')"
            value-class="mt-0.5 text-sm text-gray-900 dark:text-white"
          >
            {{ report.reasonType }}
          </DescriptionItem>
          <DescriptionItem
            v-if="report.targetType === 'POST' || report.targetType === 'COMMENT'"
            :label="t('admin.reports.targetContentId')"
            value-class="mt-0.5 text-sm text-gray-900 dark:text-white"
          >
            {{ report.targetId }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>

      <div class="border-t border-gray-200 dark:border-gray-600 pt-4">
        <h3 class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">{{ t('admin.reports.remark') }}</h3>
        <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ getReportReasonText(report) }}</p>
      </div>
    </div>
  </BaseModal>
</template>

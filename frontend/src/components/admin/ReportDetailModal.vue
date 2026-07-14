<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminDetailModalShell from '@/components/admin/AdminDetailModalShell.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import DetailSection from '@/components/admin/detail/DetailSection.vue'
import DescriptionGrid from '@/components/admin/detail/DescriptionGrid.vue'
import DescriptionItem from '@/components/admin/detail/DescriptionItem.vue'
import { formatDate } from '@/utils/date'
import {
  getAdminReportStatusLabel,
  getCommonReportTargetTypeLabel,
  getReportProcessorText,
  getReportReasonText,
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

</script>

<template>
  <AdminDetailModalShell
    :is-open="isOpen"
    :title="t('admin.reports.detail.title')"
    :empty="!report"
    @close="$emit('close')"
  >
    <template v-if="report">
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
                <span class="text-xs nv-text-subtle">{{ report.targetLoginId }}</span>
              </div>
            </template>
            <template v-else>
              {{ targetTypeLabel }} #{{ report.targetId }}
            </template>
          </DescriptionItem>
          <DescriptionItem :label="t('common.status')" value-class="mt-1">
            <AdminStatusBadge :label="getAdminReportStatusLabel(t, report.status)" :variant="statusVariant" />
          </DescriptionItem>
          <DescriptionItem :label="t('common.createdAt')">
            {{ formatDate(report.createdAt) }}
          </DescriptionItem>
          <DescriptionItem :label="t('admin.reports.table.processor')">
            {{ getReportProcessorText(t, report) }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>

      <DetailSection :title="t('admin.reports.reasonType')">
        <DescriptionGrid>
          <DescriptionItem
            v-if="report.reasonType"
            :label="t('admin.reports.reasonType')"
            value-class="mt-0.5 text-sm nv-text"
          >
            {{ report.reasonType }}
          </DescriptionItem>
          <DescriptionItem
            v-if="report.targetType === 'POST' || report.targetType === 'COMMENT'"
            :label="t('admin.reports.targetContentId')"
            value-class="mt-0.5 text-sm nv-text"
          >
            {{ report.targetId }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>

      <div class="border-t nv-border pt-4">
        <h3 class="text-sm font-medium nv-text-subtle mb-2">{{ t('admin.reports.remark') }}</h3>
        <p class="text-sm nv-text-muted whitespace-pre-wrap">{{ getReportReasonText(report) }}</p>
      </div>
    </template>
  </AdminDetailModalShell>
</template>

<script setup lang="ts">
import { Check, X, ShieldAlert, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import { computed } from 'vue'
import { formatDate } from '@/utils/date'
import {
  getAdminReportStatusLabel,
  getCommonReportTargetTypeLabel,
  getReportProcessorText,
  getReportReasonText,
  getReportStatusVariant,
  getReportTargetDisplayText
} from '@/utils/reportDisplay'
import type { Report } from '@/types'

const { t } = useI18n()

defineProps<{
  reports: Report[]
}>()

const emit = defineEmits<{
  (e: 'resolve', report: Report): void
  (e: 'reject', report: Report): void
  (e: 'sanction', report: Report): void
  (e: 'viewDetail', report: Report): void
}>()

function onResolve(report: Report) {
  emit('resolve', report)
}

function onReject(report: Report) {
  emit('reject', report)
}

function onSanction(report: Report) {
  emit('sanction', report)
}

const columns = computed(() => [
  { key: 'reportId', label: t('common.id'), width: '5%', align: 'center' as const },
  { key: 'reporterDisplayName', label: t('admin.reports.table.reporter'), width: '8%', align: 'center' as const },
  { key: 'targetType', label: t('admin.reports.targetType'), width: '5%', align: 'center' as const },
  { key: 'target', label: t('common.target'), width: '10%', align: 'center' as const },
  { key: 'contents', label: t('admin.reports.targetContentId'), width: '7%', align: 'center' as const },
  { key: 'reasonType', label: t('admin.reports.reasonType'), width: '7%', align: 'center' as const },
  { key: 'processor', label: '처리자', width: '8%', align: 'center' as const },
  { key: 'remark', label: t('admin.reports.remark'), width: '20%', align: 'left' as const },
  { key: 'status', label: t('common.status'), width: '8%', align: 'center' as const },
  { key: 'createdAt', label: t('common.createdAt'), width: '10%', align: 'center' as const },
  { key: 'actions', label: '', align: 'center' as const, width: '10%' }
])
</script>

<template>
  <div class="mt-8">
    <BaseTable :columns="columns" :items="reports" row-key="reportId" :emptyText="t('common.noData')">
      <template #cell-targetType="{ item }">
        {{ getCommonReportTargetTypeLabel(t, item.targetType) }}
      </template>

      <template #cell-target="{ item }">
        <span
          class="inline-flex flex-col max-w-full"
          :title="getReportTargetDisplayText(t, item)"
        >
          <template v-if="item.targetDisplayName != null && item.targetLoginId != null">
            <span class="text-xs font-medium">{{ item.targetDisplayName }}</span>
            <span class="text-[11px] nv-text-subtle">{{ item.targetLoginId }}</span>
          </template>
          <template v-else>
            <span class="text-xs">{{ getReportTargetDisplayText(t, item) }}</span>
          </template>
        </span>
      </template>

      <template #cell-reasonType="{ item }">
        {{ item.reasonType || '-' }}
      </template>

      <template #cell-contents="{ item }">
        <span v-if="item.targetType === 'POST' || item.targetType === 'COMMENT'">{{ item.targetId }}</span>
        <span v-else class="nv-text-subtle">-</span>
      </template>

      <template #cell-processor="{ item }">
        {{ getReportProcessorText(item) }}
      </template>

      <template #cell-remark="{ item }">
        <span class="inline-block max-w-full truncate" :title="getReportReasonText(item)">
          {{ getReportReasonText(item) }}
        </span>
      </template>

      <template #cell-status="{ item }">
        <AdminStatusBadge
          :label="getAdminReportStatusLabel(t, item.status)"
          :variant="getReportStatusVariant(item.status)"
        />
      </template>

      <template #cell-createdAt="{ item }">
        {{ formatDate(item.createdAt) }}
      </template>

      <template #cell-actions="{ item }">
        <div class="flex justify-center space-x-2">
          <AdminActionButton :label="t('common.viewDetail')" tone="accent" icon-only @click="$emit('viewDetail', item)">
            <Eye class="h-4 w-4" />
          </AdminActionButton>
          <template v-if="item.status === 'PENDING'">
            <AdminActionButton :label="t('admin.reports.actions.sanction')" tone="neutral" icon-only @click="onSanction(item)">
              <ShieldAlert class="h-4 w-4" />
            </AdminActionButton>
            <AdminActionButton :label="t('admin.reports.actions.resolve')" tone="success" icon-only @click="onResolve(item)">
              <Check class="h-4 w-4" />
            </AdminActionButton>
            <AdminActionButton :label="t('admin.reports.actions.reject')" tone="danger" icon-only @click="onReject(item)">
              <X class="h-4 w-4" />
            </AdminActionButton>
          </template>
        </div>
      </template>
    </BaseTable>
  </div>
</template>

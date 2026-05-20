<script setup lang="ts">
import { Check, X, ShieldAlert, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import { computed } from 'vue'
import { formatDate } from '@/utils/date'
import { getAdminReportStatusLabel, getReportStatusVariant } from '@/utils/reportDisplay'
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
        {{ item.targetType }}
      </template>

      <template #cell-target="{ item }">
        <span
          class="inline-flex flex-col max-w-full"
          :title="item.targetDisplayName != null && item.targetLoginId != null ? `${item.targetDisplayName}\n${item.targetLoginId}` : `${item.targetType} #${item.targetId}`"
        >
          <template v-if="item.targetDisplayName != null && item.targetLoginId != null">
            <span class="text-xs font-medium">{{ item.targetDisplayName }}</span>
            <span class="text-[11px] text-gray-500 dark:text-gray-400">{{ item.targetLoginId }}</span>
          </template>
          <template v-else>
            <span class="text-xs">{{ item.targetType }} #{{ item.targetId }}</span>
          </template>
        </span>
      </template>

      <template #cell-reasonType="{ item }">
        {{ item.reasonType || '-' }}
      </template>

      <template #cell-contents="{ item }">
        <span v-if="item.targetType === 'POST' || item.targetType === 'COMMENT'">{{ item.targetId }}</span>
        <span v-else class="text-gray-400">-</span>
      </template>

      <template #cell-processor="{ item }">
        {{ getProcessorText(item) }}
      </template>

      <template #cell-remark="{ item }">
        <span class="inline-block max-w-full truncate" :title="getReportReasonText(item)">
          {{ getReportReasonText(item) }}
        </span>
      </template>

      <template #cell-status="{ item }">
        <BaseBadge :variant="getReportStatusVariant(item.status)" size="sm">
          {{ getAdminReportStatusLabel(t, item.status) }}
        </BaseBadge>
      </template>

      <template #cell-createdAt="{ item }">
        {{ formatDate(item.createdAt) }}
      </template>

      <template #cell-actions="{ item }">
        <div class="flex justify-center space-x-2">
          <BaseButton @click="$emit('viewDetail', item)" variant="ghost" size="sm"
            :title="t('common.viewDetail')"
            class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300">
            <Eye class="h-4 w-4" />
          </BaseButton>
          <template v-if="item.status === 'PENDING'">
            <BaseButton @click="onSanction(item)" variant="ghost" size="sm" :title="t('admin.reports.actions.sanction')"
              class="p-1 text-orange-600 hover:text-orange-900 dark:text-orange-400 dark:hover:text-orange-300">
              <ShieldAlert class="h-4 w-4" />
            </BaseButton>
            <BaseButton @click="onResolve(item)" variant="ghost" size="sm" :title="t('admin.reports.actions.resolve')"
              class="p-1 text-green-600 hover:text-green-900 dark:text-green-400 dark:hover:text-green-300">
              <Check class="h-4 w-4" />
            </BaseButton>
            <BaseButton @click="onReject(item)" variant="ghost" size="sm" :title="t('admin.reports.actions.reject')"
              class="p-1 text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300">
              <X class="h-4 w-4" />
            </BaseButton>
          </template>
        </div>
      </template>
    </BaseTable>
  </div>
</template>

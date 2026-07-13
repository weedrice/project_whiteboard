<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useReport } from '@/composables/useReport'
import { formatDate } from '@/utils/date'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { Flag } from 'lucide-vue-next'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import type { MyReport } from '@/types'
import {
  getMyReportStatusClass,
  getMyReportStatusLabel,
  getMyReportTargetTypeLabel
} from '@/utils/reportDisplay'

const { t } = useI18n()
const { useMyReports } = useReport()
const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: reports,
  totalPages,
  isLoading: loading,
  errorMessage,
  refetch,
} = usePaginatedListState<MyReport>(useMyReports, { initialSize: 15, t })
</script>

<template>
  <PaginatedListCard
    title-tag="h1"
    :title="$t('user.tabs.reports')"
    :icon="Flag"
    :items-count="reports.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.reportList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    loading-preset="status-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <ul role="list" class="divide-y divide-[var(--nv-line)]">
      <li v-for="report in reports" :key="report.reportId"
        class="px-4 py-4 sm:px-6 nv-hover-surface transition-colors duration-200 min-h-[44px] flex flex-row items-center justify-between gap-3">
        <div class="flex flex-col min-w-0 flex-1">
          <p class="text-sm font-medium nv-accent-text line-clamp-2 sm:truncate">
            {{ getMyReportTargetTypeLabel($t, report.targetType) }} {{ $t('user.reportList.targetType') }} - {{
              report.reasonType || report.contents || '-' }}
          </p>
          <p class="mt-0.5 sm:mt-1 text-xs nv-text-subtle">
            {{ formatDate(report.createdAt) }}
          </p>
        </div>
        <div class="flex items-center flex-shrink-0 self-center">
          <span
            class="px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full whitespace-nowrap"
            :class="getMyReportStatusClass(report.status)"
          >
            {{ getMyReportStatusLabel($t, report.status) }}
          </span>
        </div>
      </li>
    </ul>
  </PaginatedListCard>
</template>

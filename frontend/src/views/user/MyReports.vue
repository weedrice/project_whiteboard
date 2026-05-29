<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useReport } from '@/composables/useReport'
import { formatDate } from '@/utils/date'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { Flag } from 'lucide-vue-next'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import {
  getMyReportStatusClass,
  getMyReportStatusLabel,
  getMyReportTargetTypeLabel
} from '@/utils/reportDisplay'

const { t } = useI18n()
const { useMyReports } = useReport()
const { page, size, params, handlePageChange, handleSizeChange } = usePaginatedQueryState({ initialSize: 15 })
const { data: reportsData, isLoading: loading, error, refetch } = useMyReports(params)
const { items: reports, totalPages } = usePageResponseState(reportsData, page)
const errorMessage = computed(() => error.value ? getListLoadErrorMessage(t) : '')
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.reports')"
    :icon="Flag"
    :items-count="reports.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.reportList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div class="flex flex-col flex-1">
            <BaseSkeleton width="60%" height="20px" className="mb-1" />
            <BaseSkeleton width="30%" height="14px" />
          </div>
          <BaseSkeleton width="60px" height="24px" rounded="rounded-full" />
        </div>
      </div>
    </template>

    <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
      <li v-for="report in reports" :key="report.reportId"
        class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200 min-h-[44px] flex flex-row items-center justify-between gap-3">
        <div class="flex flex-col min-w-0 flex-1">
          <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 line-clamp-2 sm:truncate">
            {{ getMyReportTargetTypeLabel($t, report.targetType) }} {{ $t('user.reportList.targetType') }} - {{
              report.reasonType || report.contents || '-' }}
          </p>
          <p class="mt-0.5 sm:mt-1 text-xs text-gray-500 dark:text-gray-400">
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

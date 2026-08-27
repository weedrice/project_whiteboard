<script setup lang="ts">
import { computed, onScopeDispose, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminAuditLogTable from '@/components/admin/AdminAuditLogTable.vue'
import AdminFilterActions from '@/components/admin/AdminFilterActions.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import ReportDetailModal from '@/components/admin/ReportDetailModal.vue'
import ReportList from '@/components/admin/ReportList.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import { useBoardManagerGovernance } from '@/features/board/edit/useBoardManagerGovernance'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import type { Report } from '@/types'
import type { ReportStatusFilter, ReportTargetTypeFilter } from '@/api/adminTypes'
import { COMMON_CODE_TYPES, useSupportedCommonCodeValues } from '@/composables/useCommonCodeDetails'

const props = withDefaults(defineProps<{
  boardUrl: string
  enabled?: boolean
}>(), {
  enabled: true,
})

const { t } = useI18n()
const governance = useBoardManagerGovernance(toRef(props, 'boardUrl'), toRef(props, 'enabled'))
const SUPPORTED_REPORT_STATUSES: ReportStatusFilter[] = ['PENDING', 'RESOLVED', 'REJECTED']
const SUPPORTED_BOARD_REPORT_TARGET_TYPES: ReportTargetTypeFilter[] = ['POST', 'COMMENT']
const activeReportStatuses = useSupportedCommonCodeValues(
  COMMON_CODE_TYPES.REPORT_STATUS,
  SUPPORTED_REPORT_STATUSES,
)
const activeReportTargetTypes = useSupportedCommonCodeValues(
  COMMON_CODE_TYPES.TARGET_TYPE,
  SUPPORTED_BOARD_REPORT_TARGET_TYPES,
)
const reportStatusOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  ...activeReportStatuses.value.map((value) => ({
    value,
    label: t(`admin.reports.status.${value}`),
  })),
])
const reportTargetTypeOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  ...activeReportTargetTypes.value.map((value) => ({
    value,
    label: t(`admin.dashboard.auditTargets.${value}`),
  })),
])
const selectedReport = ref<Report | null>(null)
const isReportDetailOpen = ref(false)

function openReportDetail(report: Report) {
  selectedReport.value = report
  isReportDetailOpen.value = true
}

function closeReportDetail() {
  isReportDetailOpen.value = false
  selectedReport.value = null
}

watch(
  [() => props.boardUrl, () => props.enabled] as const,
  ([nextBoardUrl, nextEnabled], [previousBoardUrl]) => {
    if (!nextEnabled || nextBoardUrl !== previousBoardUrl) closeReportDetail()
  },
  { flush: 'sync' },
)

watch(activeReportStatuses, (statuses) => {
  const selectedStatus = governance.reportStatus.value
  if (selectedStatus && !statuses.includes(selectedStatus)) {
    governance.handleReportStatusChange('')
  }
})
watch(activeReportTargetTypes, (targetTypes) => {
  const selectedTargetType = governance.reportTargetType.value
  if (selectedTargetType && !targetTypes.includes(selectedTargetType)) {
    governance.handleReportTargetTypeChange('')
  }
})

const unsubscribeSessionBoundary = subscribeAuthSessionBoundary(closeReportDetail)
onScopeDispose(unsubscribeSessionBoundary)
</script>

<template>
  <div class="space-y-8">
    <section class="rounded-lg border nv-border" aria-labelledby="board-manager-reports-title">
      <div class="border-b nv-border px-4 py-4">
        <h2 id="board-manager-reports-title" class="text-sm font-semibold nv-title">
          {{ t('admin.reports.title') }}
        </h2>
        <div class="mt-3 flex flex-wrap items-end justify-between gap-3">
          <div class="flex flex-wrap items-end gap-3">
            <AdminFilterField :label="t('admin.reports.statusFilter')" for-id="board-report-status" width="select">
              <BaseSelect
                id="board-report-status"
                :model-value="governance.reportStatus.value"
                :options="reportStatusOptions"
                @update:model-value="governance.handleReportStatusChange"
              />
            </AdminFilterField>
            <AdminFilterField :label="t('admin.reports.targetType')" for-id="board-report-target" width="select">
              <BaseSelect
                id="board-report-target"
                :model-value="governance.reportTargetType.value"
                :options="reportTargetTypeOptions"
                @update:model-value="governance.handleReportTargetTypeChange"
              />
            </AdminFilterField>
          </div>
          <PageSizeSelector
            v-model="governance.reportPagination.size.value"
            :options="[20, 50, 100]"
            @change="governance.reportPagination.handleSizeChange()"
          />
        </div>
      </div>

      <div v-if="governance.reportQuery.isLoading.value" class="py-8 text-center" role="status" aria-live="polite">
        <BaseSpinner size="md" />
      </div>
      <ErrorState
        v-else-if="governance.reportQuery.isError.value"
        :message="t('common.messages.loadFailed')"
        :show-retry="true"
        title-tag="h3"
        @retry="governance.reportQuery.refetch()"
      />
      <ReportList
        v-else
        :reports="governance.reportPage.items.value"
        read-only
        @view-detail="openReportDetail"
      />
      <div class="px-4 pb-4">
        <AdminPaginationFooter
          :page="governance.reportPage.currentPage.value"
          :total-pages="governance.reportPage.totalPages.value"
          :total-elements="governance.reportPage.totalElements.value"
          @page-change="governance.reportPagination.handlePageChange"
        />
      </div>
    </section>

    <section class="rounded-lg border nv-border" aria-labelledby="board-manager-audits-title">
      <div class="border-b nv-border px-4 py-4">
        <h2 id="board-manager-audits-title" class="text-sm font-semibold nv-title">
          {{ t('admin.dashboard.auditLogs') }}
        </h2>
        <form class="mt-3 space-y-3" @submit.prevent="governance.applyAuditFilters">
          <div class="flex flex-wrap items-end gap-3">
            <AdminFilterField :label="t('admin.dashboard.auditAction')" for-id="board-audit-action">
              <BaseInput id="board-audit-action" v-model="governance.auditFilterForm.action" />
            </AdminFilterField>
            <AdminFilterField :label="t('admin.dashboard.auditActorType')" for-id="board-audit-actor-type" width="select">
              <BaseSelect
                id="board-audit-actor-type"
                v-model="governance.auditFilterForm.actorType"
                :options="[
                  { value: '', label: t('admin.common.all') },
                  { value: 'USER', label: t('admin.users.role.USER') },
                  { value: 'SYSTEM', label: t('notification.actors.system') },
                ]"
              />
            </AdminFilterField>
            <AdminFilterField :label="t('admin.dashboard.auditActorUserId')" for-id="board-audit-actor-user-id">
              <BaseInput
                id="board-audit-actor-user-id"
                v-model="governance.auditFilterForm.actorUserId"
                type="number"
                min="1"
                step="1"
              />
            </AdminFilterField>
          </div>
          <div class="flex flex-wrap items-end gap-3">
            <AdminFilterField :label="t('admin.dashboard.auditStartDate')" for-id="board-audit-start-date" width="date">
              <BaseInput id="board-audit-start-date" v-model="governance.auditFilterForm.startDate" type="date" />
            </AdminFilterField>
            <AdminFilterField :label="t('admin.dashboard.auditEndDate')" for-id="board-audit-end-date" width="date">
              <BaseInput id="board-audit-end-date" v-model="governance.auditFilterForm.endDate" type="date" />
            </AdminFilterField>
            <AdminFilterActions @search="governance.applyAuditFilters" @reset="governance.resetAuditFilters" />
            <PageSizeSelector
              v-model="governance.auditPagination.size.value"
              :options="[20, 50, 100]"
              @change="governance.auditPagination.handleSizeChange()"
            />
          </div>
        </form>
      </div>

      <div v-if="governance.auditQuery.isLoading.value" class="py-8 text-center" role="status" aria-live="polite">
        <BaseSpinner size="md" />
      </div>
      <ErrorState
        v-else-if="governance.auditQuery.isError.value"
        :message="t('common.messages.loadFailed')"
        :show-retry="true"
        title-tag="h3"
        @retry="governance.auditQuery.refetch()"
      />
      <AdminAuditLogTable
        v-else
        :audits="governance.auditPage.items.value"
        :caption="t('admin.dashboard.auditLogs')"
        :empty-text="t('admin.dashboard.auditEmpty')"
        show-board-name
      />
      <div class="px-4 pb-4">
        <AdminPaginationFooter
          :page="governance.auditPage.currentPage.value"
          :total-pages="governance.auditPage.totalPages.value"
          :total-elements="governance.auditPage.totalElements.value"
          @page-change="governance.auditPagination.handlePageChange"
        />
      </div>
    </section>

    <ReportDetailModal
      :is-open="isReportDetailOpen"
      :report="selectedReport"
      @close="closeReportDetail"
    />
  </div>
</template>

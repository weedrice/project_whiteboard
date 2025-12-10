<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import ReportList from '@/components/admin/ReportList.vue'

const { t } = useI18n()
const toastStore = useToastStore()
const { useReports, useResolveReport } = useAdmin()

const page = ref(0)
const size = ref(20)
const params = computed(() => ({
    page: page.value,
    size: size.value
}))

const { data: reportsData, isLoading } = useReports(params)
const { mutateAsync: resolveReport } = useResolveReport()

const reports = computed(() => reportsData.value?.content || [])

async function handleResolve(report) {
    if (!confirm(t('admin.reports.messages.confirmResolve'))) return
    try {
        await resolveReport({ reportId: report.reportId, data: { status: 'RESOLVED' } })
        toastStore.addToast(t('admin.reports.messages.resolved'), 'success')
    } catch (err) {
        // Error handled globally
    }
}

async function handleReject(report) {
    if (!confirm(t('admin.reports.messages.confirmReject'))) return
    try {
        await resolveReport({ reportId: report.reportId, data: { status: 'REJECTED' } })
        toastStore.addToast(t('admin.reports.messages.rejected'), 'success')
    } catch (err) {
        // Error handled globally
    }
}
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.reports.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.reports.description') }}</p>
      </div>
    </div>
    
    <ReportList 
      :reports="reports" 
      @resolve="handleResolve" 
      @reject="handleReject" 
    />
  </div>
</template>

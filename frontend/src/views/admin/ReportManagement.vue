<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { Check, X, AlertTriangle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const reports = ref([])
const isLoading = ref(false)

async function fetchReports() {
  isLoading.value = true
  try {
    const res = await adminApi.getReports()
    if (res.data.success) {
      reports.value = res.data.data.content || res.data.data // Handle pagination or list
    }
  } catch (err) {
    logger.error('Failed to fetch reports:', err)
  } finally {
    isLoading.value = false
  }
}

async function handleResolve(report) {
    if (!confirm(t('admin.reports.messages.confirmResolve'))) return
    try {
        await adminApi.resolveReport(report.reportId, { status: 'RESOLVED' })
        report.status = 'RESOLVED'
        alert(t('admin.reports.messages.resolved'))
    } catch (err) {
        logger.error(err)
        alert(t('admin.reports.messages.resolveFailed'))
    }
}

async function handleReject(report) {
    if (!confirm(t('admin.reports.messages.confirmReject'))) return
    try {
        await adminApi.resolveReport(report.reportId, { status: 'REJECTED' })
        report.status = 'REJECTED'
        alert(t('admin.reports.messages.rejected'))
    } catch (err) {
        logger.error(err)
        alert(t('admin.reports.messages.rejectFailed'))
    }
}

onMounted(() => {
  fetchReports()
})
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900">{{ t('admin.reports.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700">{{ t('admin.reports.description') }}</p>
      </div>
    </div>
    
    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
            <table class="min-w-full divide-y divide-gray-300">
              <thead class="bg-gray-50">
                <tr>
                  <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">{{ t('common.id') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.reports.table.reporter') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.target') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.reason') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.status') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.createdAt') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 bg-white">
                <tr v-for="report in reports" :key="report.reportId">
                  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">{{ report.reportId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ report.reporterDisplayName }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                    {{ report.targetType }} #{{ report.targetId }}
                  </td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ report.contents }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                    <span class="inline-flex rounded-full px-2 text-xs font-semibold leading-5" 
                        :class="{
                            'bg-yellow-100 text-yellow-800': report.status === 'PENDING',
                            'bg-green-100 text-green-800': report.status === 'RESOLVED',
                            'bg-gray-100 text-gray-800': report.status === 'REJECTED'
                        }">
                      {{ t(`admin.reports.status.${report.status}`) }}
                    </span>
                  </td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ report.createdAt }}</td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <div v-if="report.status === 'PENDING'" class="flex justify-end space-x-2">
                        <button @click="handleResolve(report)" class="text-green-600 hover:text-green-900" :title="t('admin.reports.actions.resolve')">
                            <Check class="h-4 w-4" />
                        </button>
                        <button @click="handleReject(report)" class="text-red-600 hover:text-red-900" :title="t('admin.reports.actions.reject')">
                            <X class="h-4 w-4" />
                        </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Check, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  reports: any[]
}>()

const emit = defineEmits<{
  (e: 'resolve', report: any): void
  (e: 'reject', report: any): void
}>()

function onResolve(report: any) {
  emit('resolve', report)
}

function onReject(report: any) {
  emit('reject', report)
}
</script>

<template>
  <div class="mt-8 flex flex-col">
    <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
      <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
        <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg border border-gray-200 dark:border-gray-700">
          <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
            <thead class="bg-gray-50 dark:bg-gray-700">
              <tr>
                <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 dark:text-white sm:pl-6">{{ t('common.id') }}</th>
                <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('admin.reports.table.reporter') }}</th>
                <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.target') }}</th>
                <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.reason') }}</th>
                <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.status') }}</th>
                <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.createdAt') }}</th>
                <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                  <span class="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
              <tr v-for="report in reports" :key="report.reportId">
                <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 dark:text-white sm:pl-6">{{ report.reportId }}</td>
                <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ report.reporterDisplayName }}</td>
                <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                  {{ report.targetType }} #{{ report.targetId }}
                </td>
                <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ report.contents }}</td>
                <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                  <span class="inline-flex rounded-full px-2 text-xs font-semibold leading-5" 
                      :class="{
                          'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200': report.status === 'PENDING',
                          'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200': report.status === 'RESOLVED',
                          'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300': report.status === 'REJECTED'
                      }">
                    {{ t(`admin.reports.status.${report.status}`) }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ report.createdAt }}</td>
                <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                  <div v-if="report.status === 'PENDING'" class="flex justify-end space-x-2">
                      <button @click="onResolve(report)" class="text-green-600 hover:text-green-900 dark:text-green-400 dark:hover:text-green-300" :title="t('admin.reports.actions.resolve')">
                          <Check class="h-4 w-4" />
                      </button>
                      <button @click="onReject(report)" class="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300" :title="t('admin.reports.actions.reject')">
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
</template>

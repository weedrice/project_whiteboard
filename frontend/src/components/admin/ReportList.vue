<script setup lang="ts">
import { Check, X, ShieldAlert } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import { computed } from 'vue'
import type { Report } from '@/types'

const { t } = useI18n()

defineProps<{
  reports: Report[]
}>()

const emit = defineEmits<{
  (e: 'resolve', report: Report): void
  (e: 'reject', report: Report): void
  (e: 'sanction', report: Report): void
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
  { key: 'reportId', label: t('common.id'), width: '10%' },
  { key: 'reporterDisplayName', label: t('admin.reports.table.reporter'), width: '15%' },
  { key: 'target', label: t('common.target'), width: '20%' },
  { key: 'contents', label: t('common.reason'), width: '25%' },
  { key: 'status', label: t('common.status'), width: '10%' },
  { key: 'createdAt', label: t('common.createdAt'), width: '10%' },
  { key: 'actions', label: '', align: 'right' as const, width: '10%' }
])
</script>

<template>
  <div class="mt-8">
    <BaseTable :columns="columns" :items="reports" :emptyText="t('common.noData')">
      <template #cell-target="{ item }">
        {{ item.targetType }} #{{ item.targetId }}
      </template>

      <template #cell-status="{ item }">
        <BaseBadge :variant="item.status === 'PENDING' ? 'warning' : item.status === 'RESOLVED' ? 'success' : 'gray'"
          size="sm">
          {{ t(`admin.reports.status.${item.status}`) }}
        </BaseBadge>
      </template>

      <template #cell-actions="{ item }">
        <div v-if="item.status === 'PENDING'" class="flex justify-end space-x-2">
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
        </div>
      </template>
    </BaseTable>
  </div>
</template>


<script setup lang="ts">
import { Trash2, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import { computed } from 'vue'
import type { IpBlock } from '@/types'

const { t } = useI18n()

defineProps<{
  ipBlocks: IpBlock[]
}>()

const emit = defineEmits<{
  (e: 'unblock', ipAddress: string): void
  (e: 'viewDetail', ipBlock: IpBlock): void
}>()

function onUnblock(ipAddress: string) {
  emit('unblock', ipAddress)
}

const columns = computed(() => [
  { key: 'ipAddress', label: t('admin.security.table.ipAddress'), width: '20%' },
  { key: 'reason', label: t('admin.security.table.reason'), width: '30%' },
  { key: 'admin', label: t('admin.security.table.adminId'), width: '15%' },
  { key: 'startDate', label: t('admin.security.table.createdAt'), width: '20%' },
  { key: 'actions', label: '', align: 'right' as const, width: '15%' }
])
</script>

<template>
  <div class="mt-8">
    <BaseTable :columns="columns" :items="ipBlocks" row-key="ipAddress" :emptyText="t('common.noData')">
      <template #cell-admin="{ item }">
        {{ item.admin.adminId }}
      </template>

      <template #cell-actions="{ item }">
        <div class="flex justify-end space-x-2">
          <AdminActionButton :label="t('common.viewDetail')" tone="accent" icon-only @click="$emit('viewDetail', item)">
            <Eye class="h-4 w-4" />
          </AdminActionButton>
          <AdminActionButton label="차단 해제" tone="danger" icon-only @click="onUnblock(item.ipAddress)">
            <Trash2 class="h-4 w-4" />
          </AdminActionButton>
        </div>
      </template>
    </BaseTable>
  </div>
</template>


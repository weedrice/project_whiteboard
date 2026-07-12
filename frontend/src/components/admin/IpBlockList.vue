<script setup lang="ts">
import { Trash2, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminTableActions from '@/components/admin/AdminTableActions.vue'
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
  { key: 'admin', label: t('admin.security.table.adminId'), width: '15%', hideBelow: 'lg' as const },
  { key: 'startDate', label: t('admin.security.table.createdAt'), width: '20%', hideBelow: 'sm' as const },
  { key: 'actions', label: '', align: 'right' as const, width: '15%' }
])
</script>

<template>
  <div class="mt-8">
    <AdminPaginatedTable
      table-class=""
      :columns="columns"
      :caption="t('admin.dashboard.blockedIps')"
      :items="ipBlocks"
      row-key="ipAddress"
      :empty-text="t('common.noData')"
      :show-footer="false"
    >
      <template #cell-admin="{ item }">
        {{ item.admin.adminId }}
      </template>

      <template #cell-actions="{ item }">
        <AdminTableActions>
          <AdminActionButton :label="t('common.viewDetail')" tone="accent" icon-only @click="$emit('viewDetail', item)">
            <Eye class="h-4 w-4" />
          </AdminActionButton>
          <AdminActionButton :label="t('admin.security.actions.unblock')" tone="danger" icon-only @click="onUnblock(item.ipAddress)">
            <Trash2 class="h-4 w-4" />
          </AdminActionButton>
        </AdminTableActions>
      </template>
    </AdminPaginatedTable>
  </div>
</template>


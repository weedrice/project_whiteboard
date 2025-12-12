<script setup lang="ts">
import { Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseTable from '@/components/common/BaseTable.vue'
import { computed } from 'vue'

const { t } = useI18n()

defineProps<{
  ipBlocks: any[]
}>()

const emit = defineEmits<{
  (e: 'unblock', ipAddress: string): void
}>()

function onUnblock(ipAddress: string) {
  emit('unblock', ipAddress)
}

const columns = computed(() => [
  { key: 'ipAddress', label: t('admin.security.table.ipAddress'), width: '20%' },
  { key: 'reason', label: t('admin.security.table.reason'), width: '30%' },
  { key: 'adminId', label: t('admin.security.table.adminId'), width: '15%' },
  { key: 'createdAt', label: t('admin.security.table.createdAt'), width: '20%' },
  { key: 'actions', label: '', align: 'right' as const, width: '15%' }
])
</script>

<template>
  <div class="mt-8">
    <BaseTable :columns="columns" :items="ipBlocks" :emptyText="t('common.noData')">
      <template #cell-actions="{ item }">
        <BaseButton @click="onUnblock(item.ipAddress)" variant="danger" size="sm">
          <Trash2 class="h-4 w-4" />
        </BaseButton>
      </template>
    </BaseTable>
  </div>
</template>

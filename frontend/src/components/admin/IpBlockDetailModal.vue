<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminModalContentState from '@/components/admin/AdminModalContentState.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import DetailSection from '@/components/admin/detail/DetailSection.vue'
import DescriptionGrid from '@/components/admin/detail/DescriptionGrid.vue'
import DescriptionItem from '@/components/admin/detail/DescriptionItem.vue'
import { formatDate } from '@/utils/date'
import type { IpBlock } from '@/types'

const { t } = useI18n()

defineProps<{
  isOpen: boolean
  ipBlock: IpBlock | null
}>()

defineEmits<{
  (e: 'close'): void
}>()
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.security.detail.title')" @close="$emit('close')">
    <AdminModalContentState :empty="!ipBlock" empty-text="">
      <div v-if="ipBlock" class="space-y-6">
        <DetailSection :title="t('admin.security.detail.blockInfo')">
          <DescriptionGrid>
            <DescriptionItem :label="t('admin.security.table.ipAddress')" value-class="mt-1 text-sm nv-text font-mono">
              {{ ipBlock.ipAddress }}
            </DescriptionItem>
            <DescriptionItem :label="t('admin.security.table.adminId')">
              {{ ipBlock.admin.adminId }}
            </DescriptionItem>
            <DescriptionItem :label="t('admin.security.table.createdAt')">
              {{ formatDate(ipBlock.startDate) }}
            </DescriptionItem>
            <DescriptionItem v-if="ipBlock.endDate" :label="t('admin.security.detail.endDate')">
              {{ formatDate(ipBlock.endDate) }}
            </DescriptionItem>
          </DescriptionGrid>
        </DetailSection>

        <DetailSection :title="t('admin.security.table.reason')">
          <div class="nv-surface-muted rounded-lg p-4">
            <p class="text-sm nv-text-muted whitespace-pre-wrap">{{ ipBlock.reason }}</p>
          </div>
        </DetailSection>
      </div>
    </AdminModalContentState>
  </BaseModal>
</template>

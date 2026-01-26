<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
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
    <div v-if="ipBlock" class="space-y-6">
      <!-- IP 차단 정보 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.security.detail.blockInfo') }}
        </h3>
        <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">
              {{ t('admin.security.table.ipAddress') }}
            </dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white font-mono">{{ ipBlock.ipAddress }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">
              {{ t('admin.security.table.adminId') }}
            </dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ ipBlock.adminId }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">
              {{ t('admin.security.table.createdAt') }}
            </dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ formatDate(ipBlock.createdAt) }}</dd>
          </div>
        </dl>
      </div>

      <!-- 차단 사유 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.security.table.reason') }}
        </h3>
        <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
          <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ ipBlock.reason }}</p>
        </div>
      </div>
    </div>
  </BaseModal>
</template>

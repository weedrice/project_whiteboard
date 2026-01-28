<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import { formatDate } from '@/utils/date'
import type { Report } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  isOpen: boolean
  report: Report | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

const statusVariant = computed(() => {
  if (!props.report) return 'gray'
  switch (props.report.status) {
    case 'PENDING':
      return 'warning'
    case 'RESOLVED':
      return 'success'
    case 'REJECTED':
      return 'gray'
    default:
      return 'gray'
  }
})

const targetTypeLabel = computed(() => {
  if (!props.report) return ''
  switch (props.report.targetType) {
    case 'POST':
      return t('common.post')
    case 'COMMENT':
      return t('common.comment')
    case 'USER':
      return t('common.user')
    default:
      return props.report.targetType
  }
})
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.reports.detail.title')" @close="$emit('close')">
    <div v-if="report" class="space-y-6">
      <!-- 신고 정보 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.reports.detail.reportInfo') }}
        </h3>
        <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.id') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ report.reportId }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.reports.table.reporter') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ report.reporterDisplayName }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.target') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">
              <template v-if="report.targetDisplayName != null && report.targetLoginId != null">
                <div class="flex flex-col">
                  <!-- 닉네임 -->
                  <span>{{ report.targetDisplayName }}</span>
                  <!-- ID -->
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ report.targetLoginId }}</span>
                </div>
              </template>
              <template v-else>
                {{ targetTypeLabel }} #{{ report.targetId }}
              </template>
            </dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.status') }}</dt>
            <dd class="mt-1">
              <BaseBadge :variant="statusVariant" size="sm">
                {{ t(`admin.reports.status.${report.status}`) }}
              </BaseBadge>
            </dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.createdAt') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ formatDate(report.createdAt) }}</dd>
          </div>
        </dl>
      </div>

      <!-- 신고 유형 / 대상 콘텐츠 ID -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.reports.reasonType') }}
        </h3>
        <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div v-if="report.reasonType">
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.reports.reasonType') }}</dt>
            <dd class="mt-0.5 text-sm text-gray-900 dark:text-white">{{ report.reasonType }}</dd>
          </div>
          <div v-if="report.targetType === 'POST' || report.targetType === 'COMMENT'">
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.reports.targetContentId') }}</dt>
            <dd class="mt-0.5 text-sm text-gray-900 dark:text-white">{{ report.targetId }}</dd>
          </div>
        </dl>
      </div>
      <!-- 사유 (처리 비고) -->
      <div class="border-t border-gray-200 dark:border-gray-600 pt-4">
        <h3 class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">{{ t('admin.reports.remark') }}</h3>
        <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ report.remark || '-' }}</p>
      </div>
    </div>
  </BaseModal>
</template>

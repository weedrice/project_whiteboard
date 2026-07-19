<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminInlinePager from '@/components/admin/AdminInlinePager.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import type { AdminUserSanctionViewItem } from '@/features/admin/users/useAdminUserDetailTabs'

interface PageNavigationState {
  number: number
  totalPages: number
}

withDefaults(defineProps<{
  items: AdminUserSanctionViewItem[]
  loading: boolean
  error?: boolean
  pageData?: PageNavigationState | null
}>(), { error: false })

defineEmits<{
  previous: []
  next: []
  retry: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div v-if="loading" class="py-6 text-center text-sm nv-text-subtle" role="status" aria-live="polite">
      {{ t('common.loading') }}
    </div>
    <ErrorState
      v-else-if="error"
      title-tag="h3"
      :message="t('common.messages.loadFailed')"
      :show-icon="false"
      show-retry
      @retry="$emit('retry')"
    />
    <div v-else-if="!items.length" class="py-6 text-center text-sm nv-text-subtle" role="status" aria-live="polite">
      {{ t('admin.users.detail.sanctionsEmpty') }}
    </div>
    <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
      <div v-for="sanction in items" :key="sanction.sanctionId" class="rounded-lg border nv-border p-3">
        <div class="flex flex-wrap items-center gap-2">
          <AdminStatusBadge :label="sanction.typeLabel" :variant="sanction.typeVariant" />
          <span class="text-xs nv-text-subtle">{{ sanction.processorText }}</span>
        </div>
        <div class="mt-2 whitespace-pre-wrap break-words text-sm nv-text">{{ sanction.remark }}</div>
        <div class="mt-2 text-xs nv-text-subtle">{{ sanction.periodText }}</div>
        <div v-if="sanction.contentText" class="mt-1 text-xs nv-text-subtle">{{ sanction.contentText }}</div>
      </div>
    </div>
    <AdminInlinePager
      v-if="pageData"
      :page="pageData.number"
      :total-pages="pageData.totalPages"
      @previous="$emit('previous')"
      @next="$emit('next')"
    />
  </div>
</template>

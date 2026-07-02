<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminInlinePager from '@/components/admin/AdminInlinePager.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import type { AdminUserSubscriptionViewItem } from '@/features/admin/users/useAdminUserDetailTabs'

interface PageNavigationState {
  number: number
  totalPages: number
}

defineProps<{
  items: AdminUserSubscriptionViewItem[]
  loading: boolean
  pageData?: PageNavigationState | null
}>()

defineEmits<{
  previous: []
  next: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div v-if="loading" class="py-6 text-center text-sm nv-text-subtle">{{ t('common.loading') }}</div>
    <div v-else-if="!items.length" class="py-6 text-center text-sm nv-text-subtle">
      {{ t('admin.users.detail.subscriptionsEmpty') }}
    </div>
    <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
      <div v-for="board in items" :key="board.boardId" class="rounded-lg border nv-border p-3">
        <div class="truncate text-sm font-medium nv-title">{{ board.boardName }}</div>
        <div class="mt-2 flex flex-wrap gap-1">
          <AdminStatusBadge v-for="badge in board.badges" :key="badge.label" :label="badge.label" :variant="badge.variant" />
        </div>
        <div class="mt-1 text-xs nv-text-subtle">{{ board.boardPath }}</div>
        <div class="mt-1 text-xs nv-text-subtle">{{ board.sortOrderText }}</div>
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

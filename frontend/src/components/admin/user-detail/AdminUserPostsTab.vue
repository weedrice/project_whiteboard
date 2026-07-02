<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminInlinePager from '@/components/admin/AdminInlinePager.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import type { AdminUserPostViewItem } from '@/features/admin/users/useAdminUserDetailTabs'

interface PageNavigationState {
  number: number
  totalPages: number
}

defineProps<{
  items: AdminUserPostViewItem[]
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
      {{ t('admin.users.detail.postsEmpty') }}
    </div>
    <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
      <div v-for="post in items" :key="post.postId" class="rounded-lg border nv-border p-3">
        <div class="truncate text-sm font-medium nv-title">{{ post.title }}</div>
        <div class="mt-2 flex flex-wrap gap-1">
          <AdminStatusBadge v-for="badge in post.badges" :key="badge.label" :label="badge.label" :variant="badge.variant" />
        </div>
        <div class="mt-2 text-xs nv-text-subtle">
          {{ post.metaText }}
        </div>
        <div v-if="post.categoryText" class="mt-1 text-xs nv-text-subtle">
          {{ post.categoryText }}
        </div>
        <div class="mt-1 text-xs nv-text-subtle">
          {{ post.statsText }}
        </div>
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

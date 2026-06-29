<script setup lang="ts">
import { computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Users, FileText, Activity } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminMetricCard from '@/components/admin/AdminMetricCard.vue'
import type { DashboardStats } from '@/types/admin'

const { t } = useI18n()
const { useDashboardStats } = useAdmin()

const { data: statsData } = useDashboardStats()

const stats = computed(() => {
  const data = (statsData.value || {}) as Partial<DashboardStats>
  return [
    { name: t('admin.dashboard.totalUsers'), stat: data.totalUsers ?? 0, icon: Users, path: '/admin/users' },
    { name: t('admin.dashboard.pendingReports'), stat: data.pendingReports ?? 0, icon: FileText, path: '/admin/reports', tone: 'warning' as const },
    { name: t('admin.dashboard.activeUsers24h'), stat: data.activeUsers ?? 0, icon: Activity, path: '/admin/users', tone: 'success' as const },
  ]
})
</script>

<template>
  <div>
    <h1 class="text-2xl font-semibold nv-title">{{ t('admin.dashboard.title') }}</h1>

    <div class="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <AdminMetricCard
        v-for="item in stats"
        :key="item.name"
        :label="item.name"
        :value="item.stat"
        :tone="item.tone"
      >
        <template #icon>
          <component :is="item.icon" class="h-6 w-6" />
        </template>
        <template #footer>
          <router-link :to="item.path" class="text-sm font-medium nv-accent-text hover:brightness-95">
            {{ t('admin.dashboard.viewDetail') }}
          </router-link>
        </template>
      </AdminMetricCard>
    </div>

    <div class="mt-8">
      <h2 class="text-lg font-medium nv-title">{{ t('admin.dashboard.recentActivity') }}</h2>
      <AdminPanel class="mt-4 sm:rounded-md" padding="none" border="transparent" overflow="hidden">
        <ul role="list" class="divide-y divide-[var(--nv-border)]">
          <li class="px-4 py-4 sm:px-6 text-center nv-text-subtle">
            {{ t('admin.dashboard.noActivity') }}
          </li>
        </ul>
      </AdminPanel>
    </div>
  </div>
</template>

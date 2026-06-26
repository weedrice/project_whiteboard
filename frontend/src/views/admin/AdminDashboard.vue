<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Users, FileText, Activity } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminPanel from '@/components/admin/AdminPanel.vue'

const { t } = useI18n()
const { useDashboardStats } = useAdmin()

const { data: statsData } = useDashboardStats()


interface DashboardStats {
  totalUsers?: number
  pendingReports?: number
  activeUsers?: number
}

const stats = computed(() => {
  const data: DashboardStats = statsData.value || {}
  return [
    { name: t('admin.dashboard.totalUsers'), stat: data.totalUsers || '0', icon: Users, change: '0%', changeType: 'increase', path: '/admin/users' },
    { name: t('admin.dashboard.pendingReports'), stat: data.pendingReports || '0', icon: FileText, change: '0%', changeType: 'decrease', path: '/admin/reports' },
    { name: t('admin.dashboard.activeUsers24h'), stat: data.activeUsers || '0', icon: Activity, change: '0%', changeType: 'increase', path: '/admin/users' },
  ]
})

const recentActivity = ref([])
</script>

<template>
  <div>
    <h1 class="text-2xl font-semibold nv-title">{{ t('admin.dashboard.title') }}</h1>

    <div class="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <AdminPanel
        v-for="item in stats"
        :key="item.name"
        padding="none"
        border="transparent"
        overflow="hidden"
      >
        <div class="p-5">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <component :is="item.icon" class="h-6 w-6 nv-text-subtle" aria-hidden="true" />
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium nv-text-subtle truncate">
                  {{ item.name }}
                </dt>
                <dd>
                  <div class="text-lg font-medium nv-title">
                    {{ item.stat }}
                  </div>
                </dd>
              </dl>
            </div>
          </div>
        </div>
        <div class="nv-surface-muted px-5 py-3">
          <div class="text-sm">
            <router-link :to="item.path"
              class="font-medium nv-accent-text hover:brightness-95">
              {{ t('admin.dashboard.viewDetail') }}
            </router-link>
          </div>
        </div>
      </AdminPanel>
    </div>

    <div class="mt-8">
      <h2 class="text-lg font-medium nv-title">{{ t('admin.dashboard.recentActivity') }}</h2>
      <AdminPanel class="mt-4 sm:rounded-md" padding="none" border="transparent" overflow="hidden">
        <ul role="list" class="divide-y divide-[var(--nv-border)]">
          <li v-if="recentActivity.length === 0" class="px-4 py-4 sm:px-6 text-center nv-text-subtle">
            {{ t('admin.dashboard.noActivity') }}
          </li>
          <!-- Activity items would go here -->
        </ul>
      </AdminPanel>
    </div>
  </div>
</template>

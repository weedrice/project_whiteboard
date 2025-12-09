<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Users, FileText, ShieldAlert, Activity } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { useDashboardStats } = useAdmin()

const { data: statsData } = useDashboardStats()

const stats = computed(() => {
    const data = statsData.value || {}
    return [
      { name: t('admin.dashboard.totalUsers'), stat: data.totalUsers || '0', icon: Users, change: '0%', changeType: 'increase' },
      { name: t('admin.dashboard.pendingReports'), stat: data.pendingReports || '0', icon: FileText, change: '0%', changeType: 'decrease' },
      { name: t('admin.dashboard.blockedIps'), stat: data.blockedIps || '0', icon: ShieldAlert, change: '0%', changeType: 'increase' },
    ]
})

const recentActivity = ref([])
</script>

<template>
  <div>
    <h1 class="text-2xl font-semibold text-gray-900 dark:text-white">{{ t('admin.dashboard.title') }}</h1>
    
    <div class="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="item in stats" :key="item.name" class="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg border border-transparent dark:border-gray-700">
        <div class="p-5">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <component :is="item.icon" class="h-6 w-6 text-gray-400 dark:text-gray-500" aria-hidden="true" />
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">
                  {{ item.name }}
                </dt>
                <dd>
                  <div class="text-lg font-medium text-gray-900 dark:text-white">
                    {{ item.stat }}
                  </div>
                </dd>
              </dl>
            </div>
          </div>
        </div>
        <div class="bg-gray-50 dark:bg-gray-700 px-5 py-3">
          <div class="text-sm">
            <a href="#" class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">
              {{ t('admin.dashboard.viewDetail') }}
            </a>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-8">
      <h2 class="text-lg font-medium text-gray-900 dark:text-white">{{ t('admin.dashboard.recentActivity') }}</h2>
      <div class="mt-4 bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-md border border-transparent dark:border-gray-700">
        <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
          <li v-if="recentActivity.length === 0" class="px-4 py-4 sm:px-6 text-center text-gray-500 dark:text-gray-400">
            {{ t('admin.dashboard.noActivity') }}
          </li>
          <!-- Activity items would go here -->
        </ul>
      </div>
    </div>
  </div>
</template>

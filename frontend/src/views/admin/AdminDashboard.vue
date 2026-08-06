<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAdmin } from '@/features/admin/useAdmin'
import { Users, FileText, Activity } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminMetricCard from '@/components/admin/AdminMetricCard.vue'
import AdminAuditLogTable from '@/components/admin/AdminAuditLogTable.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import type { DashboardStats, ModerationAuditSearchParams } from '@/types/admin'

const { t } = useI18n()
const admin = useAdmin()
const { useDashboardStats, useDeepDashboardStats } = admin
const selectedDays = ref<30 | 90>(30)
const auditAction = ref('')
const auditActorType = ref('')
const auditBoardUrl = ref('')
const auditActorUserId = ref('')
const auditStartDate = ref('')
const auditEndDate = ref('')
const auditActionOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  ...['POST_BLIND', 'POST_UNBLIND', 'POST_AUTO_BLIND', 'COMMENT_AUTO_BLIND']
    .map(value => ({ value, label: value })),
])
const auditActorOptions = computed(() => [
  { value: '', label: t('admin.dashboard.auditActorType') },
  { value: 'USER', label: 'USER' },
  { value: 'SYSTEM', label: 'SYSTEM' },
])

const {
  data: statsData,
  isLoading: isStatsLoading,
  isError: isStatsError,
  refetch: refetchStats,
} = useDashboardStats()
const {
  data: deepStatsData,
  isLoading: isDeepStatsLoading,
  isError: isDeepStatsError,
  refetch: refetchDeepStats,
} = useDeepDashboardStats(selectedDays)
const auditParams = computed<ModerationAuditSearchParams>(() => ({
  page: 0,
  size: 10,
  sort: 'createdAt,desc',
  action: auditAction.value || undefined,
  actorType: auditActorType.value || undefined,
  boardUrl: auditBoardUrl.value.trim() || undefined,
  actorUserId: auditActorUserId.value ? Number(auditActorUserId.value) : undefined,
  startDate: auditStartDate.value || undefined,
  endDate: auditEndDate.value || undefined,
}))
const {
  data: auditData,
  isLoading: isAuditLoading,
  isError: isAuditError,
  refetch: refetchAudits,
} = admin.useModerationAudits(auditParams)

const stats = computed(() => {
  const data = (statsData.value || {}) as Partial<DashboardStats>
  return [
    { name: t('admin.dashboard.totalUsers'), stat: data.totalUsers ?? 0, icon: Users, path: '/admin/users' },
    { name: t('admin.dashboard.pendingReports'), stat: data.pendingReports ?? 0, icon: FileText, path: '/admin/reports', tone: 'warning' as const },
    { name: t('admin.dashboard.activeUsers24h'), stat: data.activeUsers ?? 0, icon: Activity, path: '/admin/users', tone: 'success' as const },
  ]
})

const daily = computed(() => deepStatsData.value?.daily ?? [])
const topBoards = computed(() => deepStatsData.value?.topBoards ?? [])
const moderation = computed(() => deepStatsData.value?.moderation)
const maxDailyValue = computed(() => Math.max(
  1,
  ...daily.value.flatMap((item) => [item.signups, item.posts, item.comments, item.reports]),
))
const linePoints = computed(() => {
  if (daily.value.length === 0) return ''
  const width = 320
  const height = 120
  const lastIndex = Math.max(1, daily.value.length - 1)
  return daily.value.map((item, index) => {
    const x = (index / lastIndex) * width
    const y = height - (item.posts / maxDailyValue.value) * height
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})
const barWidth = computed(() => daily.value.length > 0 ? 320 / daily.value.length : 0)
const moderationItems = computed(() => {
  const data = moderation.value
  return [
    { label: t('admin.dashboard.pending'), value: data?.pendingReports ?? 0 },
    { label: t('admin.dashboard.resolved'), value: data?.resolvedReports ?? 0 },
    { label: t('admin.dashboard.rejected'), value: data?.rejectedReports ?? 0 },
    { label: t('admin.dashboard.autoBlinds'), value: data?.autoBlinds ?? 0 },
    { label: t('admin.dashboard.managerBlinds'), value: data?.managerBlinds ?? 0 },
  ]
})
const auditLogs = computed(() => auditData.value?.content ?? [])
</script>

<template>
  <div>
    <h1 class="text-2xl font-semibold nv-title">{{ t('admin.dashboard.title') }}</h1>

    <div v-if="isStatsLoading" class="mt-4 py-8" role="status" aria-busy="true">
      <BaseSpinner />
    </div>
    <ErrorState
      v-else-if="isStatsError"
      class="mt-4"
      title-tag="h2"
      :message="t('common.messages.loadFailed')"
      show-retry
      @retry="refetchStats()"
    />
    <div v-else class="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
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
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 class="text-lg font-medium nv-title">{{ t('admin.dashboard.deepStats') }}</h2>
        <div class="inline-flex rounded-md border nv-border p-1">
          <button
            type="button"
            class="px-3 py-1.5 text-sm"
            :class="selectedDays === 30 ? 'nv-surface-muted nv-title' : 'nv-text-subtle'"
            @click="selectedDays = 30"
          >
            {{ t('admin.dashboard.days30') }}
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-sm"
            :class="selectedDays === 90 ? 'nv-surface-muted nv-title' : 'nv-text-subtle'"
            @click="selectedDays = 90"
          >
            {{ t('admin.dashboard.days90') }}
          </button>
        </div>
      </div>

      <div v-if="isDeepStatsLoading" class="mt-4 py-8" role="status" aria-busy="true">
        <BaseSpinner />
      </div>
      <ErrorState
        v-else-if="isDeepStatsError"
        class="mt-4"
        title-tag="h3"
        :message="t('common.messages.loadFailed')"
        show-retry
        @retry="refetchDeepStats()"
      />
      <div v-else class="mt-4 grid grid-cols-1 gap-5 xl:grid-cols-3">
        <AdminPanel class="xl:col-span-2" padding="md">
          <h3 class="text-base font-medium nv-title">{{ t('admin.dashboard.dailyActivity') }}</h3>
          <svg viewBox="0 0 320 150" class="mt-4 h-48 w-full" role="img" :aria-label="t('admin.dashboard.dailyActivity')">
            <line x1="0" y1="120" x2="320" y2="120" stroke="var(--nv-line)" />
            <g v-for="(item, index) in daily" :key="item.date">
              <rect
                :x="index * barWidth + 1"
                :y="120 - (item.comments / maxDailyValue) * 120"
                :width="Math.max(1, barWidth - 2)"
                :height="(item.comments / maxDailyValue) * 120"
                fill="var(--nv-accent-bg)"
              />
            </g>
            <polyline
              :points="linePoints"
              fill="none"
              stroke="var(--nv-accent)"
              stroke-width="3"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <text x="0" y="145" class="fill-current text-[10px] nv-text-subtle">{{ daily[0]?.date ?? '' }}</text>
            <text x="320" y="145" text-anchor="end" class="fill-current text-[10px] nv-text-subtle">{{ daily[daily.length - 1]?.date ?? '' }}</text>
          </svg>
          <div class="mt-3 flex flex-wrap gap-4 text-sm nv-text-subtle">
            <span>{{ t('admin.dashboard.postsLine') }}</span>
            <span>{{ t('admin.dashboard.commentsBar') }}</span>
          </div>
        </AdminPanel>

        <AdminPanel padding="md">
          <h3 class="text-base font-medium nv-title">{{ t('admin.dashboard.moderationStatus') }}</h3>
          <dl class="mt-4 space-y-3">
            <div v-for="item in moderationItems" :key="item.label" class="flex items-center justify-between gap-3">
              <dt class="text-sm nv-text-subtle">{{ item.label }}</dt>
              <dd class="text-lg font-semibold nv-title">{{ item.value }}</dd>
            </div>
          </dl>
        </AdminPanel>
      </div>

      <AdminPanel v-if="!isDeepStatsLoading && !isDeepStatsError" class="mt-5" padding="none" overflow="hidden">
        <div class="border-b nv-border px-4 py-3">
          <h3 class="text-base font-medium nv-title">{{ t('admin.dashboard.topSpaces') }}</h3>
        </div>
        <ul v-if="topBoards.length > 0" role="list" class="divide-y divide-[var(--nv-line)]">
          <li v-for="board in topBoards" :key="board.boardId" class="flex items-center justify-between gap-3 px-4 py-3">
            <div class="min-w-0">
              <p class="truncate font-medium nv-title">{{ board.boardName }}</p>
              <p class="text-sm nv-text-subtle">/{{ board.boardUrl }}</p>
            </div>
            <span class="text-sm font-semibold nv-title">{{ board.activityCount }}</span>
          </li>
        </ul>
        <p v-else class="px-4 py-5 text-center nv-text-subtle">{{ t('admin.dashboard.noActivity') }}</p>
      </AdminPanel>

      <AdminPanel class="mt-5" padding="none" overflow="hidden">
        <div class="border-b nv-border px-4 py-3">
          <h3 class="text-base font-medium nv-title">{{ t('admin.dashboard.auditLogs') }}</h3>
          <div class="mt-3 grid grid-cols-1 gap-2 md:grid-cols-3 xl:grid-cols-6">
            <BaseSelect v-model="auditAction" :label="t('admin.dashboard.auditAction')" :options="auditActionOptions" hide-label />
            <BaseSelect v-model="auditActorType" :label="t('admin.dashboard.auditActorType')" :options="auditActorOptions" hide-label />
            <BaseInput
              v-model="auditBoardUrl"
              :label="t('admin.dashboard.auditBoardUrl')"
              hide-label
              :placeholder="t('admin.dashboard.auditBoardUrl')"
            />
            <BaseInput
              v-model="auditActorUserId"
              :label="t('admin.dashboard.auditActorUserId')"
              hide-label
              inputmode="numeric"
              :placeholder="t('admin.dashboard.auditActorUserId')"
            />
            <BaseInput v-model="auditStartDate" type="date" :label="t('admin.dashboard.auditStartDate')" hide-label />
            <BaseInput v-model="auditEndDate" type="date" :label="t('admin.dashboard.auditEndDate')" hide-label />
          </div>
        </div>
        <div v-if="isAuditLoading" class="px-4 py-8" role="status" aria-busy="true">
          <BaseSpinner />
        </div>
        <ErrorState
          v-else-if="isAuditError"
          class="m-4"
          title-tag="h4"
          :message="t('common.messages.loadFailed')"
          show-retry
          @retry="refetchAudits()"
        />
        <AdminAuditLogTable
          v-else
          :audits="auditLogs"
          :caption="t('admin.dashboard.auditLogs')"
          :empty-text="t('admin.dashboard.auditEmpty')"
          show-board-url
        />
      </AdminPanel>
    </div>
  </div>
</template>

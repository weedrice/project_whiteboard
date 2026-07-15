<script setup lang="ts">
import { ref } from 'vue'
import { Bell, CalendarCheck, FileText, Settings } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAttendance } from '@/composables/useAttendance'
import { useNotification } from '@/features/notifications/queries/useNotification'

defineProps<{
  postCount: number
  commentCount: number
}>()

const { t } = useI18n()
const enabled = ref(true)
const { useMyAttendance } = useAttendance()
const { useUnreadCount } = useNotification()
const { data: attendance } = useMyAttendance(enabled)
const { data: unreadCount } = useUnreadCount()

const cards = [
  {
    titleKey: 'user.dashboard.contentSummary',
    href: '/mypage',
    icon: FileText,
  },
  {
    titleKey: 'user.dashboard.activitySummary',
    href: '/mypage/attendance',
    icon: CalendarCheck,
  },
  {
    titleKey: 'user.dashboard.communicationSummary',
    href: '/mypage/notifications',
    icon: Bell,
  },
  {
    titleKey: 'user.dashboard.accountSummary',
    href: '/mypage/settings',
    icon: Settings,
  },
]

const cardDescription = (href: string) => {
  if (href === '/mypage') {
    return t('user.dashboard.postsAndComments', { posts: 0, comments: 0 })
  }
  if (href === '/mypage/attendance') {
    return t('user.dashboard.dayStreak', { count: attendance.value?.currentStreakCount ?? 0 })
  }
  if (href === '/mypage/notifications') {
    return t('user.dashboard.unreadNotifications', { count: unreadCount.value ?? 0 })
  }
  return t('user.dashboard.manageSettings')
}
</script>

<template>
  <section class="mb-6" aria-labelledby="mypage-summary-title">
    <h2 id="mypage-summary-title" class="sr-only">{{ $t('user.dashboard.summaryTitle') }}</h2>
    <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <RouterLink
        v-for="card in cards"
        :key="card.href"
        :to="card.href"
        class="group rounded-[var(--nv-radius-md)] border nv-border nv-surface p-4 nv-hover-surface"
      >
        <component :is="card.icon" class="h-5 w-5 nv-text-subtle group-hover:text-[var(--nv-accent)]" aria-hidden="true" />
        <p class="mt-3 text-sm font-semibold nv-title">{{ $t(card.titleKey) }}</p>
        <p v-if="card.href === '/mypage'" class="mt-1 text-xs nv-text-subtle">
          {{ $t('user.dashboard.postsAndComments', { posts: postCount, comments: commentCount }) }}
        </p>
        <p v-else class="mt-1 text-xs nv-text-subtle">{{ cardDescription(card.href) }}</p>
      </RouterLink>
    </div>
  </section>
</template>

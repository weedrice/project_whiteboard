<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MyPageNavigation from '@/components/user/MyPageNavigation.vue'
import type { UserNavigationGroup } from '@/types/userNavigation'

const route = useRoute()

const myPageGroups: UserNavigationGroup[] = [
  {
    nameKey: 'user.navigation.content',
    items: [
      { nameKey: 'user.navigation.overview', href: '/mypage' },
      { nameKey: 'user.scraps', href: '/mypage/scraps' },
      { nameKey: 'user.tabs.drafts', href: '/mypage/drafts' },
      { nameKey: 'user.tabs.recent', href: '/mypage/recent' },
    ],
  },
  {
    nameKey: 'user.navigation.activity',
    items: [
      { nameKey: 'common.points', href: '/mypage/points' },
      { nameKey: 'shop.purchases.title', href: '/mypage/purchases' },
      { nameKey: 'user.navigation.attendance', href: '/mypage/attendance' },
      { nameKey: 'user.tabs.subscriptions', href: '/mypage/subscriptions' },
    ],
  },
  {
    nameKey: 'user.navigation.communication',
    items: [
      { nameKey: 'common.notifications', href: '/mypage/notifications' },
      { nameKey: 'common.mailbox', href: '/mypage/messages' },
    ],
  },
  {
    nameKey: 'user.navigation.account',
    items: [
      { nameKey: 'common.settings', href: '/mypage/settings' },
      { nameKey: 'user.tabs.blocked', href: '/mypage/blocked' },
      { nameKey: 'user.tabs.reports', href: '/mypage/reports' },
    ],
  },
]

const activePath = computed(() => route.path)
</script>

<template>
  <div
    class="mx-auto max-w-7xl pb-[env(safe-area-inset-bottom)]">
    <div class="grid gap-6 lg:grid-cols-[14rem_minmax(0,1fr)] lg:gap-8">
      <MyPageNavigation :groups="myPageGroups" :active-path="activePath" />
      <div class="min-w-0">
        <router-view />
      </div>
    </div>
  </div>
</template>

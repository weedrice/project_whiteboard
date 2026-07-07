<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserNavigation from '@/components/user/UserNavigation.vue'
import type { UserNavigationTab } from '@/types/userNavigation'

const route = useRoute()
const router = useRouter()

const myPageTabs: UserNavigationTab[] = [
  { nameKey: 'common.myPage', href: '/mypage' },
  { nameKey: 'common.notifications', href: '/mypage/notifications' },
  { nameKey: 'common.mailbox', href: '/mypage/messages' },
  { nameKey: 'common.points', href: '/mypage/points' },
  { nameKey: 'user.scraps', href: '/mypage/scraps' },
  { nameKey: 'user.tabs.drafts', href: '/mypage/drafts' },
  { nameKey: 'user.tabs.subscriptions', href: '/mypage/subscriptions' },
  { nameKey: 'user.tabs.recent', href: '/mypage/recent' },
  { nameKey: 'user.tabs.reports', href: '/mypage/reports' },
  { nameKey: 'user.tabs.blocked', href: '/mypage/blocked' },
]

const activePath = computed(() => route.path)

function navigateToTab(href: string) {
  router.push(href)
}
</script>

<template>
  <div
    class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 md:py-8 pb-[calc(1rem+env(safe-area-inset-bottom))] sm:pb-6 md:pb-8">
    <UserNavigation :tabs="myPageTabs" :active-path="activePath" @navigate="navigateToTab" />

    <router-view />
  </div>
</template>

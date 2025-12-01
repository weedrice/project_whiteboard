<template>
  <div class="border-b border-gray-200 mb-6 overflow-x-auto">
    <nav class="-mb-px flex space-x-8 min-w-max" aria-label="Tabs">
      <router-link
        v-for="tab in tabs"
        :key="tab.name"
        :to="tab.href"
        :class="[
          isActive(tab.href)
            ? 'border-indigo-500 text-indigo-600'
            : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
          'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm'
        ]"
        :aria-current="isActive(tab.href) ? 'page' : undefined"
      >
        {{ tab.name }}
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()

const tabs = [
  { name: '마이페이지', href: '/mypage' },
  { name: '표시 설정', href: '/mypage/settings' },
  { name: '포인트 내역', href: '/mypage/points' },
  { name: '스크랩 목록', href: '/mypage/scraps' },
  { name: '최근 읽은 글', href: '/search/recent' },
  { name: '신고 목록', href: '/mypage/reports' },
]

function isActive(href) {
    // Exact match for mypage, but maybe check prefix for others if nested?
    // For now, strict equality is fine as routes are flat.
    return route.path === href
}
</script>

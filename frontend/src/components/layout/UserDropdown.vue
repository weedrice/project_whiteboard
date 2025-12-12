<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { User, LogOut, Settings, CreditCard, FileText, Clock, AlertTriangle, PlusSquare, ChevronDown, Bell, LayoutDashboard, Mail, Star, Slash } from 'lucide-vue-next'
import axios from '@/api'
import logger from '@/utils/logger'
import BaseButton from '@/components/common/BaseButton.vue'

const router = useRouter()
const authStore = useAuthStore()

const props = withDefaults(defineProps<{
  isOpen?: boolean
}>(), {
  isOpen: false
})

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

const points = ref(0)
const dropdownRef = ref<HTMLElement | null>(null)

const toggleDropdown = () => {
  emit('toggle')
}

const fetchPoints = async () => {
  try {
    const { data } = await axios.get('/points/me')
    if (data.success) {
      points.value = data.data.currentPoint
    }
  } catch (error) {
    logger.error('Failed to fetch points:', error)
  }
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/')
}

watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    fetchPoints()
  }
})

onMounted(() => {
  fetchPoints()
})
</script>

<template>
  <div class="relative" ref="dropdownRef">
    <button @click.stop="toggleDropdown"
      class="flex items-center space-x-2 text-sm text-gray-700 dark:text-gray-200 hover:text-indigo-600 dark:hover:text-indigo-400 focus:outline-none">
      <div
        class="h-8 w-8 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold overflow-hidden border border-gray-200 dark:border-gray-600">
        <img v-if="authStore.user?.profileImageUrl" :src="authStore.user.profileImageUrl" alt="Profile"
          class="h-full w-full object-contain bg-white dark:bg-gray-800" />
        <span v-else>
          {{ authStore.user?.displayName?.[0] || authStore.user?.loginId?.[0] || 'U' }}
        </span>
      </div>
      <span class="hidden md:block font-medium">{{ authStore.user?.displayName || authStore.user?.loginId }}</span>
      <ChevronDown class="h-4 w-4 text-gray-500 dark:text-gray-400" />
    </button>

    <div v-if="isOpen"
      class="origin-top-right absolute right-0 mt-2 w-64 rounded-md shadow-lg py-1 bg-white dark:bg-gray-800 ring-1 ring-black ring-opacity-5 dark:ring-gray-700 focus:outline-none z-50">
      <!-- User Info -->
      <div class="px-4 py-3 border-b border-gray-100 dark:border-gray-700">
        <p class="text-sm font-medium text-gray-900 dark:text-white truncate">
          {{ authStore.user?.displayName }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400 truncate mb-2">
          {{ authStore.user?.email }}
        </p>
        <router-link to="/mypage/points"
          class="flex items-center text-sm text-indigo-600 dark:text-indigo-400 font-medium hover:text-indigo-800 dark:hover:text-indigo-300"
          @click="emit('toggle')">
          <CreditCard class="h-4 w-4 mr-1" />
          {{ points.toLocaleString() }} P
        </router-link>
      </div>

      <!-- Group 1: Admin, MyInfo, Points, Settings -->
      <div class="py-1 border-b border-gray-100 dark:border-gray-700">
        <router-link v-if="authStore.user?.role === 'SUPER_ADMIN'" to="/admin/dashboard"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <LayoutDashboard
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('layout.menu.admin') }}
        </router-link>
        <router-link to="/mypage"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <User
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('common.myPage') }}
        </router-link>
        <router-link to="/mypage/points"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <CreditCard
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('common.points') }}
        </router-link>
        <router-link to="/mypage/settings"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <Settings
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('common.settings') }}
        </router-link>
      </div>

      <!-- Group 2: Messages, Notifications -->
      <div class="py-1 border-b border-gray-100 dark:border-gray-700">
        <router-link to="/mypage/messages"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <Mail
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('common.mailbox') }}
        </router-link>
        <router-link to="/mypage/notifications"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <Bell
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('common.notifications') }}
        </router-link>
      </div>

      <!-- Group 3: Scraps, Subscriptions -->
      <div class="py-1 border-b border-gray-100 dark:border-gray-700">
        <router-link to="/mypage/scraps"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <FileText
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('common.scrap') }}
        </router-link>
        <router-link to="/mypage/subscriptions"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <Star
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('user.tabs.subscriptions') }}
        </router-link>
      </div>

      <!-- Group 4: Recent, Reports, Create Board -->
      <div class="py-1 border-b border-gray-100 dark:border-gray-700">
        <router-link to="/mypage/recent"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <Clock
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('layout.menu.recent') }}
        </router-link>
        <router-link to="/mypage/reports"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <AlertTriangle
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('layout.menu.reports') }}
        </router-link>
        <router-link to="/mypage/blocked"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <Slash
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('user.tabs.blocked') }}
        </router-link>
        <router-link to="/board/create"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
          @click="emit('toggle')">
          <PlusSquare
            class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-400" />
          {{ $t('layout.menu.createBoard') }}
        </router-link>
      </div>

      <!-- Logout -->
      <div class="py-1">
        <BaseButton @click="handleLogout" variant="ghost" full-width
          class="w-full text-left group flex items-center px-4 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-700 dark:hover:text-red-400 justify-start">
          <LogOut
            class="mr-3 h-4 w-4 text-red-500 group-hover:text-red-600 dark:text-red-400 dark:group-hover:text-red-300" />
          {{ $t('common.logout') }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useThemeStore } from '@/stores/theme'
import { Search, Bell, Moon, Sun } from 'lucide-vue-next'
import NotificationDropdown from '@/components/notification/NotificationDropdown.vue'
import UserDropdown from '@/components/layout/UserDropdown.vue'
import BoardDropdown from '@/components/layout/BoardDropdown.vue'
import Footer from '@/components/layout/Footer.vue'
import GlobalSearchBar from '@/components/search/GlobalSearchBar.vue'
import AdBanner from '@/components/common/AdBanner.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const themeStore = useThemeStore()
const isNotificationOpen = ref(false)
const activeDropdown = ref(null) // 'subscription', 'all', 'notification', 'user'

const showSidebarAd = computed(() => {
  return !['login', 'signup'].includes(route.name)
})

const toggleNotification = () => {
  if (isNotificationOpen.value) {
    isNotificationOpen.value = false
    activeDropdown.value = null
  } else {
    closeAllDropdowns()
    isNotificationOpen.value = true
    activeDropdown.value = 'notification'
  }
}

const toggleDropdown = (name) => {
  if (activeDropdown.value === name) {
    activeDropdown.value = null
  } else {
    closeAllDropdowns()
    activeDropdown.value = name
  }
}

const closeAllDropdowns = () => {
  isNotificationOpen.value = false
  activeDropdown.value = null
}

// Expose to children
const setActiveDropdown = (name) => {
    if (activeDropdown.value === name) {
        activeDropdown.value = null
    } else {
        isNotificationOpen.value = false
        activeDropdown.value = name
    }
}

// Click outside handler
const handleClickOutside = (event) => {
    if (activeDropdown.value || isNotificationOpen.value) {
        closeAllDropdowns()
    }
}

onMounted(() => {
    document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 transition-colors duration-200 flex flex-col">
    <nav class="bg-white shadow-sm dark:bg-gray-800 dark:border-gray-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex">
            <div class="flex-shrink-0 flex items-center">
              <router-link to="/" class="text-xl font-bold text-indigo-600 dark:text-indigo-400">Whiteboard</router-link>
            </div>
            <div class="hidden sm:ml-6 sm:flex sm:space-x-4 items-center">
              <!-- Navigation Dropdowns -->
              <BoardDropdown 
                v-if="authStore.isAuthenticated" 
                type="subscription" 
                :isOpen="activeDropdown === 'subscription'"
                @toggle="setActiveDropdown('subscription')"
              />
              <BoardDropdown 
                type="all" 
                :isOpen="activeDropdown === 'all'"
                @toggle="setActiveDropdown('all')"
              />
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <!-- Search Bar -->
            <!-- Search Bar -->
            <div class="relative">
              <GlobalSearchBar />
            </div>



            <!-- Notification Dropdown -->
            <div v-if="authStore.isAuthenticated" class="relative">
              <button 
                @click.stop="toggleNotification"
                class="bg-white dark:bg-gray-800 p-1 rounded-full text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 dark:focus:ring-offset-gray-800"
              >
                <span class="sr-only">View notifications</span>
                <Bell class="h-6 w-6" />
                <span v-if="notificationStore.unreadCount > 0" class="absolute top-0 right-0 block h-2 w-2 rounded-full bg-red-400 ring-2 ring-white dark:ring-gray-800"></span>
              </button>

              <div v-if="isNotificationOpen" class="absolute right-0 mt-2 w-80 z-50">
                <NotificationDropdown />
              </div>
            </div>

            <!-- User Menu -->
            <div v-if="authStore.isAuthenticated" class="flex items-center space-x-4">
              <UserDropdown 
                :isOpen="activeDropdown === 'user'"
                @toggle="setActiveDropdown('user')"
              />
            </div>
            <div v-else class="flex items-center space-x-4">
              <router-link to="/login" class="text-gray-500 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white px-3 py-2 rounded-md text-sm font-medium">로그인</router-link>
              <router-link to="/signup" class="bg-indigo-600 text-white hover:bg-indigo-700 px-4 py-2 rounded-md text-sm font-medium">회원가입</router-link>
            </div>
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8 flex-grow w-full">
      <!-- Left Sidebar Ad (Fixed to viewport, centered vertically) - Hidden -->
      <!-- <div v-if="showSidebarAd" class="hidden 2xl:block fixed left-[calc(50%-51.25rem)] top-1/2 -translate-y-1/2 w-40">
        <AdBanner placement="SIDEBAR" />
      </div> -->

      <router-view></router-view>
    </main>

    <Footer />
  </div>
</template>

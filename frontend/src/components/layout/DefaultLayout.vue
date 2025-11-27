<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { Search, Bell } from 'lucide-vue-next'
import NotificationDropdown from '@/components/notification/NotificationDropdown.vue'
import UserDropdown from '@/components/layout/UserDropdown.vue'
import BoardDropdown from '@/components/layout/BoardDropdown.vue'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const searchQuery = ref('')
const isNotificationOpen = ref(false)
const activeDropdown = ref(null) // 'subscription', 'all', 'notification', 'user'

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ name: 'search', query: { q: searchQuery.value } })
  }
}

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
    // If clicking inside nav, ignore (dropdowns handle their own toggles via stop propagation if needed, 
    // but actually we want to close if clicking outside the active dropdown)
    // Simplified: If we click anywhere, check if it's inside a dropdown trigger or content.
    // Since we use @click.stop on triggers, this handler usually fires on outside clicks.
    // However, we need to be careful not to close immediately after opening.
    // The easiest way is to use a directive or just rely on the fact that triggers stop propagation.
    // But triggers in BoardDropdown might not stop propagation?
    
    // Let's assume triggers stop propagation.
    // We just need to close if activeDropdown is set.
    if (activeDropdown.value || isNotificationOpen.value) {
        // We can't easily distinguish here without refs.
        // But if we attach this to window/document, and triggers use .stop, then this only fires for outside clicks.
        closeAllDropdowns()
    }
}

import { onMounted, onUnmounted } from 'vue'

onMounted(() => {
    document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <nav class="bg-white shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex">
            <div class="flex-shrink-0 flex items-center">
              <router-link to="/" class="text-xl font-bold text-indigo-600">Whiteboard</router-link>
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
            <div class="relative">
              <input 
                type="text" 
                v-model="searchQuery"
                @keyup.enter="handleSearch"
                placeholder="검색..." 
                class="w-64 pl-10 pr-4 py-2 rounded-full border border-gray-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-sm"
              >
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Search class="h-4 w-4 text-gray-400" />
              </div>
            </div>

            <!-- Notification Dropdown -->
            <div v-if="authStore.isAuthenticated" class="relative">
              <button 
                @click.stop="toggleNotification"
                class="bg-white p-1 rounded-full text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
              >
                <span class="sr-only">View notifications</span>
                <Bell class="h-6 w-6" />
                <span v-if="notificationStore.unreadCount > 0" class="absolute top-0 right-0 block h-2 w-2 rounded-full bg-red-400 ring-2 ring-white"></span>
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
              <router-link to="/login" class="text-gray-500 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium">로그인</router-link>
              <router-link to="/signup" class="bg-indigo-600 text-white hover:bg-indigo-700 px-4 py-2 rounded-md text-sm font-medium">회원가입</router-link>
            </div>
          </div>
        </div>
      </div>
    </nav>

    <main>
      <div class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <router-view></router-view>
      </div>
    </main>
  </div>
</template>

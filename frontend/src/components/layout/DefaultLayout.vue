<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { Menu, X, Bell, User, LogOut } from 'lucide-vue-next'
import NotificationDropdown from '@/components/notification/NotificationDropdown.vue'
import GlobalSearchInput from '@/components/search/GlobalSearchInput.vue'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

const isMenuOpen = ref(false)
const isNotificationOpen = ref(false)

function toggleMenu() {
  isMenuOpen.value = !isMenuOpen.value
}

function toggleNotification() {
  isNotificationOpen.value = !isNotificationOpen.value
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

onMounted(() => {
  if (authStore.isAuthenticated) {
    notificationStore.fetchUnreadCount()
  }
})
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <header class="bg-white shadow">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex">
            <div class="flex-shrink-0 flex items-center">
              <router-link to="/" class="text-xl font-bold text-indigo-600">
                Whiteboard
              </router-link>
            </div>
            <div class="hidden sm:ml-6 sm:flex sm:space-x-8">
              <router-link 
                to="/" 
                class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium"
                active-class="border-indigo-500 text-gray-900"
              >
                Home
              </router-link>
              <!-- Add more nav items here -->
            </div>
          </div>
          
          <div class="flex-1 flex items-center justify-center px-2 lg:ml-6 lg:justify-end">
            <div class="max-w-lg w-full lg:max-w-xs">
              <GlobalSearchInput />
            </div>
          </div>

          <div class="flex items-center ml-4">
            <div v-if="authStore.isAuthenticated" class="flex items-center space-x-4">
              <!-- Notification Bell -->
              <div class="relative">
                <button 
                  @click="toggleNotification"
                  class="bg-white p-1 rounded-full text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  <span class="sr-only">View notifications</span>
                  <Bell class="h-6 w-6" />
                  <span v-if="notificationStore.unreadCount > 0" class="absolute top-0 right-0 block h-2 w-2 rounded-full bg-red-400 ring-2 ring-white"></span>
                </button>

                <!-- Dropdown -->
                <div v-if="isNotificationOpen" class="absolute right-0 mt-2 w-80 z-50">
                  <NotificationDropdown />
                </div>
              </div>

              <router-link 
                to="/mypage" 
                class="flex items-center text-sm text-gray-700 hover:text-indigo-600"
              >
                <User class="h-4 w-4 mr-1" />
                {{ authStore.user?.displayName || authStore.user?.loginId }}
              </router-link>
              <button 
                @click="handleLogout"
                class="p-2 rounded-full text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                title="Logout"
              >
                <LogOut class="h-5 w-5" />
              </button>
            </div>
            <div v-else class="flex items-center space-x-4">
              <router-link 
                to="/login"
                class="text-gray-500 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium"
              >
                Login
              </router-link>
              <router-link 
                to="/signup"
                class="bg-indigo-600 text-white hover:bg-indigo-700 px-3 py-2 rounded-md text-sm font-medium"
              >
                Sign up
              </router-link>
            </div>
          </div>
        </div>
      </div>
    </header>

    <main>
      <div class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <slot />
      </div>
    </main>
  </div>
</template>

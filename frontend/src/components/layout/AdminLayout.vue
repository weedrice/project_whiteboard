<template>
  <div class="min-h-screen bg-gray-100 flex">
    <!-- Sidebar -->
    <aside class="w-64 bg-gray-800 text-white flex flex-col">
      <div class="h-16 flex items-center justify-center border-b border-gray-700">
        <h1 class="text-xl font-bold">Admin Panel</h1>
      </div>
      <nav class="flex-1 px-4 py-6 space-y-2">
        <router-link
          to="/admin/dashboard"
          class="block px-4 py-2 rounded-md hover:bg-gray-700 transition-colors"
          active-class="bg-gray-700"
        >
          Dashboard
        </router-link>
        <router-link
          to="/admin/users"
          class="block px-4 py-2 rounded-md hover:bg-gray-700 transition-colors"
          active-class="bg-gray-700"
        >
          Users
        </router-link>
        <router-link
          to="/admin/reports"
          class="block px-4 py-2 rounded-md hover:bg-gray-700 transition-colors"
          active-class="bg-gray-700"
        >
          Reports
        </router-link>
        <router-link
          to="/admin/settings"
          class="block px-4 py-2 rounded-md hover:bg-gray-700 transition-colors"
          active-class="bg-gray-700"
        >
          Settings
        </router-link>
      </nav>
      <div class="p-4 border-t border-gray-700">
        <button @click="logout" class="w-full px-4 py-2 text-sm text-gray-400 hover:text-white transition-colors">
          Logout
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col">
      <header class="h-16 bg-white shadow-sm flex items-center justify-between px-6">
        <h2 class="text-lg font-medium text-gray-800">{{ $route.meta.title || 'Admin' }}</h2>
        <div class="flex items-center space-x-4">
          <span class="text-sm text-gray-600">{{ user?.nickname || 'Admin' }}</span>
        </div>
      </header>
      <main class="flex-1 p-6 overflow-auto">
        <slot></slot>
      </main>
    </div>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

const authStore = useAuthStore()
const router = useRouter()
const { user } = storeToRefs(authStore)

const logout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

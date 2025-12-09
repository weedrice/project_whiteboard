<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { 
  LayoutDashboard, 
  Users, 
  ShieldAlert, 
  Settings, 
  LogOut,
  UserCog,
  FileText
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const navigation = computed(() => [
  { name: t('admin.menu.dashboard'), href: '/admin/dashboard', icon: LayoutDashboard },
  { name: t('admin.menu.users'), href: '/admin/users', icon: Users },
  { name: t('admin.menu.boards'), href: '/admin/boards', icon: LayoutDashboard },
  { name: t('admin.menu.admins'), href: '/admin/admins', icon: UserCog },
  { name: t('admin.menu.reports'), href: '/admin/reports', icon: FileText },
  { name: t('admin.menu.security'), href: '/admin/security', icon: ShieldAlert },
  { name: t('admin.menu.settings'), href: '/admin/settings', icon: Settings },
])

const currentRouteName = computed(() => route.name)

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-gray-100 dark:bg-gray-900 flex transition-colors duration-200">
    <!-- Sidebar -->
    <div class="hidden md:flex md:w-64 md:flex-col md:fixed md:inset-y-0">
      <div class="flex-1 flex flex-col min-h-0 bg-indigo-800">
        <div class="flex items-center h-16 flex-shrink-0 px-4 bg-indigo-900">
          <router-link to="/" class="text-xl font-bold text-white hover:text-indigo-200">{{ t('admin.layout.title') }}</router-link>
        </div>
        <div class="flex-1 flex flex-col overflow-y-auto">
          <nav class="flex-1 px-2 py-4 space-y-1">
            <router-link
              v-for="item in navigation"
              :key="item.name"
              :to="item.href"
              class="group flex items-center px-2 py-2 text-sm font-medium rounded-md text-indigo-100 hover:bg-indigo-700 hover:text-white"
              :class="{ 'bg-indigo-900 text-white': route.path.startsWith(item.href) }"
            >
              <component
                :is="item.icon"
                class="mr-3 flex-shrink-0 h-6 w-6 text-indigo-300 group-hover:text-white"
                aria-hidden="true"
              />
              {{ item.name }}
            </router-link>
          </nav>
        </div>

      </div>
    </div>

    <!-- Main content -->
    <div class="md:pl-64 flex flex-col flex-1">
      <main class="flex-1">
        <div class="py-6">
          <div class="px-4 sm:px-6 md:px-8">
            <router-view></router-view>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

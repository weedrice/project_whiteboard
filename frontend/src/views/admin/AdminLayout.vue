<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChevronDown,
  LayoutDashboard,
  Users,
  ShieldAlert,
  Settings,
  UserCog,
  FileText,
  Bug,
  MessageSquare
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()
const isMobileMenuOpen = ref(false)

const navigation = computed(() => [
  { name: t('admin.menu.dashboard'), href: '/admin/dashboard', icon: LayoutDashboard },
  { name: t('admin.menu.users'), href: '/admin/users', icon: Users },
  { name: t('admin.menu.boards'), href: '/admin/boards', icon: LayoutDashboard },
  { name: t('admin.menu.inquiries'), href: '/admin/inquiries', icon: MessageSquare },
  { name: t('admin.menu.admins'), href: '/admin/admins', icon: UserCog },
  { name: t('admin.menu.reports'), href: '/admin/reports', icon: FileText },
  { name: t('admin.menu.errorLogs'), href: '/admin/error-logs', icon: Bug },
  { name: t('admin.menu.security'), href: '/admin/security', icon: ShieldAlert },
  { name: t('admin.menu.settings'), href: '/admin/settings', icon: Settings },
])

const activeNavigationItem = computed(() => (
  navigation.value.find((item) => route.path.startsWith(item.href)) ?? navigation.value[0]
))

watch(
  () => route.fullPath,
  () => {
    isMobileMenuOpen.value = false
  },
)

</script>

<template>
  <div class="min-h-screen nv-page flex transition-colors duration-200">
    <!-- Sidebar -->
    <div class="hidden md:flex md:w-64 md:flex-col md:fixed md:inset-y-0">
      <div class="flex-1 flex flex-col min-h-0 nv-admin-sidebar">
        <div class="flex items-center h-16 flex-shrink-0 px-4 nv-admin-sidebar-header">
          <router-link to="/" class="text-xl font-bold nv-admin-sidebar-title">{{ t('admin.layout.title')
          }}</router-link>
        </div>
        <div class="flex-1 flex flex-col overflow-y-auto">
          <nav class="flex-1 px-2 py-4 space-y-1">
            <router-link v-for="item in navigation" :key="item.name" :to="item.href"
              class="nv-admin-sidebar-link flex items-center px-2 py-2 text-sm font-medium rounded-md"
              :class="{ 'nv-admin-sidebar-link-active': route.path.startsWith(item.href) }">
              <component :is="item.icon" class="nv-admin-sidebar-icon mr-3 flex-shrink-0 h-6 w-6"
                aria-hidden="true" />
              {{ item.name }}
            </router-link>
          </nav>
        </div>

      </div>
    </div>

    <!-- Main content -->
    <div class="md:pl-64 flex flex-col flex-1">
      <header class="md:hidden sticky top-0 z-40 border-b border-[var(--nv-border)] bg-[var(--nv-surface)]/95 backdrop-blur">
        <div class="flex h-14 items-center justify-between gap-3 px-4">
          <router-link to="/" class="min-w-0 text-base font-semibold nv-title">
            {{ t('admin.layout.title') }}
          </router-link>
          <button
            type="button"
            class="inline-flex min-w-0 items-center gap-2 rounded-md border border-[var(--nv-border)] px-3 py-2 text-sm font-medium nv-text"
            :aria-expanded="isMobileMenuOpen"
            aria-controls="admin-mobile-nav"
            @click="isMobileMenuOpen = !isMobileMenuOpen"
          >
            <component :is="activeNavigationItem.icon" class="h-4 w-4 flex-shrink-0" aria-hidden="true" />
            <span class="truncate">{{ activeNavigationItem.name }}</span>
            <ChevronDown
              class="h-4 w-4 flex-shrink-0 transition-transform"
              :class="{ 'rotate-180': isMobileMenuOpen }"
              aria-hidden="true"
            />
          </button>
        </div>

        <nav
          v-if="isMobileMenuOpen"
          id="admin-mobile-nav"
          class="border-t border-[var(--nv-border)] px-2 py-2"
          :aria-label="t('admin.layout.title')"
        >
          <router-link
            v-for="item in navigation"
            :key="item.name"
            :to="item.href"
            class="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium nv-text"
            :class="{ 'nv-admin-sidebar-link-active': route.path.startsWith(item.href) }"
          >
            <component :is="item.icon" class="h-5 w-5 flex-shrink-0" aria-hidden="true" />
            {{ item.name }}
          </router-link>
        </nav>
      </header>

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

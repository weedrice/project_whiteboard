<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useKeyboardStore } from '@/stores/keyboard'
import { useNotification } from '@/composables/useNotification'
import NotificationDropdown from '@/components/notification/NotificationDropdown.vue'
import UserDropdown from '@/components/layout/UserDropdown.vue'
import BoardDropdown from '@/components/layout/BoardDropdown.vue'
import Footer from '@/components/layout/Footer.vue'
import GlobalSearchBar from '@/components/search/GlobalSearchBar.vue'
import KeyboardShortcutsModal from '@/components/common/KeyboardShortcutsModal.vue'
import RecentBoardsBar from '@/components/layout/RecentBoardsBar.vue'
import MobileBottomNav from '@/components/layout/MobileBottomNav.vue'
import { isInputFocused } from '@/utils/keyboard'

import logoLight from '@/assets/noviis_logo.webp'
import logoDark from '@/assets/noviis_logo_dark.webp'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const keyboardStore = useKeyboardStore()
const { useUnreadCount, connectToSse, closeSse } = useNotification()

const logoSrc = computed(() => (themeStore.isDark ? logoDark : logoLight))

const isNotificationOpen = ref(false)
const activeDropdown = ref<string | null>(null)
const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 640)

const { data: unreadCount } = useUnreadCount()

watch(
  () => authStore.isAuthenticated,
  (isAuthenticated) => {
    if (isAuthenticated) {
      connectToSse()
      return
    }

    closeSse()
  },
  { immediate: true }
)

const isAuthRoute = computed(() => {
  return ['login', 'signup', 'find-account', 'forgot-password', 'reset-password', 'oauth-callback'].includes(String(route.name ?? ''))
})
const isAdminRoute = computed(() => String(route.name ?? '').startsWith('Admin'))
const showRecentBoardsBar = computed(() => !isAuthRoute.value && !isAdminRoute.value)
const showMobileBottomNav = computed(() => !isAuthRoute.value && !isAdminRoute.value)
const isEditorFocused = ref(false)

const closeAllDropdowns = () => {
  isNotificationOpen.value = false
  activeDropdown.value = null
  keyboardStore.closeDropdown()
}

const toggleNotification = () => {
  if (isNotificationOpen.value) {
    closeAllDropdowns()
    return
  }

  closeAllDropdowns()
  isNotificationOpen.value = true
  activeDropdown.value = 'notification'
  keyboardStore.setOpenDropdown('notification', [])
}

const setActiveDropdown = (name: string) => {
  if (activeDropdown.value === name) {
    closeAllDropdowns()
    return
  }

  isNotificationOpen.value = false
  activeDropdown.value = name
}

const handleClickOutside = () => {
  if (activeDropdown.value || isNotificationOpen.value) {
    closeAllDropdowns()
  }
}

const handleKeyDown = async (event: KeyboardEvent) => {
  const { key, shiftKey, ctrlKey, altKey, metaKey } = event

  if (key === 'Escape') {
    if (activeDropdown.value || isNotificationOpen.value) {
      event.preventDefault()
      closeAllDropdowns()
      return
    }

    if (keyboardStore.isShortcutsModalOpen) {
      event.preventDefault()
      keyboardStore.closeShortcutsModal()
    }
    return
  }

  if (keyboardStore.isShortcutsModalOpen) return
  if (activeDropdown.value || isNotificationOpen.value) return
  if (isInputFocused()) return

  if (ctrlKey || metaKey) {
    if (key === 'k' || key === 'K') {
      event.preventDefault()
      await router.push('/search')
    }
    return
  }

  if (altKey) {
    if ((key === 'n' || key === 'N') && authStore.isAuthenticated) {
      event.preventDefault()
      await router.push('/mypage/notifications')
    }
    return
  }

  if (shiftKey) {
    if (key === 'B') {
      event.preventDefault()
      await router.push('/boards')
      return
    }

    if ((key === '/' || key === '?') && !isMobile.value) {
      event.preventDefault()
      keyboardStore.toggleShortcutsModal()
    }
    return
  }

  switch (key) {
    case 's':
      if (authStore.isAuthenticated) {
        event.preventDefault()
        setActiveDropdown('subscription')
      }
      break
    case 'b':
      event.preventDefault()
      setActiveDropdown('all')
      break
    case 'h':
      event.preventDefault()
      await router.push('/')
      break
    case 'm':
      if (authStore.isAuthenticated) {
        event.preventDefault()
        setActiveDropdown('user')
      }
      break
    case 'd':
      event.preventDefault()
      themeStore.toggleTheme()
      break
    case 'q':
      if (authStore.isAuthenticated) {
        event.preventDefault()
        await authStore.logout()
        await router.push('/')
      }
      break
  }
}

const mediaQuery = typeof window !== 'undefined' ? window.matchMedia('(max-width: 639px)') : null

const updateIsMobile = () => {
  if (!mediaQuery) return

  isMobile.value = mediaQuery.matches
  if (mediaQuery.matches) {
    keyboardStore.closeShortcutsModal()
  }
}

const handleEditorFocusChange = (event: Event) => {
  const customEvent = event as CustomEvent<boolean>
  isEditorFocused.value = Boolean(customEvent.detail)
}

onMounted(() => {
  if (mediaQuery) {
    isMobile.value = mediaQuery.matches
    mediaQuery.addEventListener('change', updateIsMobile)
  }

  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeyDown)
  window.addEventListener('noviis:editor-focus-change', handleEditorFocusChange as EventListener)
})

onUnmounted(() => {
  if (mediaQuery) {
    mediaQuery.removeEventListener('change', updateIsMobile)
  }

  closeSse()
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('noviis:editor-focus-change', handleEditorFocusChange as EventListener)
})

const skipToMainContent = (event: Event) => {
  event.preventDefault()
  const mainContent = document.getElementById('main-content')
  if (!mainContent) return

  mainContent.focus()
  mainContent.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const goToNotificationsPage = async () => {
  if (!authStore.isAuthenticated) {
    await router.push({ name: 'login', query: { redirect: '/mypage/notifications' } })
    return
  }

  await router.push('/mypage/notifications')
}
</script>

<template>
  <div class="nv-shell min-h-screen flex flex-col">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[70] focus:rounded-full focus:bg-[var(--nv-accent)] focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-white"
      @click="skipToMainContent"
    >
      Skip to content
    </a>

    <nav class="nv-topnav">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
        <div class="flex min-w-0 items-center gap-3">
          <router-link to="/" class="flex items-center gap-2" :aria-label="`${$t('common.appName')} home`">
            <img :src="themeStore.isDark ? '/favicon_dark.ico' : '/favicon.ico'" alt="" class="h-8 w-8 rounded md:hidden" />
            <img :src="logoSrc" :alt="$t('common.appName')" class="hidden h-8 w-auto md:block" />
            <span class="text-lg font-semibold tracking-[-0.04em] text-[var(--nv-ink)] md:hidden">{{ $t('common.appName') }}</span>
          </router-link>

          <div class="hidden items-center gap-2 md:flex">
            <router-link to="/" class="nv-shell-tab" :class="{ 'is-active': route.name === 'home' }">
              {{ $t('layout.topNav.home') }}
            </router-link>
            <BoardDropdown
              v-if="authStore.isAuthenticated"
              type="subscription"
              :isOpen="activeDropdown === 'subscription'"
              :desktop-label="$t('layout.topNav.subscribedBoards')"
              :mobile-label="$t('layout.topNav.subscribedShort')"
              @toggle="setActiveDropdown('subscription')"
            />
            <BoardDropdown
              type="all"
              :isOpen="activeDropdown === 'all'"
              :desktop-label="$t('layout.topNav.boards')"
              :mobile-label="$t('layout.topNav.boards')"
              @toggle="setActiveDropdown('all')"
            />
          </div>
        </div>

        <div class="flex items-center gap-2 sm:gap-3">
          <div class="relative w-auto min-w-[2.5rem] sm:min-w-[16rem]">
            <GlobalSearchBar />
          </div>

          <div v-if="authStore.isAuthenticated" class="relative hidden sm:block">
            <button
              type="button"
              class="nv-shell-icon-button"
              aria-label="Open notifications"
              @click.stop="toggleNotification"
            >
              <Bell class="h-5 w-5" />
              <span v-if="unreadCount && unreadCount > 0" class="nv-shell-dot" />
            </button>

            <div v-if="isNotificationOpen" class="absolute right-0 z-50 mt-3 w-80">
              <NotificationDropdown />
            </div>
          </div>

          <button
            v-if="authStore.isAuthenticated"
            type="button"
            class="nv-shell-icon-button sm:hidden"
            aria-label="Go to notifications"
            @click="goToNotificationsPage"
          >
            <Bell class="h-5 w-5" />
            <span v-if="unreadCount && unreadCount > 0" class="nv-shell-dot" />
          </button>

          <div v-if="authStore.isAuthenticated" class="hidden items-center sm:flex">
            <UserDropdown :isOpen="activeDropdown === 'user'" @toggle="setActiveDropdown('user')" />
          </div>
          <div v-else class="flex items-center">
            <router-link to="/login" class="nv-login-link">
              {{ $t('common.login') }}
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <RecentBoardsBar v-if="showRecentBoardsBar" />

    <main id="main-content" tabindex="-1" class="mx-auto w-full max-w-7xl flex-grow px-4 py-6 pb-24 outline-none sm:px-6 sm:pb-6 lg:px-8">
      <router-view />
    </main>

    <Footer />
    <MobileBottomNav :hidden="!showMobileBottomNav || isEditorFocused" />
    <KeyboardShortcutsModal v-if="!isMobile" />
  </div>
</template>

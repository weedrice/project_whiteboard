<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useKeyboardStore } from '@/stores/keyboard'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useEventListener } from '@/composables/useEventListener'
import { useNotificationStream } from '@/features/notifications/stream/useNotificationStream'
import { useShellDropdowns } from '@/composables/useShellDropdowns'
import { useShellShortcuts } from '@/composables/useShellShortcuts'
import { useShellViewport } from '@/composables/useShellViewport'
import { useThemePreference } from '@/composables/useThemePreference'
import { getMotionAwareScrollBehavior } from '@/utils/motion'
import NotificationDropdown from '@/components/notification/NotificationDropdown.vue'
import UserDropdown from '@/components/layout/UserDropdown.vue'
import BoardDropdown from '@/components/layout/BoardDropdown.vue'
import Footer from '@/components/layout/Footer.vue'
import GlobalSearchBar from '@/components/search/GlobalSearchBar.vue'
import KeyboardShortcutsModal from '@/components/common/KeyboardShortcutsModal.vue'
import RecentBoardsBar from '@/components/layout/RecentBoardsBar.vue'
import MobileBottomNav from '@/components/layout/MobileBottomNav.vue'
import BadgeAwardCelebration from '@/components/user/BadgeAwardCelebration.vue'

import logoLight from '@/assets/noviis_logo.webp'
import logoDark from '@/assets/noviis_logo_dark.webp'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const keyboardStore = useKeyboardStore()
const { requireAuth } = useAuthGuard()
const { toggleTheme } = useThemePreference()

const logoSrc = computed(() => (themeStore.isDark ? logoDark : logoLight))

const { unreadCount } = useNotificationStream(
  () => authStore.isAuthenticated,
  () => authStore.sessionGeneration,
)
const unreadNotificationText = computed(() => t('notification.unreadNotifications', { count: unreadCount.value ?? 0 }))
const openNotificationsLabel = computed(() => unreadCount.value && unreadCount.value > 0
  ? `${t('layout.a11y.openNotifications')}. ${unreadNotificationText.value}`
  : t('layout.a11y.openNotifications'))
const goToNotificationsLabel = computed(() => unreadCount.value && unreadCount.value > 0
  ? `${t('layout.a11y.goToNotifications')}. ${unreadNotificationText.value}`
  : t('layout.a11y.goToNotifications'))
const {
  isNotificationOpen,
  activeDropdown,
  closeAllDropdowns,
  toggleNotification,
  setActiveDropdown,
  handleClickOutside
} = useShellDropdowns(keyboardStore)
const { isMobile, isEditorFocused } = useShellViewport(keyboardStore)
const { handleKeyDown } = useShellShortcuts({
  authStore,
  keyboardStore,
  router,
  isMobile,
  hasOpenDropdown: () => !!activeDropdown.value || isNotificationOpen.value,
  closeAllDropdowns,
  setActiveDropdown,
  toggleTheme
})

const isAuthRoute = computed(() => {
  return ['login', 'signup', 'find-account', 'forgot-password', 'reset-password', 'oauth-callback'].includes(String(route.name ?? ''))
})
const isAdminRoute = computed(() => String(route.name ?? '').startsWith('Admin'))
const showRecentBoardsBar = computed(() => !isAuthRoute.value && !isAdminRoute.value)
const showMobileBottomNav = computed(() => !isAuthRoute.value && !isAdminRoute.value)
const notificationTriggerRef = ref<HTMLButtonElement | null>(null)
const notificationPanelRef = ref<HTMLElement | null>(null)
let suppressDropdownFocusRestore = false

watch(() => route.fullPath, () => {
  suppressDropdownFocusRestore = true
  closeAllDropdowns()
  void nextTick(() => {
    suppressDropdownFocusRestore = false
  })
})

watch(isNotificationOpen, (isOpen) => {
  if (isOpen) {
    void nextTick(() => {
      notificationPanelRef.value
        ?.querySelector<HTMLElement>('button:not([disabled]), a[href]')
        ?.focus()
    })
    return
  }

  if (!suppressDropdownFocusRestore && notificationPanelRef.value?.contains(document.activeElement)) {
    notificationTriggerRef.value?.focus()
  }
})

useEventListener(() => document, 'click', handleClickOutside)
useEventListener(() => document, 'keydown', handleKeyDown)

const skipToMainContent = (event: Event) => {
  event.preventDefault()
  const mainContent = document.getElementById('main-content')
  if (!mainContent) return

  mainContent.focus()
  mainContent.scrollIntoView({ behavior: getMotionAwareScrollBehavior(), block: 'start' })
}

const goToNotificationsPage = async () => {
  if (!requireAuth('/mypage/notifications')) {
    return
  }

  await router.push('/mypage/notifications')
}
</script>

<template>
  <div class="nv-shell min-h-dvh flex flex-col">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[var(--nv-z-nav)] focus:rounded-full focus:bg-[var(--nv-accent)] focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-[var(--nv-on-accent)]"
      @click="skipToMainContent"
    >
      {{ t('common.skipToContent') }}
    </a>

    <nav class="nv-topnav">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
        <div class="flex min-w-0 items-center gap-3">
          <router-link to="/" class="flex min-h-11 shrink-0 items-center gap-2 rounded-md nv-focus-ring" :aria-label="t('layout.a11y.homeLink', { appName: t('common.appName') })">
            <img :src="themeStore.isDark ? '/favicon_dark.ico' : '/favicon.ico'" alt="" class="h-8 w-8 rounded md:hidden" />
            <img :src="logoSrc" :alt="$t('common.appName')" class="hidden h-8 w-auto shrink-0 object-contain md:block" />
            <span class="text-lg font-semibold tracking-[-0.04em] text-[var(--nv-ink)] md:hidden">{{ $t('common.appName') }}</span>
          </router-link>

          <div class="hidden items-center gap-2 md:flex">
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

          <div v-if="authStore.isAuthenticated" class="relative hidden sm:block" data-shell-dropdown-root>
            <button
              ref="notificationTriggerRef"
              type="button"
              class="nv-shell-icon-button"
              :aria-label="openNotificationsLabel"
              aria-controls="notification-dropdown-panel"
              :aria-expanded="isNotificationOpen"
              @click.stop="toggleNotification"
            >
              <Bell class="h-5 w-5" />
              <span v-if="unreadCount && unreadCount > 0" class="nv-shell-dot" />
            </button>

            <div v-if="isNotificationOpen" id="notification-dropdown-panel" ref="notificationPanelRef" class="absolute right-0 z-50 mt-3 w-[min(20rem,calc(100vw-2rem))]">
              <NotificationDropdown />
            </div>
          </div>

          <button
            v-if="authStore.isAuthenticated"
            type="button"
            class="nv-shell-icon-button sm:hidden"
            :aria-label="goToNotificationsLabel"
            @click="goToNotificationsPage"
          >
            <Bell class="h-5 w-5" />
            <span v-if="unreadCount && unreadCount > 0" class="nv-shell-dot" />
          </button>

          <span v-if="authStore.isAuthenticated" class="sr-only" role="status" aria-live="polite" aria-atomic="true">
            {{ unreadNotificationText }}
          </span>

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

    <main id="main-content" tabindex="-1" class="nv-main-content mx-auto w-full max-w-7xl flex-grow px-4 py-6 outline-none sm:px-6 lg:px-8">
      <router-view />
    </main>

    <Footer />
    <MobileBottomNav :hidden="!showMobileBottomNav || isEditorFocused" />
    <KeyboardShortcutsModal v-if="!isMobile" />
    <BadgeAwardCelebration v-if="authStore.isAuthenticated" />
  </div>
</template>

<style scoped>
.nv-main-content {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  padding-bottom: calc(var(--nv-bottom-nav-height) + 1.5rem);
  transition: background-color 0.2s ease;
}

@media (min-width: 640px) {
  .nv-main-content {
    padding-bottom: 1.5rem;
  }
}
</style>

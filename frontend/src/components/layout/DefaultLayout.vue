<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useKeyboardStore } from '@/stores/keyboard'
import { useNotification } from '@/composables/useNotification'
import { Search, Bell, Moon, Sun } from 'lucide-vue-next'
import NotificationDropdown from '@/components/notification/NotificationDropdown.vue'
import UserDropdown from '@/components/layout/UserDropdown.vue'
import BoardDropdown from '@/components/layout/BoardDropdown.vue'
import Footer from '@/components/layout/Footer.vue'
import GlobalSearchBar from '@/components/search/GlobalSearchBar.vue'
import AdBanner from '@/components/common/widgets/AdBanner.vue'
import KeyboardShortcutsModal from '@/components/common/KeyboardShortcutsModal.vue'

import logoLight from '@/assets/noviis_logo.png'
import logoDark from '@/assets/noviis_logo_dark.png'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const keyboardStore = useKeyboardStore()
const { useUnreadCount, connectToSse, closeSse } = useNotification()

const logoSrc = computed(() => {
  return themeStore.isDark ? logoDark : logoLight
})

const isNotificationOpen = ref(false)
const activeDropdown = ref<string | null>(null) // 'subscription', 'all', 'notification', 'user'

// Fetch unread count
const { data: unreadCount } = useUnreadCount()

// Connect to SSE if authenticated
watch(() => authStore.isAuthenticated, (isAuthenticated) => {
  if (isAuthenticated) {
    connectToSse()
  } else {
    closeSse()
  }
}, { immediate: true })

const showAds = computed(() => {
  return !['login', 'signup'].includes(route.name as string)
})

const toggleNotification = () => {
  if (isNotificationOpen.value) {
    isNotificationOpen.value = false
    activeDropdown.value = null
    keyboardStore.closeDropdown()
  } else {
    closeAllDropdowns()
    isNotificationOpen.value = true
    activeDropdown.value = 'notification'
    keyboardStore.setOpenDropdown('notification', [])
  }
}

const toggleDropdown = (name: string) => {
  if (activeDropdown.value === name) {
    activeDropdown.value = null
    keyboardStore.closeDropdown()
  } else {
    closeAllDropdowns()
    activeDropdown.value = name
    // BoardDropdown과 UserDropdown은 자체적으로 keyboardStore 등록
  }
}

const closeAllDropdowns = () => {
  isNotificationOpen.value = false
  activeDropdown.value = null
  keyboardStore.closeDropdown()
}

// Expose to children
const setActiveDropdown = (name: string) => {
  if (activeDropdown.value === name) {
    activeDropdown.value = null
    keyboardStore.closeDropdown()
  } else {
    isNotificationOpen.value = false
    activeDropdown.value = name
  }
}

// Click outside handler
const handleClickOutside = (event: Event) => {
  if (activeDropdown.value || isNotificationOpen.value) {
    closeAllDropdowns()
  }
}

// 입력 필드 확인
const isInputFocused = (): boolean => {
  const activeElement = document.activeElement
  if (!activeElement) return false
  const tagName = activeElement.tagName.toLowerCase()
  if (tagName === 'input' || tagName === 'textarea' || tagName === 'select') return true
  if (activeElement.getAttribute('contenteditable') === 'true') return true
  if (activeElement.closest('.ql-editor')) return true
  return false
}

// 전역 키보드 단축키 핸들러
const handleKeyDown = (event: KeyboardEvent) => {
  const { key, shiftKey, ctrlKey, altKey, metaKey } = event

  // 드롭다운 열린 상태에서 ESC
  if (key === 'Escape') {
    if (activeDropdown.value || isNotificationOpen.value) {
      event.preventDefault()
      closeAllDropdowns()
      return
    }
    if (keyboardStore.isShortcutsModalOpen) {
      event.preventDefault()
      keyboardStore.closeShortcutsModal()
      return
    }
    return
  }

  // 단축키 모달 열린 상태에서는 다른 단축키 무시
  if (keyboardStore.isShortcutsModalOpen) return

  // 드롭다운 열린 상태에서는 숫자키 이외 무시 (숫자키는 컴포넌트에서 처리)
  if (activeDropdown.value || isNotificationOpen.value) return

  // 입력 필드에서는 전역 단축키 비활성화
  if (isInputFocused()) return

  // Ctrl/Meta 조합
  if (ctrlKey || metaKey) {
    if (key === 'k' || key === 'K') {
      event.preventDefault()
      router.push('/search')
      return
    }
    return
  }

  // Alt 조합
  if (altKey) {
    if (key === 'n' || key === 'N') {
      if (authStore.isAuthenticated) {
        event.preventDefault()
        router.push('/mypage/notifications')
      }
      return
    }
    return
  }

  // Shift 조합
  if (shiftKey) {
    if (key === 'B') {
      event.preventDefault()
      router.push('/boards')
      return
    }
    // Shift+/ (?)로 단축키 도움말 열기
    if (key === '/' || key === '?') {
      event.preventDefault()
      keyboardStore.toggleShortcutsModal()
      return
    }
    return
  }

  // 단일 키
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
      router.push('/')
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
        authStore.logout()
        router.push('/')
      }
      break
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  closeSse()
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeyDown)
})

const skipToMainContent = (event: Event) => {
  event.preventDefault()
  const mainContent = document.getElementById('main-content')
  if (mainContent) {
    mainContent.focus()
    mainContent.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 transition-colors duration-200 flex flex-col">
    <nav class="bg-white shadow-sm dark:bg-gray-800 dark:border-gray-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex">
            <div class="flex-shrink-0 flex items-center">
              <router-link to="/" class="flex items-center">
                <img :src="logoSrc" alt="Noviis Logo" class="h-8 w-auto" />
              </router-link>
            </div>
            <div class="hidden sm:ml-6 sm:flex sm:space-x-4 items-center">
              <!-- Navigation Dropdowns -->
              <BoardDropdown v-if="authStore.isAuthenticated" type="subscription"
                :isOpen="activeDropdown === 'subscription'" @toggle="setActiveDropdown('subscription')" />
              <BoardDropdown type="all" :isOpen="activeDropdown === 'all'" @toggle="setActiveDropdown('all')" />
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <!-- Search Bar -->
            <div class="relative">
              <GlobalSearchBar />
            </div>

            <!-- Notification Dropdown -->
            <div v-if="authStore.isAuthenticated" class="relative">
              <button @click.stop="toggleNotification"
                class="bg-white dark:bg-gray-800 p-1 rounded-full text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 dark:focus:ring-offset-gray-800">
                <span class="sr-only">View notifications</span>
                <Bell class="h-6 w-6" />
                <span v-if="unreadCount && unreadCount > 0"
                  class="absolute top-0 right-0 block h-2 w-2 rounded-full bg-red-400 ring-2 ring-white dark:ring-gray-800"></span>
              </button>

              <div v-if="isNotificationOpen" class="absolute right-0 mt-2 w-80 z-50">
                <NotificationDropdown />
              </div>
            </div>

            <!-- User Menu -->
            <div v-if="authStore.isAuthenticated" class="flex items-center space-x-4">
              <UserDropdown :isOpen="activeDropdown === 'user'" @toggle="setActiveDropdown('user')" />
            </div>
            <div v-else class="flex items-center space-x-4">
              <router-link to="/login"
                class="text-gray-500 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white px-3 py-2 rounded-md text-sm font-medium">{{
                  $t('common.login') }}</router-link>
              <router-link to="/signup"
                class="bg-indigo-600 text-white hover:bg-indigo-700 px-4 py-2 rounded-md text-sm font-medium">{{
                  $t('auth.signup') }}</router-link>
            </div>
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8 flex-grow w-full">
      <!-- Left Sidebar Ad (Fixed to viewport, centered vertically) -->
      <!-- <div v-if="showAds" class="hidden 2xl:block fixed left-[calc(50%-51.25rem)] top-1/2 -translate-y-1/2 w-40">
        <AdBanner placement="SIDEBAR" />
      </div> -->

      <!-- Top Banner Ad -->
      <!-- <div v-if="showAds" class="mb-6">
        <AdBanner placement="TOP" />
      </div> -->

      <router-view></router-view>
    </main>

    <Footer />

    <!-- Keyboard Shortcuts Modal -->
    <KeyboardShortcutsModal />
  </div>
</template>

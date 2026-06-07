<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed, defineAsyncComponent, onErrorCaptured } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { registerAuthStorageSync, useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import { unwrapApiData } from '@/api/response'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useConfigStore } from '@/stores/config'
import ToastContainer from '@/components/common/widgets/ToastContainer.vue'
import GlobalConfirmModal from '@/components/common/widgets/GlobalConfirmModal.vue'
import GlobalPromptModal from '@/components/common/widgets/GlobalPromptModal.vue'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'
import NetworkStatus from '@/components/common/NetworkStatus.vue'
import logger from '@/utils/logger'
import { useGlobalShortcuts } from '@/composables/useGlobalShortcuts'
import { BOARD_DETAIL_SEARCH_INPUT_ID } from '@/composables/useBoardDetailNavigation'
import { userSettingsQueryKey } from '@/composables/useUser'
import { UserSettings } from '@/types/user'
import { useHead } from '@unhead/vue'

// Import layouts
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
// Async import for AdminLayout to avoid circular dependencies or load only when needed
const AdminLayout = defineAsyncComponent(() => import('@/views/admin/AdminLayout.vue'))

const route = useRoute()
const authStore = useAuthStore()
const { locale, t } = useI18n()
const toastStore = useToastStore()
const queryClient = useQueryClient()

const noIndexRouteNames = new Set([
    'login',
    'signup',
    'find-account',
    'forgot-password',
    'reset-password',
    'oauth-callback',
    'mypage',
    'user-settings',
    'point-history',
    'MyScraps',
    'MyMessages',
    'MyNotifications',
    'MyReports',
    'BlockList',
    'RecentViewed',
    'SubscribedBoards',
    'board-create',
    'board-edit',
    'post-write',
    'post-edit',
    'AdminDashboard',
    'UserManagement',
    'BoardManagement',
    'AdminManagement',
    'ReportManagement',
    'SecuritySettings',
    'GlobalSettings',
    'ErrorLogManagement',
    'error',
])
const boardSearchRouteNames = new Set(['board-detail', 'post-detail'])

function getSearchInput(): HTMLInputElement | null {
    if (route.name && boardSearchRouteNames.has(String(route.name))) {
        const boardSearchInput = document.getElementById(BOARD_DETAIL_SEARCH_INPUT_ID) as HTMLInputElement | null
        if (boardSearchInput) {
            return boardSearchInput
        }
    }

    const searchSelectors = [
        'input[placeholder*="search" i]',
        'input[placeholder*="검색" i]',
    ]

    for (const selector of searchSelectors) {
        const searchInput = document.querySelector(selector) as HTMLInputElement | null
        if (searchInput) {
            return searchInput
        }
    }

    return null
}

const shouldNoIndex = computed(() => {
    if (route.meta.requiresAuth || route.meta.guestOnly || route.meta.roles) {
        return true
    }

    if (route.name && noIndexRouteNames.has(String(route.name))) {
        return true
    }

    return false
})

// Global SEO Configuration
useHead({
    titleTemplate: computed(() => `%s - ${t('common.appName')}`),
    title: computed(() => t('common.appName')),
    meta: [
        {
            name: 'description',
            content: computed(() => `${t('common.appName')} - 다양한 주제의 게시판에서 인기 게시글을 발견하고, 의견을 나누는 커뮤니티. 지금 가입하고 관심 게시판을 구독하세요.`)
        },
        { property: 'og:site_name', content: computed(() => t('common.appName')) },
        { property: 'og:type', content: 'website' },
        { name: 'robots', content: computed(() => (shouldNoIndex.value ? 'noindex, nofollow' : 'index, follow')) },
        { name: 'googlebot', content: computed(() => (shouldNoIndex.value ? 'noindex, nofollow' : 'index, follow')) }
    ]
})
const layout = computed(() => {
    return route.meta.layout === 'AdminLayout' ? AdminLayout : DefaultLayout
})

const themeStore = useThemeStore()

const applySettings = (settings: Partial<UserSettings>) => {
    if (settings.theme) {
        themeStore.setTheme(settings.theme)
    }
    if (settings.language) {
        locale.value = settings.language.toLowerCase()
    }
}

const loadSettings = async () => {
    // Critical check: Do not call API if not authenticated to avoid 401 redirects for guests
    if (!authStore.isAuthenticated) return

    try {
        const settings = await queryClient.fetchQuery({
            queryKey: userSettingsQueryKey,
            queryFn: async () => {
                const { data } = await userApi.getUserSettings()
                return data.success ? unwrapApiData(data) : null
            },
        })
        if (settings) {
            applySettings(settings)
        }
    } catch (error) {
        logger.warn('Failed to load user settings:', error)
    }
}

watch(() => authStore.isAuthenticated, (newVal) => {
    if (newVal) {
        loadSettings()
    } else {
        queryClient.removeQueries({ queryKey: userSettingsQueryKey })
        // On logout, restore theme from localStorage (theme store will read it on next access)
        // Don't force LIGHT theme to preserve user's localStorage preference
        const storedTheme = localStorage.getItem('theme')
        if (storedTheme) {
            themeStore.setTheme(storedTheme === 'dark' ? 'DARK' : 'LIGHT')
        }
    }
})


const configStore = useConfigStore()

// Initialize global shortcuts
const { registerShortcut } = useGlobalShortcuts()
let stopAuthStorageSync: (() => void) | null = null

// Register search-bar focus shortcut (/)
onMounted(() => {
    stopAuthStorageSync = registerAuthStorageSync(authStore)
    configStore.fetchPublicConfigs()
    if (authStore.isAuthenticated) {
        loadSettings()
    }
    
    // Focus search bar with /
    registerShortcut({
        key: '/',
        handler: () => {
            const searchInput = getSearchInput()
            if (searchInput) {
                searchInput.focus()
                searchInput.select()
            }
        },
        description: 'Focus search bar'
    })
})

onUnmounted(() => {
    stopAuthStorageSync?.()
    stopAuthStorageSync = null
})

onErrorCaptured((err, _instance, info) => {
    logger.error('Global Error Captured:', err, info)
    toastStore.addToast(t('common.error.unknown'), 'error')
    return false // Prevent error from propagating further
})
</script>

<template>
    <ErrorBoundary>
        <NetworkStatus />
        <component :is="layout">
            <RouterView />
        </component>
        <ToastContainer />
        <GlobalConfirmModal />
        <GlobalPromptModal />
    </ErrorBoundary>
</template>

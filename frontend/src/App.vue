<script setup>
import { onMounted, watch, computed, defineAsyncComponent, onErrorCaptured } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useConfigStore } from '@/stores/config'
import ToastContainer from '@/components/common/widgets/ToastContainer.vue'
import GlobalConfirmModal from '@/components/common/widgets/GlobalConfirmModal.vue'
import GlobalPromptModal from '@/components/common/widgets/GlobalPromptModal.vue'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'
import NetworkStatus from '@/components/common/NetworkStatus.vue'
import logger from '@/utils/logger'
import type { UserSettings } from '@/types/user'

// Import layouts
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
// Async import for AdminLayout to avoid circular dependencies or load only when needed
const AdminLayout = defineAsyncComponent(() => import('@/views/admin/AdminLayout.vue'))

const route = useRoute()
const authStore = useAuthStore()
const { locale, t } = useI18n()
const toastStore = useToastStore()

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
        const { data } = await userApi.getUserSettings()
        if (data.success) {
            applySettings(data.data)
        }
    } catch (error) {
        logger.warn('Failed to load user settings:', error)
    }
}

watch(() => authStore.isAuthenticated, (newVal) => {
    if (newVal) {
        loadSettings()
    } else {
        // Reset to defaults on logout
        themeStore.setTheme('LIGHT')
    }
})


const configStore = useConfigStore()

onMounted(() => {
    configStore.fetchPublicConfigs()
    if (authStore.isAuthenticated) {
        loadSettings()
    }
})

onErrorCaptured((err, instance, info) => {
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

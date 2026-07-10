<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { registerAuthStorageSync, useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useI18n } from 'vue-i18n'
import { useConfigStore } from '@/stores/config'
import ToastContainer from '@/components/common/widgets/ToastContainer.vue'
import GlobalConfirmModal from '@/components/common/widgets/GlobalConfirmModal.vue'
import GlobalPromptModal from '@/components/common/widgets/GlobalPromptModal.vue'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'
import NetworkStatus from '@/components/common/NetworkStatus.vue'
import { useAppSearchShortcut } from '@/composables/useAppSearchShortcut'
import { useAppSeo } from '@/composables/useAppSeo'
import { useAppUserSettingsSync } from '@/composables/useAppUserSettingsSync'

// Import layouts
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
// Async import for AdminLayout to avoid circular dependencies or load only when needed
const AdminLayout = defineAsyncComponent(() => import('@/views/admin/AdminLayout.vue'))

const route = useRoute()
const authStore = useAuthStore()
const { locale, t } = useI18n()
const queryClient = useQueryClient()
const layout = computed(() => {
    return route.meta.layout === 'AdminLayout' ? AdminLayout : DefaultLayout
})

const themeStore = useThemeStore()
const configStore = useConfigStore()
let stopAuthStorageSync: (() => void) | null = null

useAppSeo(route, t)
useAppSearchShortcut(route, t)
const { loadSettings } = useAppUserSettingsSync(authStore, themeStore, queryClient, locale)

onMounted(() => {
    stopAuthStorageSync = registerAuthStorageSync(authStore)
    configStore.fetchPublicConfigs()
    if (authStore.isAuthenticated) {
        loadSettings()
    }
})

onUnmounted(() => {
    stopAuthStorageSync?.()
    stopAuthStorageSync = null
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

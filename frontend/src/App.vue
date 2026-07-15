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
import { useAppUserSettingsSync } from '@/features/user/settings/useAppUserSettingsSync'
import { useRouteFocusManagement } from '@/composables/useRouteFocusManagement'

// Import layouts
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
// Async import for AdminLayout to avoid circular dependencies or load only when needed
const AdminLayout = defineAsyncComponent(() => import('@/views/admin/AdminLayout.vue'))

const route = useRoute()
const authStore = useAuthStore()
const { t } = useI18n()
const queryClient = useQueryClient()
const layout = computed(() => {
    return route.meta.layout === 'AdminLayout' ? AdminLayout : DefaultLayout
})
const usesBareLayout = computed(() => route.matched.some(record => record.meta.layout === 'BareLayout'))

const themeStore = useThemeStore()
const configStore = useConfigStore()
let stopAuthStorageSync: (() => void) | null = null

useAppSeo(route, t)
useAppSearchShortcut(route, t)
const { loadSettings } = useAppUserSettingsSync(authStore, themeStore, queryClient)
const { routeAnnouncement } = useRouteFocusManagement(route)

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
        <p class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{ routeAnnouncement }}</p>
        <NetworkStatus />
        <RouterView v-if="usesBareLayout" />
        <component v-else :is="layout">
            <RouterView />
        </component>
        <ToastContainer />
        <GlobalConfirmModal />
        <GlobalPromptModal />
    </ErrorBoundary>
</template>

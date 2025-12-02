<script setup>
import { onMounted, watch, computed, defineAsyncComponent } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { useI18n } from 'vue-i18n'

// Import layouts
import DefaultLayout from '@/components/layout/DefaultLayout.vue'
// Async import for AdminLayout to avoid circular dependencies or load only when needed
const AdminLayout = defineAsyncComponent(() => import('@/views/admin/AdminLayout.vue'))

const route = useRoute()
const authStore = useAuthStore()
const { locale } = useI18n()

const layout = computed(() => {
  return route.meta.layout === 'AdminLayout' ? AdminLayout : DefaultLayout
})

const applySettings = (settings) => {
    if (settings.theme === 'DARK') {
        document.documentElement.classList.add('dark')
    } else {
        document.documentElement.classList.remove('dark')
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
        console.warn('Failed to load user settings:', error)
    }
}

watch(() => authStore.isAuthenticated, (newVal) => {
    if (newVal) {
        loadSettings()
    } else {
        // Reset to defaults on logout
        document.documentElement.classList.remove('dark')
        // locale.value = 'ko' // Optional: Keep last used or reset
    }
})

onMounted(() => {
    if (authStore.isAuthenticated) {
        loadSettings()
    }
})
</script>

<template>
  <component :is="layout">
    <RouterView />
  </component>
</template>

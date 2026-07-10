import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createHead } from '@unhead/vue/client'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'
import './styles/foundation.css'
import './styles/components.css'

import { configureApiStoreResolvers } from '@/api'
import { queryClient, configureQueryClientStoreResolvers } from '@/queryClient'
import { configureAuthSessionEffects, useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import { validateEnv } from '@/utils/env'
import { installClientErrorReporting } from '@/utils/clientErrorReporter'
import { registerPwaUpdatePrompt } from '@/pwa'

validateEnv()

const app = createApp(App)
const head = createHead()
const pinia = createPinia()

app.use(pinia)
app.use(head)

configureApiStoreResolvers({
    resolveToastStore: () => useToastStore(pinia),
    resolveAuthStore: () => useAuthStore(pinia),
})

configureQueryClientStoreResolvers({
    resolveToastStore: () => useToastStore(pinia),
})

configureAuthSessionEffects({
    syncThemeFromUser: (userData) => {
        if (userData?.theme) {
            useThemeStore(pinia).setTheme(userData.theme)
        }
    },
    handleSanctionedSession: () => {
        useToastStore(pinia).addToast(i18n.global.t('user.sanctioned'), 'error')
    },
})

app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.use(i18n)

installClientErrorReporting(app, {
    onVueError: () => {
        useToastStore(pinia).addToast(i18n.global.t('common.error.unknown'), 'error')
    },
})

app.mount('#app')
registerPwaUpdatePrompt(pinia, i18n.global.t)

if (import.meta.env.PROD) {
    import('@/utils/performance').then(({ reportWebVitals, logMetric }) => {
        reportWebVitals(logMetric)
    }).catch(() => {
        // Ignore optional web-vitals loading failures.
    })
}

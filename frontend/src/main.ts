import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createUnhead, headSymbol } from '@unhead/vue'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'

import { configureApiStoreResolvers } from '@/api'
import { queryClient, configureQueryClientStoreResolvers } from '@/queryClient'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { validateEnv } from '@/utils/env'

validateEnv()

const app = createApp(App)
const head = createUnhead()
const pinia = createPinia()

app.use(pinia)
app.provide(headSymbol, head)

configureApiStoreResolvers({
    resolveToastStore: () => useToastStore(pinia),
    resolveAuthStore: () => useAuthStore(pinia),
})

configureQueryClientStoreResolvers({
    resolveToastStore: () => useToastStore(pinia),
})

app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.use(i18n)

app.config.errorHandler = (err, instance, info) => {
    logger.error('Global Error Handler:', err)
    logger.error('Vue Instance:', instance)
    logger.error('Error Info:', info)
}

app.mount('#app')

if (import.meta.env.PROD) {
    import('@/utils/performance').then(({ reportWebVitals, logMetric }) => {
        reportWebVitals(logMetric)
    }).catch(() => {
        // Ignore optional web-vitals loading failures.
    })
}

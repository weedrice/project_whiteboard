import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'

import { VueQueryPlugin, QueryClient, QueryCache, MutationCache } from '@tanstack/vue-query'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)

const queryClient = new QueryClient({
    queryCache: new QueryCache({
        onError: (error, query) => {
            if (query.meta?.errorMessage === false) return

            const toastStore = useToastStore()
            const message = error.response?.data?.message || error.message || 'An error occurred'
            toastStore.addToast(message, 'error')
            logger.error('Query Error:', error)
        }
    }),
    mutationCache: new MutationCache({
        onError: (error, variables, context, mutation) => {
            if (mutation.meta?.errorMessage === false) return

            const toastStore = useToastStore()
            const message = error.response?.data?.message || error.message || 'An error occurred'
            toastStore.addToast(message, 'error')
            logger.error('Mutation Error:', error)
        }
    }),
    defaultOptions: {
        queries: {
            retry: 1,
            refetchOnWindowFocus: false
        }
    }
})

app.use(VueQueryPlugin, { queryClient })

// Global Error Handler
app.config.errorHandler = (err, instance, info) => {
    logger.error('Global Error Handler:', err)
    logger.error('Vue Instance:', instance)
    logger.error('Error Info:', info)
}

app.mount('#app')

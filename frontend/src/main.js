import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'

import { VueQueryPlugin } from '@tanstack/vue-query'
import logger from '@/utils/logger'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(VueQueryPlugin)

// Global Error Handler
app.config.errorHandler = (err, instance, info) => {
    logger.error('Global Error Handler:', err)
    logger.error('Vue Instance:', instance)
    logger.error('Error Info:', info)
}

app.mount('#app')

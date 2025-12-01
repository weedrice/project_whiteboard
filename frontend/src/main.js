import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './style.css'

import { VueQueryPlugin } from '@tanstack/vue-query'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css';
import messages from './locales/messages'

const app = createApp(App)

// Simple i18n plugin
app.config.globalProperties.$t = (key) => {
  const keys = key.split('.')
  let value = messages.ko
  for (const k of keys) {
    if (value && value[k]) {
      value = value[k]
    } else {
      return key // Fallback to key if not found
    }
  }
  return value
}

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin)

app.component('QuillEditor', QuillEditor)

app.mount('#app')

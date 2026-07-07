import { registerSW } from 'virtual:pwa-register'
import type { Pinia } from 'pinia'
import { useToastStore } from '@/stores/toast'

type Translate = (key: string) => string

export function registerPwaUpdatePrompt(pinia: Pinia, t: Translate) {
  registerSW({
    onNeedRefresh() {
      useToastStore(pinia).addToast(t('common.pwa.updateReady'), 'info', 8000)
    },
    onOfflineReady() {
      useToastStore(pinia).addToast(t('common.pwa.offlineReady'), 'success')
    },
  })
}

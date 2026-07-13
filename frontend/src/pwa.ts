import { registerSW } from 'virtual:pwa-register'
import type { Pinia } from 'pinia'
import { useToastStore } from '@/stores/toast'
import { whenPwaReloadSafe } from '@/pwaReloadGuard'

type Translate = (key: string) => string
type StopPwaUpdateChecks = () => void

export const PWA_UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000

function startPwaUpdateChecks(registration: ServiceWorkerRegistration): StopPwaUpdateChecks {
  const checkForUpdate = () => {
    if (document.visibilityState !== 'visible' || !navigator.onLine) return
    void registration.update().catch(() => undefined)
  }

  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') checkForUpdate()
  }

  const intervalId = window.setInterval(checkForUpdate, PWA_UPDATE_CHECK_INTERVAL_MS)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  return () => {
    window.clearInterval(intervalId)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
}

export function registerPwaAutoUpdate(pinia: Pinia, t: Translate): StopPwaUpdateChecks {
  const toastStore = useToastStore(pinia)
  let stopUpdateChecks: StopPwaUpdateChecks = () => undefined

  const updateServiceWorker = registerSW({
    immediate: true,
    onNeedRefresh() {
      const appliedImmediately = whenPwaReloadSafe(() => {
        void updateServiceWorker(true).catch(() => undefined)
      })
      if (!appliedImmediately) {
        toastStore.addToast(t('common.pwa.updateDeferred'), 'info', 8000)
      }
    },
    onOfflineReady() {
      toastStore.addToast(t('common.pwa.offlineReady'), 'success')
    },
    onRegisteredSW(_swUrl, registration) {
      stopUpdateChecks()
      stopUpdateChecks = registration ? startPwaUpdateChecks(registration) : () => undefined
    },
  })

  return () => stopUpdateChecks()
}

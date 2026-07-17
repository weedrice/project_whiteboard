import { registerSW } from 'virtual:pwa-register'
import type { Pinia } from 'pinia'
import { useToastStore } from '@/stores/toast'
import { whenPwaReloadSafe } from '@/pwaReloadGuard'
import { readonly, ref } from 'vue'

type Translate = (key: string) => string
type StopPwaUpdateChecks = () => void

export const PWA_UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000
export type PwaUpdateStatus = 'idle' | 'checking' | 'deferred' | 'applying' | 'failed'

const updateStatus = ref<PwaUpdateStatus>('idle')
let retryUpdate: (() => Promise<void>) | null = null

export const pwaUpdateStatus = readonly(updateStatus)
export const retryPwaUpdate = () => retryUpdate?.() ?? Promise.resolve()

function startPwaUpdateChecks(
  registration: ServiceWorkerRegistration,
  onFailure: () => void,
): StopPwaUpdateChecks {
  const checkForUpdate = async () => {
    if (document.visibilityState !== 'visible' || !navigator.onLine) return
    updateStatus.value = 'checking'
    try {
      await registration.update()
      if (updateStatus.value === 'checking') updateStatus.value = 'idle'
    } catch {
      onFailure()
    }
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
  updateStatus.value = 'idle'
  let stopUpdateChecks: StopPwaUpdateChecks = () => undefined
  let swRegistration: ServiceWorkerRegistration | undefined

  const markFailed = () => {
    updateStatus.value = 'failed'
    toastStore.addToast(t('common.pwa.updateFailed'), 'error', 8000)
  }

  const applyUpdate = async () => {
    updateStatus.value = 'applying'
    try {
      await updateServiceWorker(true)
    } catch {
      markFailed()
    }
  }

  retryUpdate = async () => {
    if (!navigator.onLine) {
      markFailed()
      return
    }
    if (updateStatus.value === 'failed' && swRegistration) {
      updateStatus.value = 'checking'
      try {
        await swRegistration.update()
      } catch {
        markFailed()
        return
      }
    }
    await applyUpdate()
  }

  const updateServiceWorker = registerSW({
    immediate: true,
    onNeedRefresh() {
      const appliedImmediately = whenPwaReloadSafe(() => {
        void applyUpdate()
      })
      if (!appliedImmediately) {
        updateStatus.value = 'deferred'
        toastStore.addToast(t('common.pwa.updateDeferred'), 'info', 8000)
      }
    },
    onOfflineReady() {
      toastStore.addToast(t('common.pwa.offlineReady'), 'success')
    },
    onRegisteredSW(_swUrl, registered) {
      stopUpdateChecks()
      if (registered) {
        // Retain the registration so a failed check can be retried by the user.
        swRegistration = registered
      }
      stopUpdateChecks = registered ? startPwaUpdateChecks(registered, markFailed) : () => undefined
    },
  })

  return () => {
    retryUpdate = null
    updateStatus.value = 'idle'
    stopUpdateChecks()
  }
}

import { effectScope, ref } from 'vue'
import type { Pinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PWA_UPDATE_CHECK_INTERVAL_MS, registerPwaAutoUpdate } from '@/pwa'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  registerSW: vi.fn(),
  updateServiceWorker: vi.fn(),
}))

vi.mock('virtual:pwa-register', () => ({
  registerSW: mocks.registerSW,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: mocks.addToast }),
}))

type RegisterOptions = {
  immediate?: boolean
  onNeedRefresh?: () => void
  onOfflineReady?: () => void
  onRegisteredSW?: (swUrl: string, registration?: ServiceWorkerRegistration) => void
}

const t = (key: string) => key
const getRegisterOptions = () => mocks.registerSW.mock.calls[0]?.[0] as RegisterOptions

describe('registerPwaAutoUpdate', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.addToast.mockReset()
    mocks.registerSW.mockReset()
    mocks.updateServiceWorker.mockReset().mockResolvedValue(undefined)
    mocks.registerSW.mockReturnValue(mocks.updateServiceWorker)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('applies a waiting service worker immediately when reload is safe', () => {
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    const options = getRegisterOptions()

    expect(options.immediate).toBe(true)
    options.onNeedRefresh?.()

    expect(mocks.updateServiceWorker).toHaveBeenCalledWith(true)
    expect(mocks.addToast).not.toHaveBeenCalledWith('common.pwa.updateDeferred', 'info', 8000)
    stop()
  })

  it('defers activation while an editor blocker is active', () => {
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    const options = getRegisterOptions()

    options.onNeedRefresh?.()

    expect(mocks.updateServiceWorker).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('common.pwa.updateDeferred', 'info', 8000)

    blocked.value = false

    expect(mocks.updateServiceWorker).toHaveBeenCalledWith(true)
    scope.stop()
    stop()
  })

  it('checks for updates periodically and when a visible tab is resumed', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const registration = {
      update: vi.fn().mockResolvedValue(undefined),
    } as unknown as ServiceWorkerRegistration
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    const options = getRegisterOptions()

    options.onRegisteredSW?.('/service-worker.js', registration)
    await vi.advanceTimersByTimeAsync(PWA_UPDATE_CHECK_INTERVAL_MS)
    expect(registration.update).toHaveBeenCalledTimes(1)

    document.dispatchEvent(new Event('visibilitychange'))
    await Promise.resolve()
    expect(registration.update).toHaveBeenCalledTimes(2)

    stop()
  })

  it('keeps the offline-ready notice', () => {
    const stop = registerPwaAutoUpdate({} as Pinia, t)

    getRegisterOptions().onOfflineReady?.()

    expect(mocks.addToast).toHaveBeenCalledWith('common.pwa.offlineReady', 'success')
    stop()
  })
})

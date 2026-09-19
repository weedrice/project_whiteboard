import { effectScope, ref } from 'vue'
import type { Pinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PWA_UPDATE_CHECK_INTERVAL_MS,
  pwaUpdateStatus,
  registerPwaAutoUpdate,
  retryPwaUpdate,
} from '@/pwa'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  registerSW: vi.fn(),
  updateServiceWorker: vi.fn(),
  reloadPage: vi.fn(),
}))

vi.mock('virtual:pwa-register', () => ({
  registerSW: mocks.registerSW,
}))

vi.mock('@/utils/pageReload', () => ({ reloadPage: mocks.reloadPage }))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: mocks.addToast }),
}))

type RegisterOptions = {
  immediate?: boolean
  onNeedRefresh?: () => void
  onNeedReload?: () => void
  onOfflineReady?: () => void
  onRegisteredSW?: (swUrl: string, registration?: ServiceWorkerRegistration) => void
}

const t = (key: string) => key
const getRegisterOptions = () => mocks.registerSW.mock.calls[0]?.[0] as RegisterOptions

describe('registerPwaAutoUpdate', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.addToast.mockReset()
    mocks.reloadPage.mockReset()
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

  it('defers another tabs activation until this tabs unsaved input is cleared', () => {
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    const options = getRegisterOptions()
    try {
      options.onNeedRefresh?.()
      options.onNeedReload?.()
      expect(pwaUpdateStatus.value).toBe('deferred')
      expect(mocks.updateServiceWorker).not.toHaveBeenCalled()
      expect(mocks.reloadPage).not.toHaveBeenCalled()

      blocked.value = false
      expect(mocks.reloadPage).toHaveBeenCalledTimes(1)
      expect(mocks.updateServiceWorker).not.toHaveBeenCalled()
    } finally {
      stop()
      scope.stop()
    }
  })

  it('rechecks input created between activation and actual reload', () => {
    const blocked = ref(false)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    const options = getRegisterOptions()
    try {
      options.onNeedRefresh?.()
      expect(mocks.updateServiceWorker).toHaveBeenCalledTimes(1)
      blocked.value = true
      options.onNeedReload?.()
      expect(mocks.reloadPage).not.toHaveBeenCalled()
      blocked.value = false
      expect(mocks.reloadPage).toHaveBeenCalledTimes(1)
    } finally {
      stop()
      scope.stop()
    }
  })

  it('reloads once when the controlling worker changes without unsaved input', () => {
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    getRegisterOptions().onNeedReload?.()
    getRegisterOptions().onNeedReload?.()
    expect(mocks.reloadPage).toHaveBeenCalledTimes(1)
    expect(mocks.updateServiceWorker).not.toHaveBeenCalled()
    stop()
  })

  it('does not reload from a deferred callback after update checks are stopped', () => {
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    getRegisterOptions().onNeedReload?.()
    stop()
    scope.stop()
    expect(mocks.reloadPage).not.toHaveBeenCalled()
    expect(pwaUpdateStatus.value).toBe('idle')
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

  it('exposes a failed update check and retries it explicitly', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const registration = {
      update: vi.fn()
        .mockRejectedValueOnce(new Error('network'))
        .mockResolvedValueOnce(undefined),
    } as unknown as ServiceWorkerRegistration
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    getRegisterOptions().onRegisteredSW?.('/service-worker.js', registration)

    await vi.advanceTimersByTimeAsync(PWA_UPDATE_CHECK_INTERVAL_MS)
    expect(pwaUpdateStatus.value).toBe('failed')
    expect(mocks.addToast).toHaveBeenCalledWith('common.pwa.updateFailed', 'error', 8000)

    await retryPwaUpdate()
    expect(registration.update).toHaveBeenCalledTimes(2)
    expect(mocks.updateServiceWorker).toHaveBeenCalledWith(true)
    expect(pwaUpdateStatus.value).toBe('applying')
    stop()
  })

  it('keeps a waiting update deferred when a failed check is retried with unsaved input', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const registration = {
      update: vi.fn()
        .mockRejectedValueOnce(new Error('network'))
        .mockResolvedValueOnce(undefined),
    } as unknown as ServiceWorkerRegistration
    const stop = registerPwaAutoUpdate({} as Pinia, t)
    const options = getRegisterOptions()
    options.onRegisteredSW?.('/service-worker.js', registration)

    try {
      options.onNeedRefresh?.()
      expect(pwaUpdateStatus.value).toBe('deferred')
      await vi.advanceTimersByTimeAsync(PWA_UPDATE_CHECK_INTERVAL_MS)
      expect(pwaUpdateStatus.value).toBe('failed')

      await retryPwaUpdate()

      expect(registration.update).toHaveBeenCalledTimes(2)
      expect(mocks.updateServiceWorker).not.toHaveBeenCalled()
      expect(pwaUpdateStatus.value).toBe('deferred')

      blocked.value = false
      expect(mocks.updateServiceWorker).toHaveBeenCalledExactlyOnceWith(true)
      expect(pwaUpdateStatus.value).toBe('applying')
    } finally {
      scope.stop()
      stop()
    }
  })

  it('keeps failed activation retryable', async () => {
    mocks.updateServiceWorker
      .mockRejectedValueOnce(new Error('activation failed'))
      .mockResolvedValueOnce(undefined)
    const stop = registerPwaAutoUpdate({} as Pinia, t)

    getRegisterOptions().onNeedRefresh?.()
    await Promise.resolve()
    expect(pwaUpdateStatus.value).toBe('failed')

    await retryPwaUpdate()
    expect(mocks.updateServiceWorker).toHaveBeenCalledTimes(2)
    stop()
  })

  it('keeps the offline-ready notice', () => {
    const stop = registerPwaAutoUpdate({} as Pinia, t)

    getRegisterOptions().onOfflineReady?.()

    expect(mocks.addToast).toHaveBeenCalledWith('common.pwa.offlineReady', 'success')
    stop()
  })
})

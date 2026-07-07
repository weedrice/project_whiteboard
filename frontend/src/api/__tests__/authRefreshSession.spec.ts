import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
    notifySessionExpired,
    resetSessionExpiredToastDebounce,
    SESSION_EXPIRED_TOAST_DEBOUNCE_MS,
    type ToastStore,
} from '../authRefreshSession'

describe('authRefreshSession', () => {
    let toastStore: ToastStore

    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-05-21T00:00:00.000Z'))
        resetSessionExpiredToastDebounce()
        toastStore = {
            addToast: vi.fn(),
        }
    })

    afterEach(() => {
        resetSessionExpiredToastDebounce()
        vi.useRealTimers()
    })

    it('debounces repeated session expired toasts and allows the next one after the window', () => {
        notifySessionExpired(toastStore, 'expired')
        notifySessionExpired(toastStore, 'expired')

        expect(toastStore.addToast).toHaveBeenCalledTimes(1)
        expect(toastStore.addToast).toHaveBeenCalledWith('expired', 'warning', 3000, 'top-center')

        vi.advanceTimersByTime(SESSION_EXPIRED_TOAST_DEBOUNCE_MS - 1)
        notifySessionExpired(toastStore, 'expired')
        expect(toastStore.addToast).toHaveBeenCalledTimes(1)

        vi.advanceTimersByTime(1)
        notifySessionExpired(toastStore, 'expired')
        expect(toastStore.addToast).toHaveBeenCalledTimes(2)
    })

    it('shows the next session expired toast immediately after debounce reset', () => {
        notifySessionExpired(toastStore, 'expired')
        resetSessionExpiredToastDebounce()
        notifySessionExpired(toastStore, 'expired again')

        expect(toastStore.addToast).toHaveBeenCalledTimes(2)
        expect(toastStore.addToast).toHaveBeenLastCalledWith('expired again', 'warning', 3000, 'top-center')
    })
})

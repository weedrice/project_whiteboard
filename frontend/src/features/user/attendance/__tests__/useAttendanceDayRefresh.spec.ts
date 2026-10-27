import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import HomeAttendancePanel from '@/components/home/HomeAttendancePanel.vue'
import { useAuthStore } from '@/stores/auth'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'
import type { User } from '@/types'
import { useAttendance } from '../useAttendance'

vi.mock('@/router', () => ({
    default: { push: vi.fn(), replace: vi.fn(), currentRoute: { value: { meta: {} } } },
}))

const originalAdapter = api.defaults.adapter
let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let client: QueryClient
let wrapper: VueWrapper | undefined
const transport = vi.fn(async (config: InternalAxiosRequestConfig) => response(config))

function response(config: InternalAxiosRequestConfig, today = new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10)) {
    return {
        config, status: 200, statusText: 'OK', headers: {},
        data: { success: true, data: {
            month: config.params?.month ?? today.slice(0, 7), today,
            checkedInToday: today === '2026-10-31', currentStreakCount: 3, days: [],
        } },
    }
}

function mountPanel() {
    wrapper = mount(HomeAttendancePanel, { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
    return wrapper
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] })
    vi.setSystemTime(new Date('2026-10-31T14:59:59Z'))
    transport.mockClear()
    transport.mockImplementation(async config => response(config))
    pinia = createPinia()
    setActivePinia(pinia)
    auth = useAuthStore(pinia)
    auth.accessToken = 'attendance-test-token'
    auth.user = { userId: 7 } as User
    persistAccessToken(auth.accessToken)
    configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
    configureApiStoreResolvers({ resolveAuthStore: () => auth, resolveToastStore: () => ({ addToast: vi.fn() }) })
    api.defaults.adapter = transport
    client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: false } } })
})

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    client.clear()
    disposePinia(pinia)
    api.defaults.adapter = originalAdapter
    configureAuthQueryScope(() => 0)
    configureApiStoreResolvers({ resolveAuthStore: () => null, resolveToastStore: () => ({ addToast: () => undefined }) })
    clearStoredAuthTokens(false)
    vi.useRealTimers()
})

describe('attendance KST day refresh with real Vue Query and Axios', () => {
    it('enables check-in at KST midnight and reloads the new month', async () => {
        const panel = mountPanel()
        await flushPromises()
        expect(panel.get('.nv-attendance-button').attributes('disabled')).toBeDefined()
        await vi.advanceTimersByTimeAsync(1000)
        await flushPromises()
        expect(transport).toHaveBeenCalledTimes(2)
        expect(panel.get('.nv-attendance-button').attributes('disabled')).toBeUndefined()
        const current = client.getQueryData(['session', 0, 'attendance', 'me', 'current', '2026-11-01'])
        expect(current).toMatchObject({ today: '2026-11-01', month: '2026-11', checkedInToday: false })
    })

    it.each(['focus', 'visibilitychange', 'pageshow'])('catches up after suspended timers on %s without repeated same-day requests', async event => {
        const panel = mountPanel()
        await flushPromises()
        vi.setSystemTime(new Date('2026-11-02T00:00:00Z'))
        const target = event === 'visibilitychange' ? document : window
        target.dispatchEvent(new Event(event))
        await flushPromises()
        expect(transport).toHaveBeenCalledTimes(2)
        expect(panel.get('.nv-attendance-button').attributes('disabled')).toBeUndefined()
        target.dispatchEvent(new Event(event))
        await flushPromises()
        expect(transport).toHaveBeenCalledTimes(2)
    })

    it('ignores a previous-day response that arrives after the new day loads', async () => {
        const pending = createDeferred<void>()
        transport.mockImplementationOnce(async config => {
            await pending.promise
            return response(config, '2026-10-31')
        })
        const panel = mountPanel()
        await flushPromises()
        await vi.advanceTimersByTimeAsync(1000)
        await flushPromises()
        expect(transport).toHaveBeenCalledTimes(2)
        pending.resolve()
        await flushPromises()
        expect(panel.get('.nv-attendance-button').attributes('disabled')).toBeUndefined()
        expect(transport.mock.calls[0]![0].signal?.aborted).toBe(true)
    })

    it('keeps midnight queries scoped when the account changes during a delayed response', async () => {
        const panel = mountPanel()
        await flushPromises()
        const pending = createDeferred<void>()
        transport.mockImplementationOnce(async config => {
            await pending.promise
            return response(config, '2026-10-31')
        })
        await vi.advanceTimersByTimeAsync(1000)
        auth.sessionGeneration += 1
        auth.user = { userId: 8 } as User
        notifyAuthSessionBoundary(auth.sessionGeneration)
        await flushPromises()
        pending.resolve()
        await flushPromises()
        expect(panel.get('.nv-attendance-button').attributes('disabled')).toBeUndefined()
        expect(transport.mock.calls.at(-1)![0].expectedSession).toEqual({ sessionGeneration: 1, userId: 8 })
        expect(client.getQueryData(['session', 1, 'attendance', 'me', 'current', '2026-11-01'])).toMatchObject({ today: '2026-11-01' })
    })

    it('allows ordinary token rotation and preserves an explicitly selected historical month', async () => {
        wrapper = mount(defineComponent({
            setup() {
                useAttendance().useMyAttendance(ref(true), ref('2026-09'))
                return () => null
            },
        }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
        await flushPromises()
        auth.accessToken = 'rotated-attendance-token'
        persistAccessToken(auth.accessToken)
        await vi.advanceTimersByTimeAsync(1000)
        await flushPromises()
        expect(transport).toHaveBeenCalledTimes(2)
        const request = transport.mock.calls[1]![0]
        expect(request.params.month).toBe('2026-09')
        expect(request.expectedSession).toEqual({ sessionGeneration: 0, userId: 7 })
        expect(request.headers.Authorization).toBe('Bearer rotated-attendance-token')
    })

    it('does not send an authenticated attendance request for guest home refresh', async () => {
        auth.accessToken = null
        auth.user = null
        clearStoredAuthTokens(false)
        const panel = mountPanel()
        await flushPromises()
        await (panel.vm as unknown as { refresh: () => unknown }).refresh()
        await flushPromises()
        expect(transport).not.toHaveBeenCalled()
        expect(panel.find('.nv-attendance-button').exists()).toBe(false)
    })

    it('does not fetch while disabled and removes timers and listeners on unmount', async () => {
        const enabled = ref(false)
        wrapper = mount(defineComponent({
            setup() {
                useAttendance().useMyAttendance(enabled)
                return () => null
            },
        }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
        await vi.advanceTimersByTimeAsync(1000)
        expect(transport).not.toHaveBeenCalled()
        enabled.value = true
        await flushPromises()
        expect(transport).toHaveBeenCalledOnce()
        expect(transport.mock.calls[0]![0].expectedSession).toEqual({ sessionGeneration: 0, userId: 7 })
        wrapper.unmount()
        wrapper = undefined
        client.clear()
        expect(vi.getTimerCount()).toBe(0)
        vi.setSystemTime(new Date('2026-11-03T00:00:00Z'))
        window.dispatchEvent(new Event('focus'))
        document.dispatchEvent(new Event('visibilitychange'))
        expect(vi.getTimerCount()).toBe(0)
        expect(transport).toHaveBeenCalledOnce()
    })
})

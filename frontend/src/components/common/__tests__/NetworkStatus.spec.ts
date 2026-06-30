import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import NetworkStatus from '../NetworkStatus.vue'

const networkState = vi.hoisted(() => ({
    isOnline: { __v_isRef: true as const, value: true },
    isOffline: { __v_isRef: true as const, value: false },
    wasOffline: { __v_isRef: true as const, value: false },
    resetWasOffline: vi.fn(),
}))

vi.mock('@/composables/useNetworkStatus', () => ({
    useNetworkStatus: () => networkState,
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => ({
            'common.network.offline': '오프라인 상태입니다.',
            'common.network.online': '인터넷 연결이 복구되었습니다.',
        })[key] ?? key,
    }),
}))

describe('NetworkStatus', () => {
    afterEach(() => {
        vi.useRealTimers()
        vi.clearAllMocks()
        networkState.isOnline.value = true
        networkState.isOffline.value = false
        networkState.wasOffline.value = false
    })

    it('announces offline state changes with status semantics', () => {
        networkState.isOnline.value = false
        networkState.isOffline.value = true
        networkState.wasOffline.value = false

        const wrapper = mount(NetworkStatus)
        const status = wrapper.get('.network-status')

        expect(status.classes()).toContain('offline')
        expect(status.attributes()).toMatchObject({
            role: 'status',
            'aria-live': 'polite',
        })
        expect(status.text()).toContain('오프라인 상태입니다.')
        expect(status.get('.network-status-icon').attributes('aria-hidden')).toBe('true')
    })

    it('announces online recovery and clears the banner after a delay', async () => {
        vi.useFakeTimers()
        networkState.isOnline.value = true
        networkState.isOffline.value = false
        networkState.wasOffline.value = true

        const wrapper = mount(NetworkStatus)

        expect(wrapper.text()).toContain('인터넷 연결이 복구되었습니다.')

        vi.advanceTimersByTime(3000)

        expect(networkState.resetWasOffline).toHaveBeenCalled()
    })
})

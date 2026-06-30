import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { useNetworkStatus } from '../useNetworkStatus'

function setNavigatorOnline(value: boolean) {
    Object.defineProperty(navigator, 'onLine', {
        configurable: true,
        value,
    })
}

function mountNetworkStatusComposable() {
    let composable!: ReturnType<typeof useNetworkStatus>

    const wrapper = mount(defineComponent({
        setup() {
            composable = useNetworkStatus()
            return () => h('div')
        },
    }))

    return {
        composable,
        wrapper,
    }
}

describe('useNetworkStatus', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
        setNavigatorOnline(true)
    })

    it('tracks offline and online recovery events', async () => {
        setNavigatorOnline(true)
        const { composable } = mountNetworkStatusComposable()

        setNavigatorOnline(false)
        window.dispatchEvent(new Event('offline'))
        await Promise.resolve()

        expect(composable.isOnline.value).toBe(false)
        expect(composable.isOffline.value).toBe(true)
        expect(composable.wasOffline.value).toBe(false)

        setNavigatorOnline(true)
        window.dispatchEvent(new Event('online'))
        await Promise.resolve()

        expect(composable.isOnline.value).toBe(true)
        expect(composable.wasOffline.value).toBe(true)

        composable.resetWasOffline()

        expect(composable.wasOffline.value).toBe(false)
    })

    it('defaults to online when navigator is unavailable', () => {
        vi.stubGlobal('navigator', undefined)

        const { composable } = mountNetworkStatusComposable()

        expect(composable.isOnline.value).toBe(true)
        expect(composable.isOffline.value).toBe(false)
    })
})

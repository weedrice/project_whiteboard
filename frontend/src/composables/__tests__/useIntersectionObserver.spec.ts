import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useIntersectionObserver } from '../useIntersectionObserver'

describe('useIntersectionObserver', () => {
    const originalIntersectionObserver = globalThis.IntersectionObserver
    const disconnect = vi.fn()
    const unobserve = vi.fn()
    const observe = vi.fn()

    class MockIntersectionObserver {
        observe = observe
        unobserve = unobserve
        disconnect = disconnect
    }

    beforeEach(() => {
        vi.clearAllMocks()
        globalThis.IntersectionObserver = MockIntersectionObserver as unknown as typeof IntersectionObserver
    })

    afterEach(() => {
        globalThis.IntersectionObserver = originalIntersectionObserver
    })

    it('disconnects observer when target ref changes to null', async () => {
        const TestComponent = defineComponent({
            setup(_, { expose }) {
                const targetRef = ref<HTMLElement | null>(null)
                useIntersectionObserver(targetRef)
                expose({ targetRef })
                return () => null
            }
        })
        const wrapper = mount(TestComponent)

        ;(wrapper.vm as unknown as { targetRef: HTMLElement | null }).targetRef = document.createElement('div')
        await nextTick()
        expect(observe).toHaveBeenCalledTimes(1)

        ;(wrapper.vm as unknown as { targetRef: HTMLElement | null }).targetRef = null
        await nextTick()

        expect(disconnect).toHaveBeenCalledTimes(1)
        wrapper.unmount()
    })
})

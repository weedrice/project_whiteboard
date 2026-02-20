import { markRaw, reactive, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuthLayout from '../AuthLayout.vue'

const mockRoute = reactive<{ name: string | null }>({
    name: 'login',
})

vi.mock('vue-router', () => ({
    useRoute: () => mockRoute,
}))

const resizeObserverMocks = vi.hoisted(() => {
    const observe = vi.fn()
    const disconnect = vi.fn()
    let callback: ((entries: ResizeObserverEntry[]) => void) | null = null

    class MockResizeObserver {
        constructor(cb: (entries: ResizeObserverEntry[]) => void) {
            callback = cb
        }

        observe = observe
        disconnect = disconnect
    }

    return {
        observe,
        disconnect,
        getCallback: () => callback,
        MockResizeObserver,
    }
})

describe('AuthLayout', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockRoute.name = 'login'
        ;(globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = resizeObserverMocks.MockResizeObserver
    })

    it('observes content area and updates card height from ResizeObserver', async () => {
        const wrapper = mount(AuthLayout, {
            global: {
                stubs: {
                    RouterView: {
                        template: '<div><slot :Component="routeComponent" /></div>',
                        data() {
                            return {
                                routeComponent: markRaw({ template: '<div class="route-component">Route</div>' }),
                            }
                        },
                    },
                },
            },
        })

        await nextTick()
        await nextTick()
        expect(resizeObserverMocks.observe).toHaveBeenCalledTimes(1)

        const callback = resizeObserverMocks.getCallback()
        callback?.([])
        callback?.([{ contentRect: { height: 320 } } as ResizeObserverEntry])
        await nextTick()

        expect(wrapper.find('.auth-card').attributes('style')).toContain('min-height: 320px')

        wrapper.unmount()
        expect(resizeObserverMocks.disconnect).toHaveBeenCalledTimes(1)
    })

    it('changes transition name based on route order', async () => {
        const wrapper = mount(AuthLayout, {
            global: {
                stubs: {
                    RouterView: {
                        template: '<div><slot :Component="routeComponent" /></div>',
                        data() {
                            return {
                                routeComponent: markRaw({ template: '<div class="route-component">Route</div>' }),
                            }
                        },
                    },
                },
            },
        })

        expect(wrapper.find('transition-stub').attributes('name')).toBe('slide-left')

        mockRoute.name = 'signup'
        await nextTick()
        expect(wrapper.find('transition-stub').attributes('name')).toBe('slide-left')

        mockRoute.name = 'login'
        await nextTick()
        expect(wrapper.find('transition-stub').attributes('name')).toBe('slide-right')

        // Unknown route names should fall back to depth 0.
        mockRoute.name = null
        await nextTick()
        expect(wrapper.find('transition-stub').attributes('name')).toBe('slide-right')

        mockRoute.name = 'unknown-route'
        await nextTick()
        expect(wrapper.find('transition-stub').attributes('name')).toBe('slide-left')
    })

    it('skips observing when component is unmounted before nextTick observer callback', async () => {
        const wrapper = mount(AuthLayout, {
            global: {
                stubs: {
                    RouterView: {
                        template: '<div><slot :Component="routeComponent" /></div>',
                        data() {
                            return {
                                routeComponent: markRaw({ template: '<div class="route-component">Route</div>' }),
                            }
                        },
                    },
                },
            },
        })

        wrapper.unmount()
        await nextTick()

        expect(resizeObserverMocks.observe).not.toHaveBeenCalled()
    })
})

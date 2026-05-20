import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAnchoredPopover } from '../useAnchoredPopover'

const createHarness = () => {
    let composable: ReturnType<typeof useAnchoredPopover> | null = null
    const anchor = document.createElement('button')
    const popover = document.createElement('div')
    const isOpen = ref(false)

    anchor.getBoundingClientRect = vi.fn(() => ({
        left: 100,
        top: 80,
        right: 140,
        bottom: 112,
        width: 40,
        height: 32,
        x: 100,
        y: 80,
        toJSON: () => ({}),
    }))
    popover.getBoundingClientRect = vi.fn(() => ({
        left: 0,
        top: 0,
        right: 240,
        bottom: 180,
        width: 240,
        height: 180,
        x: 0,
        y: 0,
        toJSON: () => ({}),
    }))

    const Harness = defineComponent({
        setup() {
            const popoverRef = ref<HTMLElement | null>(popover)
            composable = useAnchoredPopover(popoverRef, isOpen)
            return () => h('div')
        },
    })

    const wrapper = mount(Harness)
    if (!composable) throw new Error('Composable was not initialized')
    return {
        anchor,
        isOpen,
        wrapper,
        composable: composable as ReturnType<typeof useAnchoredPopover>,
    }
}

describe('useAnchoredPopover', () => {
    const originalMatchMedia = window.matchMedia
    const originalRequestAnimationFrame = window.requestAnimationFrame
    const originalCancelAnimationFrame = window.cancelAnimationFrame

    beforeEach(() => {
        vi.spyOn(window, 'addEventListener')
        vi.spyOn(window, 'removeEventListener')
        Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 })
        Object.defineProperty(window, 'innerHeight', { configurable: true, value: 768 })
        window.matchMedia = vi.fn(() => ({ matches: false }) as MediaQueryList)
        window.requestAnimationFrame = vi.fn((callback: FrameRequestCallback) => {
            callback(0)
            return 1
        })
        window.cancelAnimationFrame = vi.fn()
    })

    afterEach(() => {
        vi.restoreAllMocks()
        window.matchMedia = originalMatchMedia
        window.requestAnimationFrame = originalRequestAnimationFrame
        window.cancelAnimationFrame = originalCancelAnimationFrame
    })

    it('attaches viewport listeners only while open and removes them on close', async () => {
        const { isOpen, wrapper } = createHarness()

        expect(window.addEventListener).not.toHaveBeenCalledWith('resize', expect.any(Function))

        isOpen.value = true
        await nextTick()

        expect(window.addEventListener).toHaveBeenCalledWith('resize', expect.any(Function))
        expect(window.addEventListener).toHaveBeenCalledWith('scroll', expect.any(Function), true)

        isOpen.value = false
        await nextTick()

        expect(window.removeEventListener).toHaveBeenCalledWith('resize', expect.any(Function))
        expect(window.removeEventListener).toHaveBeenCalledWith('scroll', expect.any(Function), true)

        wrapper.unmount()
    })

    it('positions by anchor on desktop and uses centered fallback on mobile', async () => {
        const { anchor, composable, isOpen, wrapper } = createHarness()
        composable.setAnchor(anchor)

        isOpen.value = true
        await nextTick()
        await composable.updatePosition()

        expect(composable.popoverStyle.value.transform).toBe('translateX(-50%)')
        expect(composable.popoverStyle.value.left).toBe('132px')

        vi.mocked(window.matchMedia).mockReturnValueOnce({ matches: true } as MediaQueryList)
        await composable.updatePosition()

        expect(composable.popoverStyle.value.transform).toBe('translate(-50%, -50%)')
        expect(composable.popoverStyle.value.left).toBe('512px')

        wrapper.unmount()
    })

    it('coalesces scroll and resize updates with requestAnimationFrame', async () => {
        const { isOpen, wrapper } = createHarness()

        isOpen.value = true
        await nextTick()
        vi.mocked(window.requestAnimationFrame).mockImplementation(() => 2)

        window.dispatchEvent(new Event('scroll'))
        window.dispatchEvent(new Event('resize'))

        expect(window.requestAnimationFrame).toHaveBeenCalledTimes(1)

        wrapper.unmount()
    })
})

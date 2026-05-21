import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useUserMenuPosition } from '../useUserMenuPosition'

describe('useUserMenuPosition', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 })
        Object.defineProperty(window, 'innerHeight', { configurable: true, value: 768 })
        Object.defineProperty(window, 'scrollX', { configurable: true, value: 0 })
        Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 })
    })

    it('preserves left-edge positioning and cleans viewport listeners', async () => {
        const addEventListener = vi.spyOn(window, 'addEventListener')
        const removeEventListener = vi.spyOn(window, 'removeEventListener')
        const button = document.createElement('button')
        const dropdown = document.createElement('div')

        button.getBoundingClientRect = vi.fn(() => ({
            x: 20,
            y: 30,
            width: 80,
            height: 20,
            top: 30,
            right: 100,
            bottom: 50,
            left: 20,
            toJSON: () => undefined,
        }))
        Object.defineProperty(dropdown, 'offsetWidth', { configurable: true, value: 224 })
        Object.defineProperty(dropdown, 'offsetHeight', { configurable: true, value: 120 })

        const isOpen = ref(false)
        let positionResult: ReturnType<typeof useUserMenuPosition> | undefined
        const wrapper = mount(defineComponent({
            setup() {
                positionResult = useUserMenuPosition(ref(button), ref(dropdown), isOpen)
                return () => h('div')
            },
        }))

        isOpen.value = true
        await nextTick()
        await nextTick()

        expect(positionResult?.dropdownStyle.value).toEqual({ top: '55px', left: '20px' })
        expect(addEventListener).toHaveBeenCalledWith('resize', expect.any(Function))
        expect(addEventListener).toHaveBeenCalledWith('scroll', expect.any(Function), true)

        isOpen.value = false
        await nextTick()
        expect(removeEventListener).toHaveBeenCalledWith('resize', expect.any(Function))
        expect(removeEventListener).toHaveBeenCalledWith('scroll', expect.any(Function), true)

        wrapper.unmount()
    })
})

import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import { useFocusTrap } from '../useFocusTrap'

const createHarness = () => {
    let composable: ReturnType<typeof useFocusTrap> | null = null
    const isActive = ref(true)
    const Harness = defineComponent({
        setup() {
            const containerRef = ref<HTMLElement | null>(null)
            composable = useFocusTrap(containerRef, isActive)
            return () => h('div', { ref: containerRef }, [
                h('button', { id: 'first' }, 'First'),
                h('button', { id: 'hidden', style: 'display: none;' }, 'Hidden'),
                h('input', { id: 'second' }),
            ])
        },
    })

    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()

    const wrapper = mount(Harness, { attachTo: document.body })
    if (!composable) throw new Error('Composable was not initialized')
    return {
        trigger,
        isActive,
        wrapper,
        composable: composable as ReturnType<typeof useFocusTrap>,
    }
}

describe('useFocusTrap', () => {
    afterEach(() => {
        document.body.innerHTML = ''
    })

    it('returns visible focusable elements and moves focus to the first one', () => {
        const { composable, wrapper } = createHarness()

        const focusableElements = composable.getFocusableElements()

        expect(focusableElements.map((element) => element.id)).toEqual(['first', 'second'])

        composable.trapFocus()

        expect(document.activeElement).toBe(wrapper.get('#first').element)
    })

    it('loops Tab and Shift+Tab while active', () => {
        const { composable, wrapper } = createHarness()
        const first = wrapper.get('#first').element as HTMLElement
        const second = wrapper.get('#second').element as HTMLElement
        composable.trapFocus()

        second.focus()
        const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
        document.dispatchEvent(tabEvent)

        expect(tabEvent.defaultPrevented).toBe(true)
        expect(document.activeElement).toBe(first)

        const shiftTabEvent = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true })
        document.dispatchEvent(shiftTabEvent)

        expect(shiftTabEvent.defaultPrevented).toBe(true)
        expect(document.activeElement).toBe(second)
    })

    it('does not trap focus while inactive and restores the previous focus target', () => {
        const { composable, isActive, trigger, wrapper } = createHarness()
        const second = wrapper.get('#second').element as HTMLElement
        composable.trapFocus()
        isActive.value = false

        second.focus()
        const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
        document.dispatchEvent(tabEvent)

        expect(tabEvent.defaultPrevented).toBe(false)

        composable.restoreFocus()

        expect(document.activeElement).toBe(trigger)
    })
})

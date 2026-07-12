import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  numberedDropdownKeyToIndex,
  useNumberedDropdownKeyboard,
} from '../useNumberedDropdownKeyboard'

const dispatchKey = (key: string) => {
  const event = new KeyboardEvent('keydown', {
    key,
    bubbles: true,
    cancelable: true,
  })
  document.dispatchEvent(event)
  return event
}

const mountedWrappers: VueWrapper[] = []

const createHarness = () => {
  const isOpen = ref(true)
  const items = ref(['first', 'second', 'third', 'fourth', 'fifth', 'sixth', 'seventh', 'eighth', 'ninth', 'tenth'])
  const onClose = vi.fn()
  const onSelect = vi.fn()

  const Harness = defineComponent({
    setup() {
      useNumberedDropdownKeyboard({
        isOpen,
        items,
        onClose,
        onSelect,
      })
      return () => h('div')
    },
  })

  const wrapper = mount(Harness)
  mountedWrappers.push(wrapper)

  return {
    isOpen,
    items,
    onClose,
    onSelect,
    wrapper,
  }
}

describe('useNumberedDropdownKeyboard', () => {
  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => {
      wrapper.unmount()
    })
    vi.restoreAllMocks()
  })

  it('maps number keys to dropdown item indexes', () => {
    expect(numberedDropdownKeyToIndex('1')).toBe(0)
    expect(numberedDropdownKeyToIndex('9')).toBe(8)
    expect(numberedDropdownKeyToIndex('0')).toBe(9)
    expect(numberedDropdownKeyToIndex('Escape')).toBeNull()
  })

  it('selects items with number keys while open', () => {
    const { onSelect } = createHarness()

    const firstEvent = dispatchKey('1')
    const tenthEvent = dispatchKey('0')

    expect(firstEvent.defaultPrevented).toBe(true)
    expect(tenthEvent.defaultPrevented).toBe(true)
    expect(onSelect).toHaveBeenNthCalledWith(1, 'first', 0)
    expect(onSelect).toHaveBeenNthCalledWith(2, 'tenth', 9)
  })

  it('closes with Escape while open', () => {
    const { onClose } = createHarness()

    const event = dispatchKey('Escape')

    expect(event.defaultPrevented).toBe(true)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('ignores keys while closed or outside the current item range', () => {
    const { isOpen, items, onClose, onSelect } = createHarness()

    items.value = ['first']
    const outOfRangeEvent = dispatchKey('2')
    isOpen.value = false
    const closedEvent = dispatchKey('1')
    dispatchKey('Escape')

    expect(outOfRangeEvent.defaultPrevented).toBe(false)
    expect(closedEvent.defaultPrevented).toBe(false)
    expect(onSelect).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('removes the document key listener after unmount', () => {
    const { onSelect, wrapper } = createHarness()

    wrapper.unmount()
    dispatchKey('1')

    expect(onSelect).not.toHaveBeenCalled()
  })

  it('moves menu focus with arrow keys and restores the trigger on Escape', async () => {
    const isOpen = ref(true)
    const onClose = vi.fn(() => { isOpen.value = false })

    const Harness = defineComponent({
      setup() {
        const { handleMenuKeyDown } = useNumberedDropdownKeyboard({
          isOpen,
          items: ['first', 'second', 'third'],
          onClose,
          onSelect: vi.fn(),
          getMenu: () => document.getElementById('test-menu'),
          getTrigger: () => document.getElementById('test-trigger'),
        })
        return () => h('div', [
          h('button', { id: 'test-trigger' }, 'Open'),
          isOpen.value
            ? h('div', { id: 'test-menu', role: 'menu', onKeydown: handleMenuKeyDown }, [
              h('button', { role: 'menuitem' }, 'First'),
              h('button', { role: 'menuitem' }, 'Second'),
              h('button', { role: 'menuitem' }, 'Third'),
            ])
            : null,
        ])
      },
    })

    const wrapper = mount(Harness, { attachTo: document.body })
    mountedWrappers.push(wrapper)
    await nextTick()
    await nextTick()

    let menuItems = wrapper.findAll('[role="menuitem"]')
    expect(document.activeElement).toBe(menuItems[0].element)

    await menuItems[0].trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(menuItems[1].element)

    await menuItems[1].trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(menuItems[2].element)

    await menuItems[2].trigger('keydown', { key: 'Escape' })
    await nextTick()
    menuItems = wrapper.findAll('[role="menuitem"]')
    expect(menuItems).toHaveLength(0)
    expect(onClose).toHaveBeenCalledOnce()
    expect(document.activeElement).toBe(wrapper.get('#test-trigger').element)
  })
})

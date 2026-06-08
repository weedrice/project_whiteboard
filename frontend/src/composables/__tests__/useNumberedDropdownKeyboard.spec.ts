import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
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
})

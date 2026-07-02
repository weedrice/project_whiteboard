import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useKeyboardNavigation } from '../useKeyboardNavigation'

function createKeyboardEvent(key: string) {
  return {
    key,
    preventDefault: vi.fn(),
  } as unknown as KeyboardEvent
}

describe('useKeyboardNavigation', () => {
  it('runs escape handling even when there are no items', () => {
    const onEscape = vi.fn()
    const navigation = useKeyboardNavigation(ref<string[]>([]), { onEscape })
    const event = createKeyboardEvent('Escape')

    navigation.handleKeyDown(event)

    expect(event.preventDefault).toHaveBeenCalledTimes(1)
    expect(onEscape).toHaveBeenCalledTimes(1)
  })

  it('ignores non-escape navigation keys when there are no items', () => {
    const onSelect = vi.fn()
    const navigation = useKeyboardNavigation(ref<string[]>([]), { onSelect, enableNumberKeys: true })
    const event = createKeyboardEvent('ArrowDown')

    navigation.handleKeyDown(event)

    expect(event.preventDefault).not.toHaveBeenCalled()
    expect(onSelect).not.toHaveBeenCalled()
    expect(navigation.selectedIndex.value).toBe(-1)
  })

  it('loops with arrow keys and selects the active item', () => {
    const onSelect = vi.fn()
    const navigation = useKeyboardNavigation(ref(['a', 'b']), { onSelect })

    navigation.handleKeyDown(createKeyboardEvent('ArrowDown'))
    navigation.handleKeyDown(createKeyboardEvent('ArrowDown'))
    navigation.handleKeyDown(createKeyboardEvent('Enter'))

    expect(navigation.selectedIndex.value).toBe(1)
    expect(onSelect).toHaveBeenCalledWith(1)

    navigation.handleKeyDown(createKeyboardEvent('ArrowDown'))
    expect(navigation.selectedIndex.value).toBe(0)
  })

  it('selects items with enabled number keys', () => {
    const onSelect = vi.fn()
    const navigation = useKeyboardNavigation(ref(['a', 'b', 'c']), {
      onSelect,
      enableNumberKeys: true,
    })
    const event = createKeyboardEvent('2')

    navigation.handleKeyDown(event)

    expect(event.preventDefault).toHaveBeenCalledTimes(1)
    expect(onSelect).toHaveBeenCalledWith(1)
  })
})

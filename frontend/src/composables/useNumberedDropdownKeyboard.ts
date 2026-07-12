import { nextTick, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useEventListener } from '@/composables/useEventListener'

type NumberedDropdownKeyboardOptions<T> = {
  isOpen: MaybeRefOrGetter<boolean>
  items: MaybeRefOrGetter<T[]>
  onClose: () => void
  onSelect: (item: T, index: number) => void
  getMenu?: () => HTMLElement | null
  getTrigger?: () => HTMLElement | null
}

export const numberedDropdownKeyToIndex = (key: string): number | null => {
  if (!/^\d$/.test(key)) {
    return null
  }

  return key === '0' ? 9 : Number.parseInt(key, 10) - 1
}

export const useNumberedDropdownKeyboard = <T>(options: NumberedDropdownKeyboardOptions<T>) => {
  const handleKeyDown = (event: KeyboardEvent) => {
    if (!toValue(options.isOpen)) {
      return
    }

    if (event.key === 'Escape' && !options.getMenu) {
      event.preventDefault()
      options.onClose()
      return
    }

    const index = numberedDropdownKeyToIndex(event.key)
    const items = toValue(options.items)

    if (index === null || index < 0 || index >= items.length) {
      return
    }

    event.preventDefault()
    options.onSelect(items[index], index)
  }

  useEventListener(() => document, 'keydown', handleKeyDown)

  const getMenuItems = () => {
    const menu = options.getMenu?.()
    if (!menu) return []
    return Array.from(menu.querySelectorAll<HTMLElement>('[role="menuitem"]'))
      .filter((item) => !item.hasAttribute('disabled') && item.getAttribute('aria-disabled') !== 'true')
  }

  const focusTrigger = () => {
    void nextTick(() => options.getTrigger?.()?.focus())
  }

  const handleMenuKeyDown = (event: KeyboardEvent) => {
    if (!toValue(options.isOpen)) return

    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      options.onClose()
      focusTrigger()
      return
    }

    if (event.key === 'Tab') {
      options.onClose()
      return
    }

    const menuItems = getMenuItems()
    if (menuItems.length === 0) return

    const currentIndex = menuItems.indexOf(document.activeElement as HTMLElement)
    let nextIndex: number | null = null

    if (event.key === 'ArrowDown') {
      nextIndex = currentIndex < 0 || currentIndex === menuItems.length - 1 ? 0 : currentIndex + 1
    } else if (event.key === 'ArrowUp') {
      nextIndex = currentIndex <= 0 ? menuItems.length - 1 : currentIndex - 1
    } else if (event.key === 'Home') {
      nextIndex = 0
    } else if (event.key === 'End') {
      nextIndex = menuItems.length - 1
    }

    if (nextIndex === null) return
    event.preventDefault()
    menuItems[nextIndex]?.focus()
  }

  if (options.getMenu) {
    watch(() => toValue(options.isOpen), (isOpen) => {
      if (!isOpen) return
      void nextTick(() => getMenuItems()[0]?.focus())
    }, { immediate: true })
  }

  return {
    handleKeyDown,
    handleMenuKeyDown,
  }
}

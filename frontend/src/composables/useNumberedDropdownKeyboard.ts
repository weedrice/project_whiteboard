import { toValue, type MaybeRefOrGetter } from 'vue'
import { useEventListener } from '@/composables/useEventListener'

type NumberedDropdownKeyboardOptions<T> = {
  isOpen: MaybeRefOrGetter<boolean>
  items: MaybeRefOrGetter<T[]>
  onClose: () => void
  onSelect: (item: T, index: number) => void
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

    if (event.key === 'Escape') {
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

  return {
    handleKeyDown,
  }
}

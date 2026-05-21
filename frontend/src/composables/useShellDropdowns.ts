import { ref } from 'vue'
import type { DropdownItem, DropdownType } from '@/stores/keyboard'

interface ShellKeyboardStore {
  closeDropdown: () => void
  setOpenDropdown: (name: DropdownType, items?: DropdownItem[]) => void
}

export function useShellDropdowns(keyboardStore: ShellKeyboardStore) {
  const isNotificationOpen = ref(false)
  const activeDropdown = ref<DropdownType>(null)

  const closeAllDropdowns = () => {
    isNotificationOpen.value = false
    activeDropdown.value = null
    keyboardStore.closeDropdown()
  }

  const toggleNotification = () => {
    if (isNotificationOpen.value) {
      closeAllDropdowns()
      return
    }

    closeAllDropdowns()
    isNotificationOpen.value = true
    activeDropdown.value = 'notification'
    keyboardStore.setOpenDropdown('notification', [])
  }

  const setActiveDropdown = (name: Exclude<DropdownType, null>) => {
    if (activeDropdown.value === name) {
      closeAllDropdowns()
      return
    }

    isNotificationOpen.value = false
    activeDropdown.value = name
  }

  const handleClickOutside = () => {
    if (activeDropdown.value || isNotificationOpen.value) {
      closeAllDropdowns()
    }
  }

  return {
    isNotificationOpen,
    activeDropdown,
    closeAllDropdowns,
    toggleNotification,
    setActiveDropdown,
    handleClickOutside
  }
}

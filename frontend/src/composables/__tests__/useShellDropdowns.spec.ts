import { describe, expect, it, vi } from 'vitest'
import { useShellDropdowns } from '@/composables/useShellDropdowns'

function createDropdowns() {
  const keyboardStore = {
    closeDropdown: vi.fn(),
    setOpenDropdown: vi.fn(),
  }
  return { dropdowns: useShellDropdowns(keyboardStore), keyboardStore }
}

describe('useShellDropdowns', () => {
  it('keeps an open dropdown for interactions inside its trigger or panel root', () => {
    const { dropdowns, keyboardStore } = createDropdowns()
    const root = document.createElement('div')
    root.setAttribute('data-shell-dropdown-root', '')
    const retryButton = document.createElement('button')
    root.appendChild(retryButton)
    document.body.appendChild(root)
    dropdowns.setActiveDropdown('all')

    retryButton.addEventListener('click', dropdowns.handleClickOutside)
    retryButton.click()

    expect(dropdowns.activeDropdown.value).toBe('all')
    expect(keyboardStore.closeDropdown).not.toHaveBeenCalled()
    root.remove()
  })

  it('closes an open dropdown for a true outside click', () => {
    const { dropdowns, keyboardStore } = createDropdowns()
    const outside = document.createElement('button')
    document.body.appendChild(outside)
    dropdowns.toggleNotification()

    outside.addEventListener('click', dropdowns.handleClickOutside)
    outside.click()

    expect(dropdowns.isNotificationOpen.value).toBe(false)
    expect(dropdowns.activeDropdown.value).toBeNull()
    expect(keyboardStore.closeDropdown).toHaveBeenCalledTimes(2)
    outside.remove()
  })
})

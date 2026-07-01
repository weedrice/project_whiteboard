import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { slashActions } from '@/components/board/editor/postEditorOptions'
import { usePostEditorSlashMenu } from '../usePostEditorSlashMenu'

describe('usePostEditorSlashMenu', () => {
  it('opens, toggles and bounds slash action selection', () => {
    const showSlashMenu = ref(false)
    const slashPosition = {
      setAnchor: vi.fn(),
      clearAnchor: vi.fn(),
    }
    const closeFloatingMenus = vi.fn()
    const anchor = document.createElement('button')

    const menu = usePostEditorSlashMenu({
      showSlashMenu,
      slashPosition,
      closeFloatingMenus,
    })

    menu.openSlashMenu(anchor)
    expect(showSlashMenu.value).toBe(true)
    expect(closeFloatingMenus).toHaveBeenCalledOnce()
    expect(slashPosition.setAnchor).toHaveBeenCalledWith(anchor)

    menu.setSlashSelection(999)
    expect(menu.slashActiveIndex.value).toBe(slashActions.length - 1)
    menu.moveSlashSelection(1)
    expect(menu.slashActiveIndex.value).toBe(0)

    menu.toggleSlashMenu(anchor)
    expect(showSlashMenu.value).toBe(false)
  })
})

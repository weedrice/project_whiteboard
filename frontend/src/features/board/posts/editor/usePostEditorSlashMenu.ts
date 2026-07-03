import { ref, type Ref } from 'vue'
import { slashActions } from '@/components/board/editor/postEditorOptions'

interface AnchorPositionController {
  setAnchor(anchor?: HTMLElement): void
  clearAnchor(): void
}

interface UsePostEditorSlashMenuOptions {
  showSlashMenu: Ref<boolean>
  slashPosition: AnchorPositionController
  closeFloatingMenus: () => void
}

export function usePostEditorSlashMenu({
  showSlashMenu,
  slashPosition,
  closeFloatingMenus,
}: UsePostEditorSlashMenuOptions) {
  const slashActiveIndex = ref(0)

  function openSlashMenu(anchor?: HTMLElement) {
    closeFloatingMenus()
    slashPosition.setAnchor(anchor)
    slashActiveIndex.value = 0
    showSlashMenu.value = true
  }

  function toggleSlashMenu(anchor?: HTMLElement) {
    if (showSlashMenu.value) {
      showSlashMenu.value = false
      return
    }
    openSlashMenu(anchor)
  }

  function moveSlashSelection(direction: 1 | -1) {
    slashActiveIndex.value = (slashActiveIndex.value + direction + slashActions.length) % slashActions.length
  }

  function setSlashSelection(index: number) {
    slashActiveIndex.value = Math.max(0, Math.min(slashActions.length - 1, index))
  }

  return {
    slashActiveIndex,
    openSlashMenu,
    toggleSlashMenu,
    moveSlashSelection,
    setSlashSelection,
  }
}

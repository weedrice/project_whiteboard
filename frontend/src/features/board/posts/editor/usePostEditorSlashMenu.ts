import { ref, type Ref } from 'vue'
import { slashActions, type SlashAction } from '@/components/board/editor/postEditorOptions'

interface AnchorPositionController {
  setAnchor(anchor?: HTMLElement): void
  clearAnchor(): void
}

interface UsePostEditorSlashMenuOptions {
  showSlashMenu: Ref<boolean>
  slashPosition: AnchorPositionController
  closeFloatingMenus: () => void
  actions?: Readonly<Ref<SlashAction[]>>
}

export function usePostEditorSlashMenu({
  showSlashMenu,
  slashPosition,
  closeFloatingMenus,
  actions,
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
    const count = actions?.value.length ?? slashActions.length
    slashActiveIndex.value = count ? (slashActiveIndex.value + direction + count) % count : 0
  }

  function setSlashSelection(index: number) {
    slashActiveIndex.value = Math.max(0, Math.min((actions?.value.length ?? slashActions.length) - 1, index))
  }

  return {
    slashActiveIndex,
    openSlashMenu,
    toggleSlashMenu,
    moveSlashSelection,
    setSlashSelection,
  }
}

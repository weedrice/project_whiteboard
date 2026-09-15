import { ref } from 'vue'
import { useAnchoredPopover } from '@/composables/useAnchoredPopover'
import { usePopoverFocus } from '@/composables/usePopoverFocus'

export function usePostEditorPopovers() {
  const showColorPanel = ref(false)
  const showLinkPopover = ref(false)
  const showTablePopover = ref(false)
  const showSlashMenu = ref(false)

  const slashPopoverRef = ref<HTMLElement | null>(null)
  const colorPanelRef = ref<HTMLElement | null>(null)
  const linkPopoverRef = ref<HTMLElement | null>(null)
  const tablePopoverRef = ref<HTMLElement | null>(null)
  const slashPosition = useAnchoredPopover(slashPopoverRef, showSlashMenu)
  const colorPosition = useAnchoredPopover(colorPanelRef, showColorPanel)
  const linkPosition = useAnchoredPopover(linkPopoverRef, showLinkPopover)
  const tablePosition = useAnchoredPopover(tablePopoverRef, showTablePopover)

  const { isTopDialog: isSlashDialogTop } = usePopoverFocus(
    slashPopoverRef,
    showSlashMenu,
    () => { showSlashMenu.value = false },
    () => slashPosition.anchorElement.value,
  )
  const { isTopDialog: isColorDialogTop } = usePopoverFocus(
    colorPanelRef,
    showColorPanel,
    () => { showColorPanel.value = false },
    () => colorPosition.anchorElement.value,
  )
  const { isTopDialog: isLinkDialogTop } = usePopoverFocus(
    linkPopoverRef,
    showLinkPopover,
    () => { showLinkPopover.value = false },
    () => linkPosition.anchorElement.value,
  )
  const { isTopDialog: isTableDialogTop } = usePopoverFocus(
    tablePopoverRef,
    showTablePopover,
    () => { showTablePopover.value = false },
    () => tablePosition.anchorElement.value,
  )

  const closeFloatingMenus = () => {
    showSlashMenu.value = false
    showColorPanel.value = false
    colorPosition.clearAnchor()
  }

  return {
    showColorPanel,
    showLinkPopover,
    showTablePopover,
    showSlashMenu,
    slashPopoverRef,
    colorPanelRef,
    linkPopoverRef,
    tablePopoverRef,
    isSlashDialogTop,
    isColorDialogTop,
    isLinkDialogTop,
    isTableDialogTop,
    slashPosition,
    colorPosition,
    linkPosition,
    tablePosition,
    closeFloatingMenus,
  }
}

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
  const colorTriggerElement = ref<HTMLElement | null>(null)

  usePopoverFocus(slashPopoverRef, showSlashMenu)
  usePopoverFocus(colorPanelRef, showColorPanel)
  usePopoverFocus(linkPopoverRef, showLinkPopover)
  usePopoverFocus(tablePopoverRef, showTablePopover)

  const slashPosition = useAnchoredPopover(slashPopoverRef, showSlashMenu)
  const colorPosition = useAnchoredPopover(colorPanelRef, showColorPanel)
  const linkPosition = useAnchoredPopover(linkPopoverRef, showLinkPopover)
  const tablePosition = useAnchoredPopover(tablePopoverRef, showTablePopover)

  const closeFloatingMenus = () => {
    showSlashMenu.value = false
    showColorPanel.value = false
    colorPosition.clearAnchor()
    colorTriggerElement.value = null
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
    colorTriggerElement,
    slashPosition,
    colorPosition,
    linkPosition,
    tablePosition,
    closeFloatingMenus,
  }
}

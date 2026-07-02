import type { Ref } from 'vue'
import type { Editor } from '@tiptap/vue-3'

interface ColorPanelPosition {
  setAnchor: (anchor?: HTMLElement) => void
  clearAnchor: () => void
}

interface UsePostEditorColorPanelOptions {
  editor: Ref<Editor | null | undefined>
  showColorPanel: Ref<boolean>
  showSlashMenu: Ref<boolean>
  slashPosition: ColorPanelPosition
  colorPosition: ColorPanelPosition
  colorTriggerElement: Ref<HTMLElement | null>
}

export function usePostEditorColorPanel({
  editor,
  showColorPanel,
  showSlashMenu,
  slashPosition,
  colorPosition,
  colorTriggerElement,
}: UsePostEditorColorPanelOptions) {
  function closeColorPanel(focusTarget = colorTriggerElement.value) {
    showColorPanel.value = false
    colorPosition.clearAnchor()
    colorTriggerElement.value = null
    if (focusTarget instanceof HTMLElement) {
      focusTarget.focus()
    }
  }

  function toggleColorPanel(anchor?: HTMLElement) {
    if (showColorPanel.value) {
      closeColorPanel(anchor)
      return
    }
    showSlashMenu.value = false
    slashPosition.clearAnchor()
    colorTriggerElement.value = anchor ?? null
    colorPosition.setAnchor(anchor)
    showColorPanel.value = true
  }

  function setDefaultColor() {
    editor.value?.chain().focus().unsetColor().run()
    closeColorPanel()
  }

  function setPresetColor(color: string) {
    editor.value?.chain().focus().setColor(color).run()
    closeColorPanel()
  }

  return {
    closeColorPanel,
    setDefaultColor,
    setPresetColor,
    toggleColorPanel,
  }
}

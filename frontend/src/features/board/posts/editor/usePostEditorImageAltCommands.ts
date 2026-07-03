import { ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'

type PopoverPosition = {
  setAnchor: (element?: HTMLElement | null) => void
  clearAnchor: () => void
}

type ImageAltCommandOptions = {
  editor: Ref<Editor | undefined>
  showImageAltPopover: Ref<boolean>
  imageAltPosition: PopoverPosition
  closeFloatingMenus: () => void
}

export function usePostEditorImageAltCommands({
  editor,
  showImageAltPopover,
  imageAltPosition,
  closeFloatingMenus,
}: ImageAltCommandOptions) {
  const imageAltText = ref('')

  function openImageAltPopover(anchor: HTMLElement, alt = '', nodePos?: number) {
    closeFloatingMenus()
    imageAltPosition.setAnchor(anchor)
    imageAltText.value = alt
    showImageAltPopover.value = true
    if (typeof nodePos === 'number') {
      editor.value?.commands.setTextSelection(nodePos)
    }
  }

  function closeImageAltPopover() {
    showImageAltPopover.value = false
    imageAltPosition.clearAnchor()
    imageAltText.value = ''
  }

  function applyImageAlt(value = imageAltText.value) {
    editor.value?.chain().focus().updateAttributes('image', { alt: value.trim() }).run()
    closeImageAltPopover()
  }

  function clearImageAlt() {
    editor.value?.chain().focus().updateAttributes('image', { alt: null }).run()
    closeImageAltPopover()
  }

  return {
    imageAltText,
    openImageAltPopover,
    closeImageAltPopover,
    applyImageAlt,
    clearImageAlt,
  }
}

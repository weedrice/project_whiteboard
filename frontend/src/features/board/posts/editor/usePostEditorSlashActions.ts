import type { Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import type { SlashAction } from '@/components/board/editor/postEditorOptions'

type SlashActionOptions = {
  editor: Ref<Editor | undefined>
  showSlashMenu: Ref<boolean>
  applyBulletList: () => void
  openLinkPopover: () => void
  openTablePopover: () => void
  openPollEditor: () => void
}

export function usePostEditorSlashActions({
  editor,
  showSlashMenu,
  applyBulletList,
  openLinkPopover,
  openTablePopover,
  openPollEditor,
}: SlashActionOptions) {
  function applySlashAction(action: SlashAction) {
    switch (action) {
      case 'heading':
        editor.value?.chain().focus().toggleHeading({ level: 2 }).run()
        break
      case 'quote':
        editor.value?.chain().focus().setBlockquote().run()
        break
      case 'list':
        applyBulletList()
        break
      case 'link':
        openLinkPopover()
        break
      case 'table':
        openTablePopover()
        break
      case 'codeBlock':
        editor.value?.chain().focus().toggleCodeBlock().run()
        break
      case 'divider':
        editor.value?.chain().focus().setHorizontalRule().run()
        break
      case 'poll':
        openPollEditor()
        break
    }
    showSlashMenu.value = false
  }

  return {
    applySlashAction,
  }
}

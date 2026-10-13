import type { Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { closeHistory } from '@tiptap/pm/history'
import type { SlashAction } from '@/components/board/editor/postEditorOptions'

type SlashActionOptions = {
  editor: Ref<Editor | undefined>
  showSlashMenu: Ref<boolean>
  applyBulletList: () => void
  openLinkPopover: () => void
  openTablePopover: () => void
  openPollEditor: () => void
  detailsTitle: () => string
}

export function usePostEditorSlashActions({
  editor,
  showSlashMenu,
  applyBulletList,
  openLinkPopover,
  openTablePopover,
  openPollEditor,
  detailsTitle,
}: SlashActionOptions) {
  function applySlashAction(action: SlashAction) {
    const instance = editor.value
    const isContentBlock = ['taskList', 'info', 'warning', 'details'].includes(action)
    if (isContentBlock && instance) instance.view.dispatch(closeHistory(instance.state.tr))
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
      case 'taskList':
        editor.value?.chain().focus().toggleList('taskList', 'taskItem').run()
        break
      case 'info':
      case 'warning':
        editor.value?.chain().focus().wrapIn('callout', { kind: action }).run()
        break
      case 'details':
        editor.value?.chain().focus().insertContent([
          { type: 'details', content: [
            { type: 'detailsSummary', content: [{ type: 'text', text: detailsTitle() }] },
            { type: 'detailsContent', content: [{ type: 'paragraph' }] },
          ] },
          { type: 'paragraph' },
        ]).run()
        break
    }
    if (isContentBlock && instance) instance.view.dispatch(closeHistory(instance.state.tr))
    showSlashMenu.value = false
  }

  return {
    applySlashAction,
  }
}

import { ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { escapeHtmlAttr, escapeHtmlText } from '@/components/board/editor/postEditorHtml'
import { toSafePostLinkUrl } from '@/utils/postForm'

type PopoverPosition = {
  setAnchor: (element?: HTMLElement | null) => void
  clearAnchor: () => void
}

type LinkCommandOptions = {
  editor: Ref<Editor | undefined>
  showLinkPopover: Ref<boolean>
  linkPosition: PopoverPosition
  closeFloatingMenus: () => void
  t: (key: string) => string
  addToast: (message: string, type: 'error') => void
}

export function usePostEditorLinkCommands({
  editor,
  showLinkPopover,
  linkPosition,
  closeFloatingMenus,
  t,
  addToast,
}: LinkCommandOptions) {
  const linkUrl = ref('')
  const linkText = ref('')

  function openLinkPopover(anchor?: HTMLElement) {
    closeFloatingMenus()
    linkPosition.setAnchor(anchor)
    const attrs = editor.value?.getAttributes('link')
    linkUrl.value = attrs?.href ?? ''
    const { from, to } = editor.value?.state.selection ?? {}
    const selectedText = from !== undefined && to !== undefined && from < to
      ? editor.value?.state.doc.textBetween(from, to, ' ') ?? ''
      : ''
    linkText.value = selectedText
    showLinkPopover.value = true
  }

  function closeLinkPopover() {
    showLinkPopover.value = false
    linkPosition.clearAnchor()
    linkUrl.value = ''
    linkText.value = ''
  }

  function applyLink(nextUrl = linkUrl.value, nextText = linkText.value) {
    linkUrl.value = nextUrl
    linkText.value = nextText
    const url = nextUrl.trim()
    const displayText = nextText.trim()
    if (!url) {
      addToast(t('board.writePost.linkUrlPrompt'), 'error')
      return
    }
    const safeUrl = toSafePostLinkUrl(url)
    if (!safeUrl) {
      addToast(t('board.writePost.invalidLinkUrl'), 'error')
      return
    }
    const text = displayText || url
    const { from, to } = editor.value?.state.selection ?? {}
    const hasSelection = from !== undefined && to !== undefined && from < to
    if (hasSelection) {
      editor.value?.chain().focus().setLink({ href: safeUrl }).run()
    } else {
      editor.value?.chain().focus().insertContent(`<a href="${escapeHtmlAttr(safeUrl)}" class="tiptap-link">${escapeHtmlText(text)}</a>`).run()
    }
    closeLinkPopover()
  }

  function removeLink() {
    editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
    closeLinkPopover()
  }

  return {
    linkUrl,
    linkText,
    openLinkPopover,
    closeLinkPopover,
    applyLink,
    removeLink,
  }
}

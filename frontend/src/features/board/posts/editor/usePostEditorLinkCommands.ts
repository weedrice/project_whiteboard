import { ref, type Ref } from 'vue'
import { getMarkRange, type Editor } from '@tiptap/core'
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

  function targetRange(instance: Editor) {
    const { selection } = instance.state
    const linkType = instance.schema.marks.link
    if (selection.empty && linkType && instance.isActive('link')) {
      return getMarkRange(selection.$from, linkType) ?? { from: selection.from, to: selection.to }
    }
    return { from: selection.from, to: selection.to }
  }

  function openLinkPopover(anchor?: HTMLElement) {
    closeFloatingMenus()
    linkPosition.setAnchor(anchor)
    const attrs = editor.value?.getAttributes('link')
    linkUrl.value = attrs?.href ?? ''
    const { from, to } = editor.value ? targetRange(editor.value) : {}
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
    const instance = editor.value
    if (!instance) return
    const range = targetRange(instance)
    if (range.from < range.to) {
      const currentText = instance.state.doc.textBetween(range.from, range.to, ' ')
      if (displayText && displayText !== currentText.trim()) {
        const marks = instance.state.doc.nodeAt(range.from)?.marks
          ?? instance.state.doc.resolve(range.from).marks()
        instance.chain().focus().insertContentAt(range, {
          type: 'text',
          text: displayText,
          marks: [
            ...marks.filter((mark) => mark.type.name !== 'link').map((mark) => mark.toJSON()),
            { type: 'link', attrs: { ...instance.getAttributes('link'), href: safeUrl } },
          ],
        }).run()
      } else {
        // Updating only the URL must keep the original text and its inline formatting.
        instance.chain().focus().setTextSelection(range).setLink({ href: safeUrl }).run()
      }
    } else {
      const text = displayText || url
      instance.chain().focus().insertContent(`<a href="${escapeHtmlAttr(safeUrl)}" class="tiptap-link">${escapeHtmlText(text)}</a>`).run()
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

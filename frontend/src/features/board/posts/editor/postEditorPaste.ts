import type { Editor } from '@tiptap/core'
import { sanitizeQuillHtml } from '@/utils/sanitize'
import { closeHistory } from '@tiptap/pm/history'

export type PostEditorPasteMode = 'original' | 'clean' | 'text'

export function cleanPostEditorPaste(html: string): string {
  const template = document.createElement('template')
  template.innerHTML = sanitizeQuillHtml(html)
  template.content.querySelectorAll('*').forEach(element => {
    element.removeAttribute('style')
    // Keep code languages, but discard external layout/typography classes.
    if (element.tagName !== 'CODE') element.removeAttribute('class')
  })
  return template.innerHTML
}

export function pasteWithPostEditorMode(editor: Editor | undefined, event: ClipboardEvent, mode: PostEditorPasteMode): boolean {
  if (!editor?.isEditable || !event.clipboardData || mode === 'original') return false
  const html = event.clipboardData.getData('text/html')
  const text = event.clipboardData.getData('text/plain')
  if (!html && !text) return false
  editor.view.dispatch(closeHistory(editor.state.tr))
  if (mode === 'text' || editor.state.selection.$from.parent.type.spec.code) {
    const template = document.createElement('template')
    template.innerHTML = sanitizeQuillHtml(html)
    editor.view.pasteText(text || template.content.textContent || '')
  } else if (html) {
    editor.commands.insertContent(cleanPostEditorPaste(html))
  } else {
    editor.view.pasteText(text)
  }
  editor.view.dispatch(closeHistory(editor.state.tr))
  return true
}

import type { Editor } from '@tiptap/core'

/** Handle code before the general HTML-preservation paste path. */
export function pastePostEditorCode(editor: Editor | undefined, event: ClipboardEvent): boolean {
  const clipboard = event.clipboardData
  if (!editor?.isEditable || !clipboard) return false
  const text = clipboard.getData('text/plain')
  if (!text) return false

  // Use ProseMirror's text paste so selection replacement and line breaks are preserved.
  if (editor.state.selection.$from.parent.type.spec.code) {
    return editor.view.pasteText(text)
  }

  const vscode = clipboard.getData('vscode-editor-data')
  if (!vscode) return false
  let metadata: unknown
  try {
    metadata = JSON.parse(vscode)
  } catch {
    // Malformed clipboard metadata must not reach TipTap's unguarded JSON.parse.
  }
  if (metadata && typeof metadata === 'object' && 'mode' in metadata
    && typeof metadata.mode === 'string' && /^[a-z0-9_+.#-]+$/i.test(metadata.mode)) {
    // Pass validated metadata to TipTap's existing VS Code clipboard handler.
    return editor.view.pasteText(text, event)
  }
  return editor.view.pasteText(text)
}

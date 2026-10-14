import type { Editor } from '@tiptap/core'
import { NodeSelection } from '@tiptap/pm/state'

export function isPostEditorFormattingDisabled(editor: Editor, rawHtmlSelected = false): boolean {
  return !editor.isEditable || rawHtmlSelected
    || (editor.state.selection instanceof NodeSelection && editor.state.selection.node.isAtom)
    || editor.isActive('codeBlock')
}

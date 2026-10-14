import type { Editor } from '@tiptap/core'
import { Fragment } from '@tiptap/pm/model'
import { NodeSelection, Selection } from '@tiptap/pm/state'
import { closeHistory } from '@tiptap/pm/history'

/** Only target a single containing block, never a selection spanning other blocks. */
export function getPostEditorContentBlock(editor: Editor, types = ['callout', 'details']) {
  const { selection } = editor.state
  if (selection instanceof NodeSelection && types.includes(selection.node.type.name)) {
    return { node: selection.node, position: selection.from }
  }
  for (let depth = selection.$from.depth; depth > 0; depth--) {
    const node = selection.$from.node(depth)
    if (types.includes(node.type.name) && selection.to <= selection.$from.end(depth)) {
      return { node, position: selection.$from.before(depth) }
    }
  }
  return null
}

export function setPostEditorCalloutKind(editor: Editor, kind: 'info' | 'warning'): boolean {
  const block = getPostEditorContentBlock(editor, ['callout'])
  if (!editor.isEditable || !block) return false
  if (block.node.attrs.kind === kind) return true
  editor.view.dispatch(closeHistory(editor.state.tr).setNodeMarkup(block.position, undefined, { ...block.node.attrs, kind }))
  editor.view.dispatch(closeHistory(editor.state.tr))
  editor.commands.focus()
  return true
}

export function unwrapPostEditorContentBlock(editor: Editor): boolean {
  const block = getPostEditorContentBlock(editor)
  if (!editor.isEditable || !block) return false
  const { node, position } = block
  // A details title becomes a paragraph before its body, retaining inline marks and media.
  const content = node.type.name === 'details'
    ? Fragment.from(editor.schema.nodes.paragraph!.create(null, node.child(0).content)).append(node.child(1).content)
    : node.content
  const $position = editor.state.doc.resolve(position)
  if (!$position.parent.canReplace($position.index(), $position.index() + 1, content)) return false
  const tr = closeHistory(editor.state.tr).replaceWith(position, position + node.nodeSize, content)
  tr.setSelection(Selection.near(tr.doc.resolve(position + 1)))
  editor.view.dispatch(tr.scrollIntoView())
  editor.view.dispatch(closeHistory(editor.state.tr))
  editor.commands.focus()
  return true
}

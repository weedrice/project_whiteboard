import type { Editor } from '@tiptap/core'
import type { Node } from '@tiptap/pm/model'
import { closeHistory } from '@tiptap/pm/history'

export interface PostEditorTextMatch { from: number; to: number }

export function exitPostEditorContentBlock(editor: Editor): boolean {
  if (!editor.isEditable) return false
  const { $from } = editor.state.selection
  for (let depth = $from.depth; depth > 0; depth--) {
    if (!['callout', 'details', 'taskList'].includes($from.node(depth).type.name)) continue
    const position = $from.after(depth)
    const next = editor.state.doc.nodeAt(position)
    if (next?.type.name === 'paragraph' && next.content.size === 0) {
      return editor.chain().focus().setTextSelection(position + 1).run()
    }
    return editor.chain().focus().insertContentAt(position, { type: 'paragraph' }).setTextSelection(position + 1).run()
  }
  return false
}

/** Match adjacent text across marks, without crossing blocks or inline atoms. */
export function findPostEditorText(doc: Node, query: string): PostEditorTextMatch[] {
  if (!query) return []
  const matches: PostEditorTextMatch[] = []
  const pattern = new RegExp(query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'giu')
  doc.descendants((block, position) => {
    if (!block.isTextblock) return true
    let text = ''
    let start = position + 1
    const flush = () => {
      pattern.lastIndex = 0
      for (const match of text.matchAll(pattern)) {
        matches.push({ from: start + match.index, to: start + match.index + match[0].length })
      }
      text = ''
    }
    block.forEach((child, offset) => {
      if (child.isText) {
        if (!text) start = position + 1 + offset
        text += child.text
      } else flush()
    })
    flush()
    return false
  })
  return matches
}

export function replacePostEditorText(editor: Editor, query: string, replacement: string, all: boolean): number {
  if (!editor.isEditable || !query) return 0
  const matches = findPostEditorText(editor.state.doc, query)
  const selected = matches.find(match => match.from === editor.state.selection.from && match.to === editor.state.selection.to)
  const targets = all ? matches : selected ? [selected] : []
  if (!targets.length) return 0
  const tr = closeHistory(editor.state.tr)
  for (const match of [...targets].reverse()) tr.insertText(replacement, match.from, match.to)
  editor.view.dispatch(tr)
  editor.view.dispatch(closeHistory(editor.state.tr))
  return targets.length
}

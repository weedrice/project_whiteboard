import type { Editor } from '@tiptap/core'
import { TableMap } from '@tiptap/pm/tables'

/** Set a consistent width for every logical column, including merged cells. */
export function distributePostEditorColumns(editor: Editor): boolean {
  if (!editor.isEditable) return false
  const { $from } = editor.state.selection
  let depth = $from.depth
  while (depth > 0 && $from.node(depth).type.spec.tableRole !== 'table') depth--
  if (!depth) return false
  const table = $from.node(depth)
  const map = TableMap.get(table)
  const start = $from.start(depth)
  const dom = editor.view.nodeDOM($from.before(depth)) as HTMLElement | null
  const tableElement = dom?.matches('table') ? dom : dom?.querySelector('table')
  const measuredWidth = tableElement?.getBoundingClientRect().width ?? 0
  const widths = Array<number>(map.width).fill(0)
  for (const offset of new Set(map.map)) {
    const cell = table.nodeAt(offset)!
    const column = map.colCount(offset)
    for (let index = 0; index < cell.attrs.colspan; index++) {
      widths[column + index] = Math.max(widths[column + index], cell.attrs.colwidth?.[index] ?? 0)
    }
  }
  const totalWidth = measuredWidth || widths.reduce((sum, width) => sum + (width || 100), 0)
  const width = Math.max(40, Math.floor(totalWidth / map.width))
  const transaction = editor.state.tr
  for (const offset of new Set(map.map)) {
    const cell = table.nodeAt(offset)!
    transaction.setNodeMarkup(start + offset, undefined, {
      ...cell.attrs, colwidth: Array<number>(cell.attrs.colspan).fill(width),
    })
  }
  editor.view.dispatch(transaction)
  editor.commands.focus()
  return true
}

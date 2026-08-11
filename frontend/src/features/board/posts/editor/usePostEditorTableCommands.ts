import { ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { moveFromSelectedRawHtmlBlock } from '@/extensions/tiptap-raw-html-block'

type PopoverPosition = {
  setAnchor: (element?: HTMLElement | null) => void
  clearAnchor: () => void
}

type TableCommandOptions = {
  editor: Ref<Editor | undefined>
  showTablePopover: Ref<boolean>
  tablePosition: PopoverPosition
  closeFloatingMenus: () => void
}

export function usePostEditorTableCommands({
  editor,
  showTablePopover,
  tablePosition,
  closeFloatingMenus,
}: TableCommandOptions) {
  const tableRows = ref(3)
  const tableCols = ref(3)
  const tableHeaderRow = ref(true)

  function openTablePopover(anchor?: HTMLElement) {
    closeFloatingMenus()
    tablePosition.setAnchor(anchor)
    tableRows.value = 3
    tableCols.value = 3
    tableHeaderRow.value = true
    showTablePopover.value = true
  }

  function closeTablePopover() {
    showTablePopover.value = false
    tablePosition.clearAnchor()
  }

  function applyTable() {
    const rows = Math.max(1, Math.min(20, Math.floor(Number(tableRows.value)) || 3))
    const cols = Math.max(1, Math.min(10, Math.floor(Number(tableCols.value)) || 3))
    if (editor.value) moveFromSelectedRawHtmlBlock(editor.value, 1)
    editor.value?.chain().focus().insertTable({ rows, cols, withHeaderRow: tableHeaderRow.value }).run()
    closeTablePopover()
  }

  return {
    tableRows,
    tableCols,
    tableHeaderRow,
    openTablePopover,
    closeTablePopover,
    applyTable,
  }
}

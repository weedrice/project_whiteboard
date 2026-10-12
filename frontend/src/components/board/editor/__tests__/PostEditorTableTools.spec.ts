import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { Editor } from '@tiptap/vue-3'
import { CellSelection } from '@tiptap/pm/tables'
import { closeHistory } from '@tiptap/pm/history'
import PostEditorTableTools from '../PostEditorTableTools.vue'
import { createPostEditorExtensions } from '../postEditorExtensions'
import { requiresPreservedPostHtml } from '@/utils/postHtmlSandbox'

describe('PostEditorTableTools', () => {
  let editor: Editor
  let wrapper: VueWrapper

  function mountTable(editable = true) {
    editor = new Editor({ extensions: createPostEditorExtensions(), content: '<p>Before</p>' })
    editor.commands.insertTable({ rows: 2, cols: 2, withHeaderRow: false })
    editor.setEditable(editable)
    wrapper = mount(PostEditorTableTools, { props: { editor } })
  }

  function cells() {
    const positions: number[] = []
    editor.state.doc.descendants((node, position) => {
      if (node.type.name === 'tableCell' || node.type.name === 'tableHeader') positions.push(position)
    })
    return positions
  }

  function html() {
    return new DOMParser().parseFromString(editor.getHTML(), 'text/html')
  }

  async function click(action: string) {
    await wrapper.get(`[data-table-action="${action}"]`).trigger('click')
  }

  afterEach(() => {
    wrapper?.unmount()
    editor?.destroy()
  })

  it('adds and removes rows and columns relative to the selected cell', async () => {
    mountTable()
    await click('addRowBefore')
    await click('addRowAfter')
    expect(html().querySelectorAll('tr')).toHaveLength(4)
    await click('deleteRow')
    expect(html().querySelectorAll('tr')).toHaveLength(3)
    await click('addColumnBefore')
    await click('addColumnAfter')
    expect(html().querySelector('tr')?.children).toHaveLength(4)
    await click('deleteColumn')
    expect(html().querySelector('tr')?.children).toHaveLength(3)
  })

  it('merges selected cells without losing text, supports undo and splits merged cells', async () => {
    mountTable()
    editor.commands.insertContentAt(cells()[0] + 2, 'Alpha')
    editor.commands.insertContentAt(cells()[1] + 2, 'Beta')
    expect(wrapper.get('[data-table-action="mergeCells"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.get('[data-table-action="splitCell"]').attributes()).toHaveProperty('disabled')

    editor.view.dispatch(editor.state.tr.setSelection(CellSelection.create(editor.state.doc, cells()[0], cells()[1])))
    await nextTick()
    await vi.waitFor(() => expect(wrapper.get('[data-table-action="mergeCells"]').attributes()).not.toHaveProperty('disabled'))
    editor.view.dispatch(closeHistory(editor.state.tr))
    await click('mergeCells')
    const merged = html().querySelector('td[colspan="2"]')
    expect(merged?.textContent).toBe('AlphaBeta')
    expect(requiresPreservedPostHtml(editor.getHTML())).toBe(false)
    const mergedHtml = editor.getHTML()
    editor.commands.undo()
    expect(html().querySelectorAll('td')).toHaveLength(4)
    editor.commands.setContent(mergedHtml)
    editor.commands.setTextSelection(cells()[0] + 2)
    await nextTick()
    await vi.waitFor(() => expect(wrapper.get('[data-table-action="splitCell"]').attributes()).not.toHaveProperty('disabled'))
    await click('splitCell')
    expect(html().querySelectorAll('td')).toHaveLength(4)
    expect(html().body.textContent).toContain('AlphaBeta')
  })

  it('toggles header rows and columns and round-trips the edited table', async () => {
    mountTable()
    await click('toggleHeaderRow')
    expect(html().querySelectorAll('th')).toHaveLength(2)
    await click('toggleHeaderRow')
    expect(html().querySelectorAll('th')).toHaveLength(0)
    await click('toggleHeaderColumn')
    expect(html().querySelectorAll('th')).toHaveLength(2)
    const saved = editor.getHTML()
    editor.commands.setContent(saved)
    expect(html().querySelectorAll('th')).toHaveLength(2)
    expect(requiresPreservedPostHtml(editor.getHTML())).toBe(false)
  })

  it('deletes the table while preserving surrounding content and hides tools outside it', async () => {
    mountTable()
    editor.view.dispatch(closeHistory(editor.state.tr))
    await click('deleteTable')
    expect(html().querySelector('table')).toBeNull()
    expect(html().body.textContent).toContain('Before')
    await vi.waitFor(() => expect(wrapper.find('[data-table-action]').exists()).toBe(false))
    editor.commands.undo()
    expect(html().querySelector('table')).not.toBeNull()
  })

  it('disables table actions in a read-only editor', async () => {
    mountTable(false)
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('keeps the tools mounted but inaccessible outside a table and restores them on re-entry', async () => {
    mountTable()
    const tools = wrapper.get('.tiptap-table-tools').element
    editor.commands.setTextSelection(editor.state.doc.content.size - 1)
    await vi.waitFor(() => expect(wrapper.get('.tiptap-table-tools').attributes('aria-hidden')).toBe('true'))
    expect(wrapper.get('.tiptap-table-tools').element).toBe(tools)
    expect(wrapper.get('.tiptap-table-tools').classes()).toContain('tiptap-table-tools--inactive')
    expect(wrapper.get('.tiptap-table-tools').attributes()).toHaveProperty('inert')
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)

    editor.commands.setTextSelection(cells()[0] + 2)
    await vi.waitFor(() => expect(wrapper.get('.tiptap-table-tools').attributes('aria-hidden')).toBe('false'))
    expect(wrapper.get('.tiptap-table-tools').element).toBe(tools)
    expect(wrapper.get('.tiptap-table-tools').attributes()).not.toHaveProperty('inert')
    expect(wrapper.get('[data-table-action="addRowAfter"]').attributes()).not.toHaveProperty('disabled')
  })

  it('does not reserve space in documents without a table, including after content replacement', async () => {
    mountTable()
    editor.commands.setContent('<p>No tables</p>')
    await vi.waitFor(() => expect(wrapper.find('.tiptap-table-tools').exists()).toBe(false))
  })
})

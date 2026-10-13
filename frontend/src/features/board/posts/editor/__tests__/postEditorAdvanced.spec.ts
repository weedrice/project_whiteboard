import { afterEach, describe, expect, it, vi } from 'vitest'
import { Editor } from '@tiptap/core'
import { ref, shallowRef } from 'vue'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { usePostEditorSlashActions } from '../usePostEditorSlashActions'
import { sanitizeQuillHtml } from '@/utils/sanitize'
import { requiresPreservedPostHtml } from '@/utils/postHtmlSandbox'

describe('advanced post editor', () => {
  let editor: Editor
  afterEach(() => { editor?.destroy(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
  function create(html = '<p>Before</p>') {
    editor = new Editor({ extensions: createPostEditorExtensions(), content: html })
    return editor
  }

  it.each([
    '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>Done</p></li><li data-type="taskItem" data-checked="false"><p>Todo</p></li></ul>',
    '<aside data-callout="info"><p>Information</p></aside>',
    '<aside data-callout="warning"><p>Warning</p></aside>',
    '<details><summary>Title</summary><div data-details-content=""><p>Body</p></div></details>',
  ])('round-trips a content block through sanitization: %s', html => {
    create(html + '<p>After</p>')
    const saved = editor.getHTML()
    expect(requiresPreservedPostHtml(saved)).toBe(false)
    editor.commands.setContent(sanitizeQuillHtml(saved))
    expect(editor.getHTML()).toBe(saved)
    expect(editor.getText()).toBeTruthy()
  })

  it('starts the next checklist item unchecked after completing the previous item', () => {
    create('<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>Done</p></li></ul>')
    editor.commands.setTextSelection(7)
    expect(editor.commands.splitListItem('taskItem')).toBe(true)
    const items = editor.state.doc.firstChild!
    expect(items.child(0).attrs.checked).toBe(true)
    expect(items.child(1).attrs.checked).toBe(false)
  })

  it('toggles a completed checklist back to paragraphs and restores completion on undo', () => {
    create('<ul data-type="taskList"><li data-type="taskItem" data-checked="false"><p>Done</p></li></ul>')
    editor.commands.setTextSelection(4)
    editor.commands.updateAttributes('taskItem', { checked: true })
    const actions = usePostEditorSlashActions({
      editor: shallowRef(editor), showSlashMenu: ref(true),
      applyBulletList: vi.fn(), openLinkPopover: vi.fn(), openTablePopover: vi.fn(), openPollEditor: vi.fn(), detailsTitle: () => 'Details',
    })
    actions.applySlashAction('taskList')
    expect(editor.getHTML()).not.toContain('data-type="taskList"')
    expect(editor.getText().trim()).toBe('Done')
    editor.commands.undo()
    expect(editor.state.doc.firstChild!.firstChild!.attrs.checked).toBe(true)
  })

  it('keeps nested checklists with paragraph-first items editable', () => {
    const html = '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>Parent</p><h2>Heading</h2><ul data-type="taskList"><li data-type="taskItem" data-checked="false"><p>Child</p></li></ul></li></ul>'
    expect(requiresPreservedPostHtml(html)).toBe(false)
    create(html)
    expect(editor.getHTML()).toContain('<h2>Heading</h2>')
    expect(editor.getText()).toContain('Child')
  })

  it('keeps supported inline formatting and images inside an editable details summary', () => {
    const html = '<details><summary><strong><em>Title</em></strong><br><span style="color: red">Note</span><img src="/api/v1/files/10" alt="Icon"></summary><div data-details-content=""><p>Body</p></div></details>'
    expect(requiresPreservedPostHtml(html)).toBe(false)
    create(html + '<p>After</p>')
    const summary = editor.state.doc.firstChild!.firstChild!
    expect(summary.type.name).toBe('detailsSummary')
    expect(summary.textContent).toBe('TitleNote')
    expect(summary.lastChild!.type.name).toBe('image')
    const saved = editor.getHTML()
    expect(requiresPreservedPostHtml(saved)).toBe(false)
    editor.commands.setContent(sanitizeQuillHtml(saved))
    expect(editor.getHTML()).toBe(saved)
  })

  it.each([
    '<summary>Standalone summary</summary>',
    '<ul data-type="taskList"><li><p>Unmarked item</p></li></ul>',
    '<ul><li data-type="taskItem" data-checked="true"><p>Orphan task</p></li></ul>',
    '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><h2>Heading</h2><p>Body</p></li></ul>',
    '<ul data-type="taskList">Loose text<li data-type="taskItem" data-checked="true"><p>Task</p></li></ul>',
    '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>Task</p>Loose text</li></ul>',
    '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>Task</p><strong>Loose inline</strong></li></ul>',
    '<details><summary>Title</summary>Outside content<div data-details-content=""><p>Inside</p></div></details>',
    '<details><summary><h2>Title</h2></summary><div data-details-content=""><p>Body</p></div></details>',
    '<details><summary><span style="color: red"><h2>Nested title</h2></span></summary><div data-details-content=""><p>Body</p></div></details>',
    '<details><summary><table><tr><td>Title</td></tr></table></summary><div data-details-content=""><p>Body</p></div></details>',
  ])('preserves imported block structures that cannot round-trip: %s', html => {
    expect(requiresPreservedPostHtml(html)).toBe(true)
  })
})

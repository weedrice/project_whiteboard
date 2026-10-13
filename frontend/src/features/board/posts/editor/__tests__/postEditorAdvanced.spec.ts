import { getPostEditorContentBlock, setPostEditorCalloutKind, unwrapPostEditorContentBlock } from '../postEditorContentBlocks'
import { postEditorSearchKey } from '../postEditorSearch'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Editor } from '@tiptap/core'
import { ref, shallowRef } from 'vue'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { cleanPostEditorPaste, pasteWithPostEditorMode } from '../postEditorPaste'
import { exitPostEditorContentBlock, findPostEditorText, replacePostEditorText } from '../postEditorDocumentTools'
import { usePostEditorUploadedImages } from '../usePostEditorUploadedImages'
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
  function clipboard(html: string, text: string) {
    return { clipboardData: { getData: (type: string) => type === 'text/html' ? html : text } } as ClipboardEvent
  }

  it('cleans external formatting while preserving editable structure and safe media', () => {
    const cleaned = cleanPostEditorPaste('<section class="word"><p style="color:red"><strong>Title</strong></p><table><tr><td>Cell</td></tr></table><script>bad()</script><img src="javascript:bad()"><img src="/api/v1/files/10" onerror="bad()"></section>')
    create(cleaned)
    expect(editor.getHTML()).toContain('<strong>Title</strong>')
    expect(editor.getHTML()).toContain('<table')
    expect(editor.getHTML()).not.toMatch(/script|onerror|javascript:|color: red|class="word"/)
    expect(requiresPreservedPostHtml(editor.getHTML())).toBe(false)
  })

  it('inserts cleaned paste in one undo step', () => {
    create()
    editor.commands.setTextSelection(7)
    pasteWithPostEditorMode(editor, clipboard('<p style="color:red">After</p>', 'After'), 'clean')
    expect(editor.getText()).toContain('After')
    editor.commands.undo()
    expect(editor.getText()).toBe('Before')
  })

  it('pastes text only without interpreting markup or losing code line breaks', () => {
    vi.stubGlobal('ClipboardEvent', class extends Event {})
    create('<pre><code>x</code></pre>')
    editor.commands.setTextSelection(2)
    pasteWithPostEditorMode(editor, clipboard('<strong>new</strong>', '\n<b>literal</b>\nnext'), 'text')
    expect(editor.getText()).toContain('x\n<b>literal</b>\nnext')
    expect(editor.getHTML()).not.toContain('<strong>')
  })

  it('finds text across marks but never crosses an image or block boundary', () => {
    create('<p><strong>Al</strong>pha Alpha</p><p>Al<img src="/api/v1/files/10">pha</p><p>Al</p><p>pha</p>')
    expect(findPostEditorText(editor.state.doc, 'alpha')).toEqual([{ from: 1, to: 6 }, { from: 7, to: 12 }])
    expect(findPostEditorText(editor.state.doc, '[')).toEqual([])
    expect(replacePostEditorText(editor, 'alpha', 'Beta', true)).toBe(2)
    expect(editor.getText()).toContain('Beta Beta')
    editor.commands.undo()
    expect(editor.getHTML()).toContain('<strong>Al</strong>pha Alpha')
  })

  it('only replaces a selected exact match and respects read-only state', () => {
    create('<p>one one</p>')
    expect(replacePostEditorText(editor, 'one', 'two', false)).toBe(0)
    editor.commands.setTextSelection({ from: 1, to: 4 })
    expect(replacePostEditorText(editor, 'one', 'two', false)).toBe(1)
    expect(editor.getText()).toBe('two one')
    editor.setEditable(false)
    expect(replacePostEditorText(editor, 'one', 'two', true)).toBe(0)
  })

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

  it('creates a checklist and exits a callout without losing its contents', () => {
    create('<p>Task</p>')
    editor.commands.toggleList('taskList', 'taskItem')
    expect(editor.getHTML()).toContain('data-type="taskList"')
    editor.commands.setContent('<aside data-callout="info"><p>Inside</p></aside>')
    editor.commands.setTextSelection(3)
    expect(exitPostEditorContentBlock(editor)).toBe(true)
    expect(editor.getHTML()).toBe('<aside data-callout="info"><p>Inside</p></aside><p></p>')
  })

  it('replaces only the image source and file identity and supports undo', () => {
    create('<p><img src="/api/v1/files/10" width="240" alt="Alt" data-caption="Caption" class="tiptap-image-align-right"></p>')
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:replacement')
    const uploaded = vi.fn()
    const images = usePostEditorUploadedImages(shallowRef(editor), uploaded)
    const original = editor.getHTML()
    expect(images.replaceUploadedImage({ url: '/api/v1/files/20', fileId: 20 }, new File(['image'], 'new.png'), 1)).toBe(true)
    const node = editor.state.doc.nodeAt(1)!
    expect(node.attrs).toMatchObject({ src: 'blob:replacement', serverSrc: '/api/v1/files/20', fileId: 20, alt: 'Alt', caption: 'Caption', width: '240', alignment: 'right' })
    expect(uploaded).toHaveBeenCalledWith(20)
    editor.commands.undo()
    expect(editor.getHTML()).toBe(original)
    images.disposeUploadedImagePreviews()
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
  it('changes an existing callout from the insert menu without nesting and undoes independently', () => {
    create('<aside data-callout="info"><p>Keep <strong>format</strong></p></aside>')
    editor.commands.setTextSelection(4)
    const original = editor.getHTML()
    const actions = usePostEditorSlashActions({
      editor: shallowRef(editor), showSlashMenu: ref(true),
      applyBulletList: vi.fn(), openLinkPopover: vi.fn(), openTablePopover: vi.fn(), openPollEditor: vi.fn(), detailsTitle: () => 'Details',
    })
    actions.applySlashAction('warning')
    expect(editor.getHTML()).toBe(original.replace('data-callout="info"', 'data-callout="warning"'))
    expect(unwrapPostEditorContentBlock(editor)).toBe(true)
    expect(editor.getHTML()).toBe(original.replace('<aside data-callout="info">', '').replace('</aside>', ''))
    editor.commands.undo()
    expect(editor.getHTML()).toContain('data-callout="warning"')
    editor.commands.undo()
    expect(editor.getHTML()).toBe(original)
  })

  it('unwraps only the innermost block and preserves a details title, media and body in order', () => {
    create('<aside data-callout="info"><details><summary><strong>Title</strong><img src="/api/v1/files/10"></summary><div data-details-content=""><p>Body</p><h2>End</h2></div></details></aside>')
    editor.commands.setTextSelection(4)
    const original = editor.getHTML()
    expect(getPostEditorContentBlock(editor)?.node.type.name).toBe('details')
    expect(unwrapPostEditorContentBlock(editor)).toBe(true)
    expect(editor.getHTML()).toContain('<aside data-callout="info"><p><strong>Title</strong><img')
    expect(editor.getHTML()).toContain('</p><p>Body</p><h2>End</h2></aside>')
    expect(editor.getHTML()).not.toContain('<details>')
    editor.commands.undo()
    expect(editor.getHTML()).toBe(original)
  })

  it('does not unwrap a spanning selection or change blocks in read-only mode', () => {
    create('<aside data-callout="info"><p>Inside</p></aside><p>Outside</p>')
    const original = editor.getHTML()
    editor.commands.setTextSelection({ from: 3, to: editor.state.doc.content.size - 1 })
    expect(unwrapPostEditorContentBlock(editor)).toBe(false)
    editor.commands.setTextSelection(3)
    editor.setEditable(false)
    expect(unwrapPostEditorContentBlock(editor)).toBe(false)
    expect(setPostEditorCalloutKind(editor, 'warning')).toBe(false)
    expect(editor.getHTML()).toBe(original)
  })

  it('highlights all matches and the current selection without changing HTML or undo history', () => {
    create('<p>one <strong>one</strong></p>')
    const original = editor.getHTML()
    editor.view.dispatch(editor.state.tr.setMeta(postEditorSearchKey, 'one'))
    expect(postEditorSearchKey.getState(editor.state)?.decorations.find()).toHaveLength(2)
    expect(editor.view.dom.querySelectorAll('.post-editor-search-match')).toHaveLength(2)
    editor.commands.setTextSelection({ from: 5, to: 8 })
    expect(editor.view.dom.querySelector('.post-editor-search-match--current')?.textContent).toBe('one')
    expect(editor.getHTML()).toBe(original)
    expect(editor.can().undo()).toBe(false)
    replacePostEditorText(editor, 'one', 'two', false)
    expect(postEditorSearchKey.getState(editor.state)?.matches).toHaveLength(1)
    editor.commands.undo()
    expect(postEditorSearchKey.getState(editor.state)?.matches).toHaveLength(2)
    editor.view.dispatch(editor.state.tr.setMeta(postEditorSearchKey, ''))
    expect(editor.view.dom.querySelectorAll('.post-editor-search-match')).toHaveLength(0)
    expect(editor.getHTML()).toBe(original)
  })

})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { Editor } from '@tiptap/vue-3'
import { closeHistory } from '@tiptap/pm/history'
import PostEditorTextTools from '../PostEditorTextTools.vue'
import { createPostEditorExtensions } from '../postEditorExtensions'

describe('PostEditorTextTools', () => {
  let editor: Editor
  let wrapper: VueWrapper
  function setup(content: string) {
    editor = new Editor({ extensions: createPostEditorExtensions(), content })
    wrapper = mount(PostEditorTextTools, { props: { editor, isRawHtmlBlockSelected: false } })
  }
  afterEach(() => { wrapper?.unmount(); editor?.destroy() })

  it('exposes undo and redo with their current availability', async () => {
    setup('<p>Before</p>')
    expect(wrapper.findAll('button')[0].attributes()).toHaveProperty('disabled')
    editor.commands.setTextSelection(7)
    editor.commands.insertContent(' after')
    await vi.waitFor(() => expect(wrapper.findAll('button')[0].attributes()).not.toHaveProperty('disabled'))
    await wrapper.findAll('button')[0].trigger('click')
    expect(editor.getText()).toBe('Before')
    await vi.waitFor(() => expect(wrapper.findAll('button')[1].attributes()).not.toHaveProperty('disabled'))
    await wrapper.findAll('button')[1].trigger('click')
    expect(editor.getText()).toBe('Before after')
  })

  it('clears selected formatting while retaining headings, links and unselected text', async () => {
    setup('<h2 style="text-align:center"><strong><span style="color:red;font-size:24px;line-height:2"><a href="https://noviis.kr">Alpha</a>Beta</span></strong></h2>')
    editor.commands.setTextSelection({ from: 1, to: 6 })
    editor.view.dispatch(closeHistory(editor.state.tr))
    await wrapper.findAll('button')[2].trigger('click')
    const doc = new DOMParser().parseFromString(editor.getHTML(), 'text/html')
    expect(doc.querySelector('h2')?.textContent).toBe('AlphaBeta')
    expect(doc.querySelector('a')?.textContent).toBe('Alpha')
    expect(doc.querySelector('strong')?.textContent).toBe('Beta')
    expect(doc.querySelector('h2')?.hasAttribute('style')).toBe(false)
    editor.commands.undo()
    expect(editor.getHTML()).toContain('text-align: center')
  })

  it('sets heading levels and returns to a paragraph without losing content', async () => {
    setup('<p>Heading</p>')
    editor.commands.setTextSelection(2)
    await wrapper.get('select').setValue('3')
    expect(editor.isActive('heading', { level: 3 })).toBe(true)
    expect(editor.state.doc.firstChild?.textContent).toBe('Heading')
    await wrapper.get('select').setValue('paragraph')
    expect(editor.isActive('paragraph')).toBe(true)
    expect(editor.state.doc.firstChild?.textContent).toBe('Heading')
  })

  it('protects code blocks and read-only documents from formatting changes', async () => {
    setup('<pre><code class="language-ts">const x = 1</code></pre>')
    editor.commands.setTextSelection(2)
    await vi.waitFor(() => expect(wrapper.get('select').attributes()).toHaveProperty('disabled'))
    expect(wrapper.findAll('button')[2].attributes()).toHaveProperty('disabled')
    editor.setEditable(false)
    await vi.waitFor(() => expect(wrapper.findAll('button').every(button => button.attributes('disabled') !== undefined)).toBe(true))
  })
  it('disables block conversion in a details title while keeping inline formatting available', async () => {
    setup('<details><summary>Title</summary><div data-details-content><p>Body</p></div></details>')
    editor.commands.setTextSelection(3)
    await vi.waitFor(() => expect(wrapper.get('select').attributes()).toHaveProperty('disabled'))
    expect(wrapper.findAll('option').every(option => option.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.findAll('button')[2]!.attributes()).not.toHaveProperty('disabled')
    const before = editor.getHTML()
    await wrapper.get('select').setValue('2')
    expect(editor.getHTML()).toBe(before)
    let bodyPosition = 0
    editor.state.doc.descendants((node, position) => { if (node.type.name === 'paragraph' && node.textContent === 'Body') bodyPosition = position + 1 })
    editor.commands.setTextSelection(bodyPosition)
    await vi.waitFor(() => expect(wrapper.get('select').attributes()).not.toHaveProperty('disabled'))
    await wrapper.get('select').setValue('2')
    expect(editor.getHTML()).toContain('<h2>Body</h2>')
    expect(editor.getHTML()).toContain('<summary>Title</summary>')
  })
})

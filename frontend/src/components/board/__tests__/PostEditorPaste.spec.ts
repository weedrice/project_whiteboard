import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import type { Editor } from '@tiptap/core'
import {
  mockCreateMutate,
  mountPostForm,
  resetPostFormTestState,
  submitPostForm,
  unmountPostFormWrappers,
} from './PostFormTestHarness'
import PostEditorTipTap from '../PostEditorTipTap.vue'

const uploadMocks = vi.hoisted(() => ({ uploadFile: vi.fn(), discardUploads: vi.fn() }))
vi.mock('@/api/file', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/file')>()
  return { ...actual, fileApi: { ...actual.fileApi, ...uploadMocks } }
})
vi.mock('@/stores/theme', () => ({ useThemeStore: () => ({ isDark: false }) }))

function paste(target: Element, html: string, text: string, files: File[] = [], vscode = '') {
  const event = new Event('paste', { bubbles: true, cancelable: true })
  const data: Record<string, string> = { 'text/html': html, 'text/plain': text, 'vscode-editor-data': vscode }
  Object.defineProperty(event, 'clipboardData', {
    value: { files, getData: (type: string) => data[type] ?? '' },
  })
  target.dispatchEvent(event)
}

async function mountComposer() {
  const wrapper = mountPostForm('create', { PostEditorTipTap })
  await flushPromises()
  await wrapper.get('#title').setValue('Pasted content')
  await wrapper.get('#category').setValue('1')
  return wrapper
}

function submittedContents(): string {
  expect(mockCreateMutate).toHaveBeenCalledOnce()
  return mockCreateMutate.mock.calls[0]?.[0].data.contents
}

describe('PostEditor clipboard integration', () => {
  beforeEach(() => {
    // jsdom does not implement the empty ClipboardEvent used by view.pasteText.
    vi.stubGlobal('ClipboardEvent', class extends Event { clipboardData = null })
    resetPostFormTestState()
    uploadMocks.uploadFile.mockReset()
    uploadMocks.discardUploads.mockResolvedValue({ data: { data: { discardedCount: 0 } } })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:paste-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    unmountPostFormWrappers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('preserves rich HTML once without submitting a duplicate plain paragraph', async () => {
    const wrapper = await mountComposer()
    paste(wrapper.get('.ProseMirror').element, '<section style="padding:12px"><p>Clipboard message</p></section>', 'Clipboard message')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="raw-html-block"]')).toHaveLength(1)
    await submitPostForm(wrapper)
    expect(submittedContents()).toContain('noviis-sandboxed-post-html')
    expect(submittedContents()).not.toContain('<p>Clipboard message</p>')
  })

  it.each(['plain text', 'supported HTML'])('lets TipTap insert %s once', async (format) => {
    const wrapper = await mountComposer()
    paste(wrapper.get('.ProseMirror').element, format === 'supported HTML' ? '<p><strong>Clipboard message</strong></p>' : '', 'Clipboard message')
    await flushPromises()
    await submitPostForm(wrapper)
    expect(submittedContents().match(/Clipboard message/g)).toHaveLength(1)
    expect(submittedContents()).not.toContain('noviis-sandboxed-post-html')
    expect(submittedContents().includes('<strong>Clipboard message</strong>')).toBe(format === 'supported HTML')
  })

  it.each([false, true])('pastes VS Code as editable code with HTML=%s', async (withHtml) => {
    const wrapper = await mountComposer()
    const text = 'const answer = 42;\r\n  // Code'
    paste(wrapper.get('.ProseMirror').element,
      withHtml ? '<div style="font-family:Consolas"><span style="color:blue">const</span> answer = 42;</div>' : '',
      text, [], JSON.stringify({ mode: 'typescript' }))
    await flushPromises()
    expect(wrapper.findAll('[data-testid="raw-html-block"]')).toHaveLength(0)
    expect(wrapper.get('pre code').element.textContent).toBe(text.replace('\r\n', '\n'))
    expect(wrapper.get('pre code').classes()).toContain('language-typescript')
    await submitPostForm(wrapper)
    expect(submittedContents()).toContain('language-typescript')
    expect(submittedContents()).not.toContain('noviis-sandboxed-post-html')
  })

  it('replaces the selection inside code with plain text, retaining language and line breaks', async () => {
    const wrapper = await mountComposer()
    const target = wrapper.get('.ProseMirror').element as HTMLElement & { editor: Editor }
    target.editor.commands.setContent('<pre><code class="language-typescript">abcdef</code></pre>')
    await flushPromises()
    target.editor.commands.setTextSelection({ from: 2, to: 4 })
    paste(target, '<div style="font-family:Consolas">formatted</div>', '<tag>\r\n  code', [], JSON.stringify({ mode: 'html' }))
    await flushPromises()
    expect(wrapper.findAll('[data-testid="raw-html-block"]')).toHaveLength(0)
    expect(wrapper.get('pre code').element.textContent).toBe('a<tag>\n  codedef')
    expect(wrapper.get('pre code').classes()).toContain('language-typescript')
    expect(wrapper.find('pre tag').exists()).toBe(false)
  })

  it.each(['{bad', '{"mode":42}', '{"mode":"javascript other-class"}'])('safely falls back to text for invalid VS Code metadata %s', async (metadata) => {
    const wrapper = await mountComposer()
    paste(wrapper.get('.ProseMirror').element, '<div style="font-family:Consolas">formatted</div>', '<tag>Code</tag>', [], metadata)
    await flushPromises()
    expect(wrapper.findAll('[data-testid="raw-html-block"]')).toHaveLength(0)
    expect(wrapper.get('.ProseMirror').text()).toContain('<tag>Code</tag>')
    expect(wrapper.find('pre').exists()).toBe(false)
    expect(wrapper.find('.ProseMirror tag').exists()).toBe(false)
  })

  it('uploads a clipboard image once and skips its accompanying HTML and code metadata', async () => {
    uploadMocks.uploadFile.mockResolvedValue({ data: { success: true, data: { fileId: 501, fileUrl: '/api/v1/files/501' } } })
    const wrapper = await mountComposer()
    paste(wrapper.get('.ProseMirror').element, '<p><img src="https://example.com/clipboard.png"></p>', 'code', [new File(['image'], 'clipboard.png', { type: 'image/png' })], JSON.stringify({ mode: 'typescript' }))
    await flushPromises()
    await submitPostForm(wrapper)
    expect(uploadMocks.uploadFile).toHaveBeenCalledOnce()
    const contents = submittedContents()
    expect(contents.match(/<img /g)).toHaveLength(1)
    expect(contents).toContain('/api/v1/files/501')
    expect(contents).not.toContain('https://example.com/clipboard.png')
    expect(mockCreateMutate.mock.calls[0]?.[0].data.fileIds).toEqual([501])
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
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

function paste(target: Element, html: string, text: string, files: File[] = []) {
  const event = new Event('paste', { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'clipboardData', {
    value: { files, getData: (type: string) => type === 'text/html' ? html : type === 'text/plain' ? text : '' },
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
    resetPostFormTestState()
    uploadMocks.uploadFile.mockReset()
    uploadMocks.discardUploads.mockResolvedValue({ data: { data: { discardedCount: 0 } } })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:paste-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    unmountPostFormWrappers()
    vi.restoreAllMocks()
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

  it('uploads a clipboard image once and skips its accompanying HTML preview', async () => {
    uploadMocks.uploadFile.mockResolvedValue({ data: { success: true, data: { fileId: 501, fileUrl: '/api/v1/files/501' } } })
    const wrapper = await mountComposer()
    paste(wrapper.get('.ProseMirror').element, '<p><img src="https://example.com/clipboard.png"></p>', '', [new File(['image'], 'clipboard.png', { type: 'image/png' })])
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

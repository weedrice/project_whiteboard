import { createPinia, setActivePinia } from 'pinia'
import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import { defineComponent, h, inject, ref, shallowRef } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { postEditorImageReplacementKey, usePostEditorImageReplacement } from '../usePostEditorImageReplacement'

const mocks = vi.hoisted(() => ({ upload: vi.fn(), discard: vi.fn() }))
vi.mock('@/api/file', () => ({
  fileApi: { uploadFile: mocks.upload, discardUploads: mocks.discard },
  resolveFileUploadUrl: (file: { fileUrl: string }) => file.fileUrl,
}))

describe('image replacement cancellation', () => {
  let editor: Editor
  let wrapper: VueWrapper
  let replacement: ReturnType<typeof usePostEditorImageReplacement>
  let request: (position: () => number | undefined) => void
  const owner = ref<string | undefined>('owner')
  const replace = vi.fn(() => true)
  const failed = vi.fn()
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mocks.discard.mockResolvedValue({})
    owner.value = 'owner'
    editor = new Editor({ extensions: [StarterKit, Image.configure({ inline: true })], content: '<p><img src="/original.png"></p>' })
    const Child = defineComponent({ setup() {
      request = inject(postEditorImageReplacementKey)!.request
      return () => h('span')
    } })
    wrapper = mount(defineComponent({ setup() {
      replacement = usePostEditorImageReplacement({ editor: shallowRef(editor), ownerIdentity: owner, replace, invalid: vi.fn(), failed })
      return () => h(Child)
    } }))
  })
  afterEach(() => { wrapper.unmount(); editor.destroy() })
  function start() {
    request(() => 1)
    const input = document.createElement('input')
    Object.defineProperty(input, 'files', { value: [new File(['image'], 'replacement.png', { type: 'image/png' })] })
    return replacement.onChange({ target: input } as unknown as Event)
  }

  it.each(['cancel', 'owner', 'unmount'])('aborts for %s without replacing or reporting a failure', async reason => {
    mocks.upload.mockImplementation((_file: File, { signal }: { signal: AbortSignal }) => new Promise((_, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    }))
    const upload = start()
    expect(replacement.busy.value).toBe(true)
    expect(replacement.currentFileName.value).toBe('replacement.png')
    if (reason === 'owner') owner.value = 'other'
    else if (reason === 'unmount') wrapper.unmount()
    else replacement.cancel()
    await upload
    expect(replacement.busy.value).toBe(false)
    expect(replacement.currentFileName.value).toBe('')
    expect(replace).not.toHaveBeenCalled()
    expect(failed).not.toHaveBeenCalled()
    expect(editor.getHTML()).toContain('/original.png')
  })

  it('discards a late successful response after cancellation without showing a failure', async () => {
    let finish!: (value: unknown) => void
    mocks.upload.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const upload = start()
    replacement.cancel()
    finish({ data: { success: true, data: { fileId: 99, fileUrl: '/new.png' } } })
    await upload
    expect(mocks.discard).toHaveBeenCalledWith([99], expect.objectContaining({ skipGlobalErrorHandler: true }))
    expect(replace).not.toHaveBeenCalled()
    expect(failed).not.toHaveBeenCalled()
  })

  it('can replace again after a cancelled request settles', async () => {
    mocks.upload.mockImplementationOnce((_file: File, { signal }: { signal: AbortSignal }) => new Promise((_, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    }))
    const upload = start()
    replacement.cancel()
    await upload
    mocks.upload.mockResolvedValueOnce({ data: { success: true, data: { fileId: 99, fileUrl: '/new.png' } } })
    await start()
    expect(replace).toHaveBeenCalledWith({ fileId: 99, url: '/new.png' }, expect.any(File), 1)
    expect(failed).not.toHaveBeenCalled()
  })
})

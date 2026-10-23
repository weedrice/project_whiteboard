import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePostComposerUploadOwnership } from '@/features/board/posts/form/usePostComposerUploadOwnership'
import { encodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { createDeferred } from '@/test/async'
import { Editor } from '@tiptap/core'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { resolvePostFormFileIds } from '@/utils/postForm'

const discardUploadsMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/file', () => ({ fileApi: { discardUploads: discardUploadsMock } }))
vi.mock('@/utils/logger', () => ({ default: { warn: vi.fn() } }))

function createOwnership() {
  const scope = effectScope()
  const identity = ref('session-1:create:free:new')
  const content = ref('')
  const durableDraftFileIds = ref<number[]>([])
  const ownership = scope.run(() => usePostComposerUploadOwnership({ identity, durableDraftFileIds }))
  if (!ownership) throw new Error('Upload ownership composable was not initialized')
  return { scope, identity, content, durableDraftFileIds, ownership }
}

describe('usePostComposerUploadOwnership', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    discardUploadsMock.mockResolvedValue({ data: { data: { discardedCount: 1 } } })
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
  })
  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('keeps a removed upload available for undo after the old discard deadline', async () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    const image = '<img data-file-id="41" src="/api/v1/files/41">'
    ownership.recordUploadedFile(41)
    content.value = image
    await nextTick()
    content.value = '<p>removed</p>'
    await nextTick()
    await vi.advanceTimersByTimeAsync(10_000)
    content.value = image
    await nextTick()

    expect(discardUploadsMock).not.toHaveBeenCalled()
    expect(ownership.ownedUploadedFileIds.value).toEqual([41])
    durableDraftFileIds.value = [41]
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalled()
  })

  it('retains replaced uploads while editing and discards unused files on exit', async () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    ownership.adoptUploadedFiles([41, 42, 41])
    content.value = '<img src="/api/v1/files/42">'
    durableDraftFileIds.value = [42]
    await nextTick()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(discardUploadsMock).not.toHaveBeenCalled()
    scope.stop()
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([41], { skipGlobalErrorHandler: true })
  })

  it('preserves a real editor undo after deletion and submits only restored content files', async () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    ownership.adoptUploadedFiles([41, 42])
    const editor = new Editor({
      extensions: createPostEditorExtensions(),
      content: '<img data-file-id="41" src="/api/v1/files/41"><p>After image</p>',
      onUpdate: ({ editor: current }) => { content.value = current.getHTML() },
    })
    try {
      content.value = editor.getHTML()
      editor.commands.setNodeSelection(0)
      editor.commands.deleteSelection()
      await nextTick()
      await vi.advanceTimersByTimeAsync(10_000)
      expect(resolvePostFormFileIds(content.value, [41, 42], 'content')).toEqual([])
      expect(editor.commands.undo()).toBe(true)
      await nextTick()
      expect(discardUploadsMock).not.toHaveBeenCalled()
      expect(resolvePostFormFileIds(content.value, [41, 42], 'content')).toEqual([41])
      durableDraftFileIds.value = [41]
    } finally {
      editor.destroy()
      scope.stop()
    }
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([42], { skipGlobalErrorHandler: true })
  })

  it('splits terminal cleanup at the API limit', () => {
    const { scope, ownership } = createOwnership()
    const ids = Array.from({ length: 102 }, (_, index) => index + 1)
    ownership.adoptUploadedFiles(ids)
    scope.stop()
    expect(discardUploadsMock).toHaveBeenCalledTimes(2)
    expect(discardUploadsMock).toHaveBeenNthCalledWith(1, ids.slice(0, 101), { skipGlobalErrorHandler: true })
    expect(discardUploadsMock).toHaveBeenNthCalledWith(2, [102], { skipGlobalErrorHandler: true })
  })

  it('does not discard files transferred to a server draft or published post', () => {
    const { scope, ownership } = createOwnership()
    ownership.adoptUploadedFiles([51, 52])
    ownership.releaseUploadedFiles([51, 52])
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalled()
  })

  it('never discards files that this session did not upload', async () => {
    const { scope, identity, content } = createOwnership()
    content.value = '<img src="/api/v1/files/77">'
    await nextTick()
    content.value = ''
    await nextTick()
    identity.value = 'session-1:edit:free:2'
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalled()
  })

  it('preserves every file in the last successful recovery snapshot when newer persistence fails', () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    ownership.adoptUploadedFiles([81, 82])
    durableDraftFileIds.value = [81, 82]
    content.value = encodeSandboxedPostHtml('<style>.gallery{display:grid}</style><img src="/api/v1/files/82">')
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalled()
  })

  it('discards removed files once a replacement recovery snapshot has been stored', () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    ownership.adoptUploadedFiles([81, 82])
    durableDraftFileIds.value = [81, 82]
    content.value = '<img src="/api/v1/files/82">'
    durableDraftFileIds.value = [82]
    scope.stop()
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([81], { skipGlobalErrorHandler: true })
  })

  it('discards referenced uploads when the local recovery snapshot could not be saved', () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(91)
    content.value = '<img src="/api/v1/files/91">'
    scope.stop()
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([91], { skipGlobalErrorHandler: true })
  })

  it('does not restore old ownership after an identity boundary or cleanup failure', async () => {
    const request = createDeferred<unknown>()
    discardUploadsMock.mockReturnValueOnce(request.promise)
    const { scope, identity, content, durableDraftFileIds, ownership } = createOwnership()
    ownership.recordUploadedFile(81)
    identity.value = 'session-2:create:free:new'
    ownership.recordUploadedFile(82)
    content.value = '<img src="/api/v1/files/82">'
    durableDraftFileIds.value = [82]
    request.reject(new Error('old cleanup failed'))
    await vi.advanceTimersByTimeAsync(10_000)
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([81], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([82])
    scope.stop()
    expect(discardUploadsMock).toHaveBeenCalledTimes(1)
  })

  it('uses server expiry when terminal cleanup is offline or fails', async () => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
    const offline = createOwnership()
    offline.ownership.recordUploadedFile(81)
    offline.scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalled()
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
    discardUploadsMock.mockRejectedValueOnce(new Error('temporary failure'))
    const failed = createOwnership()
    failed.ownership.recordUploadedFile(91)
    failed.scope.stop()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(discardUploadsMock).toHaveBeenCalledTimes(1)
    expect(failed.ownership.ownedUploadedFileIds.value).toEqual([])
  })
})

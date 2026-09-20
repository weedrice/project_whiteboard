import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS,
  usePostComposerUploadOwnership,
} from '@/features/board/posts/form/usePostComposerUploadOwnership'
import { encodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { createDeferred } from '@/test/async'

const discardUploadsMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/file', () => ({
  fileApi: {
    discardUploads: discardUploadsMock,
  },
}))

vi.mock('@/utils/logger', () => ({
  default: { warn: vi.fn() },
}))

function createOwnership() {
  const scope = effectScope()
  const identity = ref('session-1:create:free:new')
  const content = ref('')
  const durableDraftFileIds = ref<number[]>([])
  const ownership = scope.run(() => usePostComposerUploadOwnership({
    identity,
    content,
    durableDraftFileIds,
  }))
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

  it('discards only current-session uploads removed from post content', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(41)
    content.value = '<p><img data-file-id="41" src="/api/v1/files/41"></p>'
    await nextTick()

    content.value = '<p>image removed</p>'
    await nextTick()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(discardUploadsMock).toHaveBeenCalledWith([41], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    scope.stop()
  })

  it('batches simultaneous removals with one content parse and one request', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.adoptUploadedFiles([41, 42, 43])
    content.value = '<p>three images removed</p>'
    await nextTick()
    const parseContent = vi.spyOn(DOMParser.prototype, 'parseFromString')

    expect(vi.getTimerCount()).toBe(1)
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(parseContent).toHaveBeenCalledTimes(1)
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([41, 42, 43], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    expect(vi.getTimerCount()).toBe(0)
    scope.stop()
  })

  it('splits simultaneous removals at the API limit while parsing content only once', async () => {
    const { scope, ownership } = createOwnership()
    const fileIds = Array.from({ length: 102 }, (_, index) => index + 1)
    ownership.adoptUploadedFiles(fileIds)
    ownership.discardUnreferencedUploads()
    const parseContent = vi.spyOn(DOMParser.prototype, 'parseFromString')
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(parseContent).toHaveBeenCalledTimes(1)
    expect(discardUploadsMock).toHaveBeenCalledTimes(2)
    expect(discardUploadsMock).toHaveBeenNthCalledWith(1, fileIds.slice(0, 101), { skipGlobalErrorHandler: true })
    expect(discardUploadsMock).toHaveBeenNthCalledWith(2, [102], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    expect(vi.getTimerCount()).toBe(0)
    scope.stop()
  })

  it('preserves individual deadlines without delaying earlier removals or discarding later ones early', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.adoptUploadedFiles([51, 52])
    content.value = '<img src="/api/v1/files/52">'
    await nextTick()
    await vi.advanceTimersByTimeAsync(500)
    content.value = '<p>second image removed</p>'
    await nextTick()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS - 500)

    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([51], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([52])
    await vi.advanceTimersByTimeAsync(499)
    expect(discardUploadsMock).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(discardUploadsMock).toHaveBeenNthCalledWith(2, [52], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    scope.stop()
  })

  it('keeps the discard delay stable when the system clock moves backwards', async () => {
    const { scope, ownership } = createOwnership()
    ownership.adoptUploadedFiles([51, 52])
    ownership.discardUnreferencedUploads()
    vi.setSystemTime(Date.now() - 60_000)
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS - 1)

    expect(discardUploadsMock).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([51, 52], { skipGlobalErrorHandler: true })
    scope.stop()
  })

  it('excludes reinserted and released uploads from a pending batch', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.adoptUploadedFiles([61, 62, 63])
    ownership.discardUnreferencedUploads()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS - 1)
    content.value = '<img src="/api/v1/files/62">'
    await nextTick()
    ownership.releaseUploadedFiles([63])
    await vi.advanceTimersByTimeAsync(1)

    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([61], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([62])
    scope.stop()
  })

  it('restores only retained ownership when an in-flight batch fails after a release', async () => {
    const request = createDeferred<unknown>()
    discardUploadsMock.mockReturnValueOnce(request.promise)
    const { scope, ownership } = createOwnership()
    ownership.adoptUploadedFiles([71, 72])
    ownership.discardUnreferencedUploads()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)
    ownership.releaseUploadedFiles([72])
    request.reject(new Error('temporary failure'))
    await vi.advanceTimersByTimeAsync(10_000)

    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([71, 72], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([71])
    expect(vi.getTimerCount()).toBe(0)
    scope.stop()
  })

  it('does not restore an old failed batch after identity changes', async () => {
    const request = createDeferred<unknown>()
    discardUploadsMock.mockReturnValueOnce(request.promise)
    const { scope, identity, durableDraftFileIds, ownership } = createOwnership()
    ownership.adoptUploadedFiles([81, 82])
    ownership.discardUnreferencedUploads()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)
    identity.value = 'session-2:create:free:new'
    ownership.recordUploadedFile(83)
    request.reject(new Error('old request failed'))
    await vi.advanceTimersByTimeAsync(10_000)

    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([81, 82], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([83])
    durableDraftFileIds.value = [83]
    scope.stop()
  })

  it('does not restore an in-flight batch that fails after disposal', async () => {
    const request = createDeferred<unknown>()
    discardUploadsMock.mockReturnValueOnce(request.promise)
    const { scope, ownership } = createOwnership()
    ownership.adoptUploadedFiles([81, 82])
    ownership.discardUnreferencedUploads()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)
    scope.stop()
    request.reject(new Error('disposed request failed'))
    await vi.advanceTimersByTimeAsync(10_000)

    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([81, 82], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    expect(vi.getTimerCount()).toBe(0)
  })

  it('cancels pending deadlines at disposal and hands durable uploads off before terminal batching', async () => {
    const { scope, durableDraftFileIds, ownership } = createOwnership()
    ownership.adoptUploadedFiles([91, 92, 93])
    ownership.discardUnreferencedUploads()
    durableDraftFileIds.value = [92]
    scope.stop()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(discardUploadsMock).toHaveBeenCalledExactlyOnceWith([91, 93], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    expect(vi.getTimerCount()).toBe(0)
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

  it('adopts recovered unassociated uploads and hands durable files to the draft', async () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    content.value = '<img src="/api/v1/files/81"><img src="/api/v1/files/82">'
    ownership.adoptUploadedFiles([81, 82, 81])

    content.value = '<img src="/api/v1/files/82">'
    await nextTick()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(discardUploadsMock).toHaveBeenCalledWith([81], { skipGlobalErrorHandler: true })
    durableDraftFileIds.value = [82]
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalledWith([82], expect.anything())
  })

  it('cancels a pending discard when an upload is referenced again', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(64)
    ownership.discardUnreferencedUploads()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS - 1)
    content.value = '<img src="/api/v1/files/64">'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1)

    expect(discardUploadsMock).not.toHaveBeenCalled()
    expect(ownership.ownedUploadedFileIds.value).toEqual([64])
    scope.stop()
  })

  it('keeps an upload referenced inside preserved html', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(65)
    content.value = encodeSandboxedPostHtml([
      '<style>.gallery{display:grid}</style>',
      '<img src="/api/v1/files/65">',
    ].join(''))
    await nextTick()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(discardUploadsMock).not.toHaveBeenCalled()
    expect(ownership.ownedUploadedFileIds.value).toEqual([65])
    scope.stop()
  })

  it('keeps a failed non-terminal discard available for a later user action without auto retrying', async () => {
    discardUploadsMock.mockRejectedValueOnce(new Error('network unavailable'))
    const { scope, ownership } = createOwnership()
    ownership.adoptUploadedFiles([71, 72, 73])

    ownership.discardUnreferencedUploads('')
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)
    await vi.runAllTicks()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(discardUploadsMock).toHaveBeenCalledTimes(1)
    expect(discardUploadsMock).toHaveBeenCalledWith([71, 72, 73], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([71, 72, 73])
    scope.stop()
  })

  it('does not persist or beacon terminal cleanup while offline', () => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
    const { scope, ownership } = createOwnership()
    ownership.recordUploadedFile(81)

    scope.stop()

    expect(discardUploadsMock).not.toHaveBeenCalled()
    expect(localStorage.length).toBe(0)
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
  })

  it('does not restore a failed terminal cleanup after the editor is disposed', async () => {
    discardUploadsMock.mockRejectedValueOnce(new Error('temporary failure'))
    const { scope, ownership } = createOwnership()
    ownership.recordUploadedFile(91)

    scope.stop()
    await vi.runAllTicks()

    expect(discardUploadsMock).toHaveBeenCalledWith([91], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
  })
})

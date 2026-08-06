import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS,
  usePostComposerUploadOwnership,
} from '@/features/board/posts/form/usePostComposerUploadOwnership'

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
    content.value = ''
    await nextTick()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS - 1)
    content.value = '<img src="/api/v1/files/64">'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1)

    expect(discardUploadsMock).not.toHaveBeenCalled()
    expect(ownership.ownedUploadedFileIds.value).toEqual([64])
    scope.stop()
  })

  it('keeps a failed non-terminal discard available for a later user action without auto retrying', async () => {
    discardUploadsMock.mockRejectedValueOnce(new Error('network unavailable'))
    const { scope, ownership } = createOwnership()
    ownership.recordUploadedFile(71)

    ownership.discardUnreferencedUploads('')
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)
    await vi.runAllTicks()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(discardUploadsMock).toHaveBeenCalledTimes(1)
    expect(ownership.ownedUploadedFileIds.value).toEqual([71])
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

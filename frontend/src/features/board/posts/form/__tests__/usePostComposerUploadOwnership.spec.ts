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
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('discards only current-session uploads removed from post content', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(41)
    content.value = '<p><img data-file-id="41" src="/api/v1/files/41"></p>'
    await nextTick()

    expect(discardUploadsMock).not.toHaveBeenCalled()

    content.value = '<p>image removed</p>'
    await nextTick()

    expect(discardUploadsMock).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(discardUploadsMock).toHaveBeenCalledWith([41], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    scope.stop()
  })

  it('never discards restored draft or existing post files that this session did not upload', async () => {
    const { scope, identity, content } = createOwnership()
    content.value = '<img src="/api/v1/files/77">'
    await nextTick()
    content.value = ''
    await nextTick()
    identity.value = 'session-1:edit:free:2'
    scope.stop()

    expect(discardUploadsMock).not.toHaveBeenCalled()
  })

  it('adopts recovered unassociated uploads and manages their remaining lifecycle', async () => {
    const { scope, content, durableDraftFileIds, ownership } = createOwnership()
    content.value = '<img src="/api/v1/files/81"><img src="/api/v1/files/82">'
    ownership.adoptUploadedFiles([81, 82, 81])

    expect(ownership.ownedUploadedFileIds.value).toEqual([81, 82])

    content.value = '<img src="/api/v1/files/82">'
    await nextTick()
    await vi.advanceTimersByTimeAsync(POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS)

    expect(discardUploadsMock).toHaveBeenCalledWith([81], { skipGlobalErrorHandler: true })
    durableDraftFileIds.value = [82]
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalledWith([82], expect.anything())
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
  })

  it('cancels a pending discard when an upload is referenced again', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(64)
    content.value = '<img src="/api/v1/files/64">'
    await nextTick()

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

  it('hands referenced uploads to local recovery and discards only unreferenced uploads on identity change', () => {
    const { scope, identity, content, durableDraftFileIds, ownership } = createOwnership()
    content.value = '<img src="/api/v1/files/61">'
    ownership.recordUploadedFile(61)
    ownership.recordUploadedFile(62)
    durableDraftFileIds.value = [61]

    identity.value = 'session-2:create:other:new'

    expect(discardUploadsMock).toHaveBeenCalledWith([62], { skipGlobalErrorHandler: true })
    expect(discardUploadsMock).not.toHaveBeenCalledWith([61], expect.anything())
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    scope.stop()
  })

  it('discards referenced uploads that were not durably stored in local recovery', () => {
    const { scope, content, ownership } = createOwnership()
    content.value = '<img src="/api/v1/files/63">'
    ownership.recordUploadedFile(63)

    scope.stop()

    expect(discardUploadsMock).toHaveBeenCalledWith([63], { skipGlobalErrorHandler: true })
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
  })

  it('releases server-owned uploads and discards remaining uploads on identity change or dispose', () => {
    const { scope, identity, ownership } = createOwnership()
    ownership.recordUploadedFile(51)
    ownership.recordUploadedFile(52)
    ownership.releaseUploadedFiles([51])

    identity.value = 'session-2:create:free:new'

    expect(discardUploadsMock).toHaveBeenCalledWith([52], { skipGlobalErrorHandler: true })
    expect(discardUploadsMock).not.toHaveBeenCalledWith([51], expect.anything())

    ownership.recordUploadedFile(53)
    scope.stop()
    expect(discardUploadsMock).toHaveBeenCalledWith([53], { skipGlobalErrorHandler: true })
  })

  it('restores upload ownership when discard fails so cleanup can be retried', async () => {
    discardUploadsMock.mockRejectedValueOnce(new Error('network unavailable'))
    const { scope, ownership } = createOwnership()
    ownership.recordUploadedFile(71)

    ownership.discardAllOwnedUploads()
    await Promise.resolve()
    expect(ownership.ownedUploadedFileIds.value).toEqual([71])

    ownership.discardAllOwnedUploads()
    await Promise.resolve()
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    expect(discardUploadsMock).toHaveBeenCalledTimes(2)
    scope.stop()
  })
})

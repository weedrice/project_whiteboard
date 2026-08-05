import { effectScope, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePostComposerUploadOwnership } from '@/features/board/posts/form/usePostComposerUploadOwnership'

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
  const ownership = scope.run(() => usePostComposerUploadOwnership({ identity, content }))
  if (!ownership) throw new Error('Upload ownership composable was not initialized')
  return { scope, identity, content, ownership }
}

describe('usePostComposerUploadOwnership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    discardUploadsMock.mockResolvedValue({ data: { data: { discardedCount: 1 } } })
  })

  it('discards only current-session uploads removed from post content', async () => {
    const { scope, content, ownership } = createOwnership()
    ownership.recordUploadedFile(41)
    content.value = '<p><img data-file-id="41" src="/api/v1/files/41"></p>'
    await nextTick()

    expect(discardUploadsMock).not.toHaveBeenCalled()

    content.value = '<p>image removed</p>'
    await nextTick()

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
    const { scope, content, ownership } = createOwnership()
    content.value = '<img src="/api/v1/files/81"><img src="/api/v1/files/82">'
    ownership.adoptUploadedFiles([81, 82, 81])

    expect(ownership.ownedUploadedFileIds.value).toEqual([81, 82])

    content.value = '<img src="/api/v1/files/82">'
    await nextTick()

    expect(discardUploadsMock).toHaveBeenCalledWith([81], { skipGlobalErrorHandler: true })
    scope.stop()
    expect(discardUploadsMock).not.toHaveBeenCalledWith([82], expect.anything())
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
  })

  it('hands referenced uploads to local recovery and discards only unreferenced uploads on identity change', () => {
    const { scope, identity, content, ownership } = createOwnership()
    content.value = '<img src="/api/v1/files/61">'
    ownership.recordUploadedFile(61)
    ownership.recordUploadedFile(62)

    identity.value = 'session-2:create:other:new'

    expect(discardUploadsMock).toHaveBeenCalledWith([62], { skipGlobalErrorHandler: true })
    expect(discardUploadsMock).not.toHaveBeenCalledWith([61], expect.anything())
    expect(ownership.ownedUploadedFileIds.value).toEqual([])
    scope.stop()
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
})

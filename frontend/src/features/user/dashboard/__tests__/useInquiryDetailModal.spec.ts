import { describe, expect, it, vi, beforeEach } from 'vitest'
import { effectScope } from 'vue'
import { useInquiryDetailModal } from '../useInquiryDetailModal'
import { postApi } from '@/api/post'
import { apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import type { Post } from '@/types'

const { toastMock, confirmMock } = vi.hoisted(() => ({
  toastMock: {
    addToast: vi.fn()
  },
  confirmMock: vi.fn()
}))
const authStore = vi.hoisted(() => ({ sessionGeneration: 0 }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStore,
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@/api/post', () => ({
  postApi: {
    getPost: vi.fn(),
    deletePost: vi.fn()
  }
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => toastMock
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: confirmMock
  })
}))

const signalConfig = {
  params: { incrementView: false },
  signal: expect.any(AbortSignal)
}

type GetPostResponse = Awaited<ReturnType<typeof postApi.getPost>>

describe('useInquiryDetailModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmMock.mockResolvedValue(true)
    authStore.sessionGeneration = 0
  })

  it('loads inquiry detail without incrementing view count', async () => {
    vi.mocked(postApi.getPost).mockResolvedValue(
      apiSuccessDataResponse<typeof postApi.getPost>({ postId: 11, title: 'Inquiry' })
    )
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)

    await modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })

    expect(postApi.getPost).toHaveBeenCalledWith(11, signalConfig)
    expect(modal.selectedInquiryPost.value?.postId).toBe(11)
    expect(modal.isInquiryDetailLoading.value).toBe(false)
  })

  it('ignores stale inquiry detail responses from earlier requests', async () => {
    const firstRequest = createDeferred<GetPostResponse>()
    const secondRequest = createDeferred<GetPostResponse>()
    vi.mocked(postApi.getPost)
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)

    const firstOpen = modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })
    const secondOpen = modal.openMyInquiryPost({ postId: 12, boardUrl: 'inquiry' })

    secondRequest.resolve(apiSuccessDataResponse<typeof postApi.getPost>({ postId: 12, title: 'Second' }))
    await secondOpen

    expect(modal.selectedInquiryPost.value?.postId).toBe(12)
    expect(modal.isInquiryDetailLoading.value).toBe(false)

    firstRequest.resolve(apiSuccessDataResponse<typeof postApi.getPost>({ postId: 11, title: 'First' }))
    await firstOpen

    expect(modal.selectedInquiryPost.value?.postId).toBe(12)
    expect(modal.isInquiryDetailLoading.value).toBe(false)
  })

  it('aborts the active inquiry detail request when the modal closes', async () => {
    const request = createDeferred<GetPostResponse>()
    vi.mocked(postApi.getPost).mockReturnValueOnce(request.promise)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)

    const open = modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })
    const signal = vi.mocked(postApi.getPost).mock.calls[0][1]?.signal

    modal.closeInquiryModal()
    request.resolve(apiSuccessDataResponse<typeof postApi.getPost>({ postId: 11, title: 'Inquiry' }))
    await open

    expect(signal?.aborted).toBe(true)
    expect(modal.selectedInquiryPost.value).toBeNull()
    expect(modal.isInquiryDetailLoading.value).toBe(false)
  })

  it('aborts the previous inquiry detail request when opening another post', async () => {
    const firstRequest = createDeferred<GetPostResponse>()
    const secondRequest = createDeferred<GetPostResponse>()
    vi.mocked(postApi.getPost)
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)

    const firstOpen = modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })
    const firstSignal = vi.mocked(postApi.getPost).mock.calls[0][1]?.signal
    const secondOpen = modal.openMyInquiryPost({ postId: 12, boardUrl: 'inquiry' })
    const secondSignal = vi.mocked(postApi.getPost).mock.calls[1][1]?.signal

    secondRequest.resolve(apiSuccessDataResponse<typeof postApi.getPost>({ postId: 12, title: 'Second' }))
    await secondOpen
    firstRequest.resolve(apiSuccessDataResponse<typeof postApi.getPost>({ postId: 11, title: 'First' }))
    await firstOpen

    expect(firstSignal?.aborted).toBe(true)
    expect(secondSignal?.aborted).toBe(false)
    expect(modal.selectedInquiryPost.value?.postId).toBe(12)
  })

  it('aborts the active inquiry detail request when its scope is disposed', async () => {
    const request = createDeferred<GetPostResponse>()
    vi.mocked(postApi.getPost).mockReturnValueOnce(request.promise)
    const refreshPosts = vi.fn()
    const scope = effectScope()
    const modal = scope.run(() => useInquiryDetailModal(refreshPosts))!

    const open = modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })
    const signal = vi.mocked(postApi.getPost).mock.calls[0][1]?.signal

    scope.stop()
    request.resolve(apiSuccessDataResponse<typeof postApi.getPost>({ postId: 11, title: 'Inquiry' }))
    await open

    expect(signal?.aborted).toBe(true)
    expect(modal.selectedInquiryPost.value).toBeNull()
  })

  it('refreshes the list once after deleting an inquiry', async () => {
    vi.mocked(postApi.deletePost).mockResolvedValue(apiSuccessResponse<typeof postApi.deletePost>())
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 12 } as Post

    await modal.deleteInquiryPost()

    expect(confirmMock).toHaveBeenCalledWith('common.messages.confirmDelete')
    expect(postApi.deletePost).toHaveBeenCalledWith(12, { signal: expect.any(AbortSignal) })
    expect(refreshPosts).toHaveBeenCalledTimes(1)
    expect(toastMock.addToast).toHaveBeenCalledWith('common.messages.deleteSuccess', 'success')
  })

  it('discards a delete result after the account session changes', async () => {
    const request = createDeferred<Awaited<ReturnType<typeof postApi.deletePost>>>()
    vi.mocked(postApi.deletePost).mockReturnValue(request.promise)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 12 } as Post

    const deletion = modal.deleteInquiryPost()
    await Promise.resolve()
    authStore.sessionGeneration += 1
    request.resolve(apiSuccessResponse<typeof postApi.deletePost>())
    await deletion

    expect(refreshPosts).not.toHaveBeenCalled()
    expect(toastMock.addToast).not.toHaveBeenCalled()
    expect(modal.selectedInquiryPost.value?.postId).toBe(12)
  })

  it('does not delete after confirm resolves across an account or selection change', async () => {
    const confirmation = createDeferred<boolean>()
    confirmMock.mockReturnValue(confirmation.promise)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 12 } as Post

    const deletion = modal.deleteInquiryPost()
    await Promise.resolve()
    authStore.sessionGeneration += 1
    modal.selectedInquiryPost.value = { postId: 99 } as Post
    confirmation.resolve(true)
    await deletion

    expect(postApi.deletePost).not.toHaveBeenCalled()
    expect(refreshPosts).not.toHaveBeenCalled()
    expect(toastMock.addToast).not.toHaveBeenCalled()
  })

  it('does not delete an inquiry when confirm is cancelled', async () => {
    confirmMock.mockResolvedValue(false)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 13 } as Post

    await modal.deleteInquiryPost()

    expect(postApi.deletePost).not.toHaveBeenCalled()
    expect(refreshPosts).not.toHaveBeenCalled()
  })
})

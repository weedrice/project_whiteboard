import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useInquiryDetailModal } from '../useInquiryDetailModal'
import { postApi } from '@/api/post'

const { toastMock, confirmMock } = vi.hoisted(() => ({
  toastMock: {
    addToast: vi.fn()
  },
  confirmMock: vi.fn()
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

const createDeferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })

  return { promise, resolve, reject }
}

describe('useInquiryDetailModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmMock.mockResolvedValue(true)
  })

  it('loads inquiry detail without incrementing view count', async () => {
    vi.mocked(postApi.getPost).mockResolvedValue({
      data: { success: true, data: { postId: 11, title: 'Inquiry' } }
    } as never)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)

    await modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })

    expect(postApi.getPost).toHaveBeenCalledWith(11, { params: { incrementView: false } })
    expect(modal.selectedInquiryPost.value?.postId).toBe(11)
    expect(modal.isInquiryDetailLoading.value).toBe(false)
  })

  it('ignores stale inquiry detail responses from earlier requests', async () => {
    const firstRequest = createDeferred<{
      data: { success: boolean; data: { postId: number; title: string } }
    }>()
    const secondRequest = createDeferred<{
      data: { success: boolean; data: { postId: number; title: string } }
    }>()
    vi.mocked(postApi.getPost)
      .mockReturnValueOnce(firstRequest.promise as never)
      .mockReturnValueOnce(secondRequest.promise as never)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)

    const firstOpen = modal.openMyInquiryPost({ postId: 11, boardUrl: 'inquiry' })
    const secondOpen = modal.openMyInquiryPost({ postId: 12, boardUrl: 'inquiry' })

    secondRequest.resolve({
      data: { success: true, data: { postId: 12, title: 'Second' } }
    })
    await secondOpen

    expect(modal.selectedInquiryPost.value?.postId).toBe(12)
    expect(modal.isInquiryDetailLoading.value).toBe(false)

    firstRequest.resolve({
      data: { success: true, data: { postId: 11, title: 'First' } }
    })
    await firstOpen

    expect(modal.selectedInquiryPost.value?.postId).toBe(12)
    expect(modal.isInquiryDetailLoading.value).toBe(false)
  })

  it('keeps the existing close and delete refresh sequence', async () => {
    vi.mocked(postApi.deletePost).mockResolvedValue({ data: { success: true } } as never)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 12 } as never

    await modal.deleteInquiryPost()

    expect(confirmMock).toHaveBeenCalledWith('common.messages.confirmDelete')
    expect(postApi.deletePost).toHaveBeenCalledWith(12)
    expect(refreshPosts).toHaveBeenCalledTimes(2)
    expect(toastMock.addToast).toHaveBeenCalledWith('common.messages.deleteSuccess', 'success')
  })

  it('does not delete an inquiry when confirm is cancelled', async () => {
    confirmMock.mockResolvedValue(false)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 13 } as never

    await modal.deleteInquiryPost()

    expect(postApi.deletePost).not.toHaveBeenCalled()
    expect(refreshPosts).not.toHaveBeenCalled()
  })
})

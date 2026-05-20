import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useInquiryDetailModal } from '../useInquiryDetailModal'
import { postApi } from '@/api/post'

const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
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

describe('useInquiryDetailModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
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

  it('keeps the existing close and delete refresh sequence', async () => {
    vi.mocked(postApi.deletePost).mockResolvedValue({ data: { success: true } } as never)
    const refreshPosts = vi.fn()
    const modal = useInquiryDetailModal(refreshPosts)
    modal.selectedInquiryPost.value = { postId: 12 } as never

    await modal.deleteInquiryPost()

    expect(postApi.deletePost).toHaveBeenCalledWith(12)
    expect(refreshPosts).toHaveBeenCalledTimes(2)
    expect(toastMock.addToast).toHaveBeenCalledWith('common.messages.deleteSuccess', 'success')
  })
})

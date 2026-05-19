import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import type { Post } from '@/types'

export function useInquiryDetailModal(refreshPosts: () => Promise<void> | void) {
  const { t } = useI18n()
  const toastStore = useToastStore()

  const isInquiryDetailOpen = ref(false)
  const selectedInquiryPost = ref<Post | null>(null)
  const isInquiryDetailLoading = ref(false)
  const inquiryDetailError = ref('')
  const isDeletingInquiry = ref(false)

  function isInquiryPostItem(post: { boardUrl?: string | number }): boolean {
    return String(post.boardUrl || '').toLowerCase() === 'inquiry'
  }

  async function openMyInquiryPost(post: { postId: number; boardUrl?: string | number }) {
    if (!isInquiryPostItem(post)) {
      return
    }

    isInquiryDetailOpen.value = true
    selectedInquiryPost.value = null
    inquiryDetailError.value = ''
    isInquiryDetailLoading.value = true

    try {
      const { data } = await postApi.getPost(post.postId, { params: { incrementView: false } })
      if (data.success) {
        selectedInquiryPost.value = data.data
      }
    } catch (err: unknown) {
      inquiryDetailError.value = extractErrorMessage(err) || t('common.messages.loadFailed')
    } finally {
      isInquiryDetailLoading.value = false
    }
  }

  function closeInquiryModal() {
    isInquiryDetailOpen.value = false
    selectedInquiryPost.value = null
    inquiryDetailError.value = ''
    void refreshPosts()
  }

  async function deleteInquiryPost() {
    const post = selectedInquiryPost.value
    if (!post) return
    if (!window.confirm('문의를 삭제하시겠습니까?')) {
      return
    }

    isDeletingInquiry.value = true
    try {
      await postApi.deletePost(post.postId)
      toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
      closeInquiryModal()
      await refreshPosts()
    } catch (err: unknown) {
      toastStore.addToast(extractErrorMessage(err) || t('common.messages.deleteFailed'), 'error')
    } finally {
      isDeletingInquiry.value = false
    }
  }

  return {
    isInquiryDetailOpen,
    selectedInquiryPost,
    isInquiryDetailLoading,
    inquiryDetailError,
    isDeletingInquiry,
    isInquiryPostItem,
    openMyInquiryPost,
    closeInquiryModal,
    deleteInquiryPost
  }
}

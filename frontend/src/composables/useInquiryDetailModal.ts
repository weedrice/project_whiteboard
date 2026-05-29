import { onScopeDispose, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { isInquiryPostItem } from '@/utils/postNavigation'
import type { Post } from '@/types'

export function useInquiryDetailModal(refreshPosts: () => Promise<void> | void) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { confirm } = useConfirm()

  const isInquiryDetailOpen = ref(false)
  const selectedInquiryPost = ref<Post | null>(null)
  const isInquiryDetailLoading = ref(false)
  const inquiryDetailError = ref('')
  const isDeletingInquiry = ref(false)
  let inquiryDetailRequestId = 0
  let inquiryDetailAbortController: AbortController | null = null

  function abortInquiryDetailRequest() {
    inquiryDetailAbortController?.abort()
    inquiryDetailAbortController = null
    inquiryDetailRequestId += 1
  }

  async function openMyInquiryPost(post: { postId: number; boardUrl?: string | number }) {
    if (!isInquiryPostItem(post)) {
      return
    }

    isInquiryDetailOpen.value = true
    selectedInquiryPost.value = null
    inquiryDetailError.value = ''
    isInquiryDetailLoading.value = true
    abortInquiryDetailRequest()
    const requestId = inquiryDetailRequestId
    const controller = new AbortController()
    inquiryDetailAbortController = controller

    try {
      const { data } = await postApi.getPost(post.postId, {
        params: { incrementView: false },
        signal: controller.signal
      })
      if (requestId === inquiryDetailRequestId && data.success) {
        selectedInquiryPost.value = data.data
      }
    } catch (err: unknown) {
      if (requestId === inquiryDetailRequestId && !controller.signal.aborted) {
        inquiryDetailError.value = extractErrorMessage(err) || t('common.messages.loadFailed')
      }
    } finally {
      if (inquiryDetailAbortController === controller) {
        inquiryDetailAbortController = null
      }
      if (requestId === inquiryDetailRequestId) {
        isInquiryDetailLoading.value = false
      }
    }
  }

  function closeInquiryModal() {
    abortInquiryDetailRequest()
    isInquiryDetailOpen.value = false
    selectedInquiryPost.value = null
    inquiryDetailError.value = ''
    isInquiryDetailLoading.value = false
    void refreshPosts()
  }

  async function deleteInquiryPost() {
    const post = selectedInquiryPost.value
    if (!post) return
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return

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

  onScopeDispose(abortInquiryDetailRequest, true)

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

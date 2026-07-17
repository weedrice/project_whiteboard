import { onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { unwrapApiData } from '@/api/response'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { isInquiryPostItem } from '@/utils/postNavigation'
import type { Post } from '@/types'
import { useAuthStore } from '@/stores/auth'

export function useInquiryDetailModal(refreshPosts: () => Promise<void> | void) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { confirm } = useConfirm()
  const authStore = useAuthStore()

  const isInquiryDetailOpen = ref(false)
  const selectedInquiryPost = ref<Post | null>(null)
  const isInquiryDetailLoading = ref(false)
  const inquiryDetailError = ref('')
  const isDeletingInquiry = ref(false)
  let inquiryDetailRequestId = 0
  let inquiryDetailAbortController: AbortController | null = null
  let inquiryDeleteAbortController: AbortController | null = null

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
    const generation = authStore.sessionGeneration

    try {
      const { data } = await postApi.getPost(post.postId, {
        params: { incrementView: false },
        signal: controller.signal
      })
      if (requestId === inquiryDetailRequestId
        && generation === authStore.sessionGeneration
        && data.success) {
        selectedInquiryPost.value = unwrapApiData(data)
      }
    } catch (err: unknown) {
      if (requestId === inquiryDetailRequestId
        && generation === authStore.sessionGeneration
        && !controller.signal.aborted) {
        inquiryDetailError.value = extractErrorMessage(err) || t('common.messages.loadFailed')
      }
    } finally {
      if (inquiryDetailAbortController === controller) {
        inquiryDetailAbortController = null
      }
      if (requestId === inquiryDetailRequestId && generation === authStore.sessionGeneration) {
        isInquiryDetailLoading.value = false
      }
    }
  }

  function closeInquiryModal(refresh = true) {
    abortInquiryDetailRequest()
    isInquiryDetailOpen.value = false
    selectedInquiryPost.value = null
    inquiryDetailError.value = ''
    isInquiryDetailLoading.value = false
    if (refresh) void refreshPosts()
  }

  async function deleteInquiryPost() {
    const post = selectedInquiryPost.value
    if (!post) return
    const generation = authStore.sessionGeneration
    const selectedPostId = post.postId
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return
    if (generation !== authStore.sessionGeneration
      || selectedInquiryPost.value?.postId !== selectedPostId) return

    inquiryDeleteAbortController?.abort()
    const controller = new AbortController()
    inquiryDeleteAbortController = controller
    isDeletingInquiry.value = true
    try {
      await postApi.deletePost(post.postId, { signal: controller.signal })
      if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
      toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
      closeInquiryModal(false)
      await refreshPosts()
    } catch (err: unknown) {
      if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
      toastStore.addToast(extractErrorMessage(err) || t('common.messages.deleteFailed'), 'error')
    } finally {
      if (inquiryDeleteAbortController === controller) inquiryDeleteAbortController = null
      if (generation === authStore.sessionGeneration) isDeletingInquiry.value = false
    }
  }

  watch(() => authStore.sessionGeneration, () => {
    abortInquiryDetailRequest()
    inquiryDeleteAbortController?.abort()
    inquiryDeleteAbortController = null
    isInquiryDetailOpen.value = false
    selectedInquiryPost.value = null
    inquiryDetailError.value = ''
    isInquiryDetailLoading.value = false
    isDeletingInquiry.value = false
  })

  onScopeDispose(() => {
    abortInquiryDetailRequest()
    inquiryDeleteAbortController?.abort()
  }, true)

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

import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AxiosError } from 'axios'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import { unwrapApiData } from '@/api/response'
import { useConfirm } from '@/composables/useConfirm'
import { useMailboxListState } from '@/features/user/messages/useMailboxListState'
import { useMessageSubmit } from '@/features/user/messages/useMessageSubmit'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import type { MailboxMessageViewModel } from '@/types'
import { extractErrorResponse } from '@/utils/errorHandler'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'
import logger from '@/utils/logger'

const NOT_FOUND_CODE = 'C006'

export function useMailboxResource() {
    const { t } = useI18n()
    const toastStore = useToastStore()
    const authStore = useAuthStore()
    const { confirm } = useConfirm()

    const {
        viewType,
        messages,
        loading,
        error,
        selectedMessages,
        page,
        size,
        totalPages,
        fetchMessages,
        handlePageChange,
        handleSizeChange,
        changeViewType,
        markListMessageRead,
        resetMailboxState,
    } = useMailboxListState()
    const selectedMessage = ref<MailboxMessageViewModel | null>(null)
    const selectedConversationMessages = ref<MailboxMessageViewModel[]>([])
    const messageDetailLoading = ref(false)
    const messageDetailError = ref<string | null>(null)
    const conversationLoading = ref(false)
    const conversationError = ref<string | null>(null)
    const lastConversationPartnerId = ref<number | null>(null)

    const isReplyModalOpen = ref(false)
    const replyTarget = ref<MailboxMessageViewModel | null>(null)
    const {
        content: replyContent,
        isSending,
        send: sendReply,
        reset: resetReplyContent,
    } = useMessageSubmit({
        getReceiverId: () => replyTarget.value?.partnerUserId ?? selectedMessage.value?.partnerUserId,
        logMessage: 'Failed to send reply:',
        onSuccess: () => {
            void refreshConversationAfterReply()
        },
    })
    /** Block relationship can make detail/read fail; show toast only when user attempts reply. */
    const messageFromBlockedUser = ref(false)
    let messageDetailRequestId = 0
    let messageDetailAbortController: AbortController | null = null
    let conversationRefreshRequestId = 0
    let conversationRefreshAbortController: AbortController | null = null
    let deleteRequestAbortController: AbortController | null = null
    const markAsReadAbortControllers = new Set<AbortController>()

    function abortMessageDetailRequest() {
        messageDetailAbortController?.abort()
        messageDetailAbortController = null
    }

    function abortMarkAsReadRequests() {
        markAsReadAbortControllers.forEach((controller) => controller.abort())
        markAsReadAbortControllers.clear()
    }

    function abortConversationRefresh() {
        conversationRefreshRequestId++
        conversationRefreshAbortController?.abort()
        conversationRefreshAbortController = null
    }

    function abortDeleteRequest() {
        deleteRequestAbortController?.abort()
        deleteRequestAbortController = null
    }

    function isStaleMessageDetail(requestId: number, messageId: number) {
        return requestId !== messageDetailRequestId || selectedMessage.value?.id !== messageId
    }

    function startMessageDetailRequest(msg: MailboxMessageViewModel) {
        const requestId = ++messageDetailRequestId
        abortMessageDetailRequest()
        const controller = new AbortController()
        messageDetailAbortController = controller
        const messageId = msg.id
        messageFromBlockedUser.value = false
        selectedMessage.value = msg
        selectedConversationMessages.value = []
        messageDetailLoading.value = true
        messageDetailError.value = null
        conversationLoading.value = true
        conversationError.value = null
        lastConversationPartnerId.value = msg.partnerUserId
        return { requestId, messageId, controller }
    }

    async function loadMessageDetail(messageId: number, controller: AbortController) {
        const { data } = await messageApi.getMessage(messageId, {
            skipGlobalErrorHandler: true,
            signal: controller.signal
        })
        const message = unwrapApiData(data)
        if (data.success && message) {
            return toMailboxMessageViewModel(message)
        }
        return null
    }

    async function markMessageAsReadIfNeeded(messageId: number, wasUnread: boolean, requestId: number) {
        if (viewType.value === 'sent' || !wasUnread || selectedMessage.value?.sentByMe) {
            return
        }

        const markAsReadController = new AbortController()
        markAsReadAbortControllers.add(markAsReadController)
        try {
            await messageApi.markAsRead(messageId, {
                skipGlobalErrorHandler: true,
                signal: markAsReadController.signal
            })
        } finally {
            markAsReadAbortControllers.delete(markAsReadController)
        }
        markListMessageRead(messageId)
        if (isStaleMessageDetail(requestId, messageId)) {
            return
        }
        if (selectedMessage.value?.id === messageId) {
            selectedMessage.value = markMailboxMessageRead(selectedMessage.value)
        }
    }

    async function handleMessageDetailError(
        error: unknown,
        requestId: number,
        messageId: number,
        controller: AbortController
    ) {
        if (isStaleMessageDetail(requestId, messageId) || controller.signal.aborted) {
            return
        }

        const errRes = extractErrorResponse(error as AxiosError)
        if (errRes?.code === BLOCKED_BY_USER_CODE) {
            messageFromBlockedUser.value = true
        } else if (errRes?.code === NOT_FOUND_CODE) {
            selectedMessage.value = null
            await fetchMessages()
            toastStore.addToast(t('common.messages.notFound'), 'info')
        } else {
            logger.error('Failed to open message:', error)
            messageDetailError.value = t('user.message.detailLoadFailed')
        }
    }

    async function openMessage(msg: MailboxMessageViewModel) {
        const { requestId, messageId, controller } = startMessageDetailRequest(msg)
        const detailTask = (async () => {
            try {
                const detail = await loadMessageDetail(messageId, controller)
                if (isStaleMessageDetail(requestId, messageId)) return
                if (detail) selectedMessage.value = detail
                messageDetailError.value = null
                try {
                    await markMessageAsReadIfNeeded(messageId, msg.isUnread, requestId)
                } catch (error) {
                    await handleMessageDetailError(error, requestId, messageId, controller)
                }
            } catch (error) {
                await handleMessageDetailError(error, requestId, messageId, controller)
            } finally {
                if (!isStaleMessageDetail(requestId, messageId)) messageDetailLoading.value = false
            }
        })()
        const conversationTask = (async () => {
            try {
                const conversation = await loadConversationMessages(msg.partnerUserId, controller)
                if (isStaleMessageDetail(requestId, messageId)) return
                selectedConversationMessages.value = conversation
                conversationError.value = null
            } catch (error) {
                if (!isStaleMessageDetail(requestId, messageId) && !controller.signal.aborted) {
                    logger.error('Failed to load message conversation:', error)
                    conversationError.value = t('user.message.conversationLoadFailed')
                }
            } finally {
                if (!isStaleMessageDetail(requestId, messageId)) conversationLoading.value = false
            }
        })()

        try {
            await Promise.all([detailTask, conversationTask])
        } finally {
            if (messageDetailAbortController === controller) {
                messageDetailAbortController = null
            }
        }
    }

    async function loadConversationMessages(partnerId: number, controller: AbortController) {
        const { data } = await messageApi.getConversation(partnerId, {
            page: 0,
            size: 50,
            sort: 'createdAt,desc',
        }, {
            skipGlobalErrorHandler: true,
            signal: controller.signal,
        })
        const messagePage = unwrapApiData(data)
        if (data.success && messagePage) {
            return messagePage.content.map(toMailboxMessageViewModel).reverse()
        }
        return []
    }

    async function refreshConversationAfterReply() {
        const partnerId = replyTarget.value?.partnerUserId ?? selectedMessage.value?.partnerUserId
        if (partnerId == null) return

        abortConversationRefresh()
        const requestId = conversationRefreshRequestId
        const generation = authStore.sessionGeneration
        const controller = new AbortController()
        conversationRefreshAbortController = controller
        try {
            const conversation = await loadConversationMessages(partnerId, controller)
            if (
                controller.signal.aborted
                || requestId !== conversationRefreshRequestId
                || generation !== authStore.sessionGeneration
            ) return
            selectedConversationMessages.value = conversation
            await fetchMessages()
        } catch (error) {
            if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
            logger.error('Failed to refresh message conversation:', error)
        } finally {
            if (conversationRefreshAbortController === controller) {
                conversationRefreshAbortController = null
            }
            if (requestId === conversationRefreshRequestId && generation === authStore.sessionGeneration) {
                replyTarget.value = selectedMessage.value
            }
        }
    }

    async function openConversationByPartnerId(partnerId: number) {
        const requestId = ++messageDetailRequestId
        abortMessageDetailRequest()
        const controller = new AbortController()
        messageDetailAbortController = controller
        messageFromBlockedUser.value = false
        selectedMessage.value = null
        selectedConversationMessages.value = []
        conversationLoading.value = true
        conversationError.value = null
        lastConversationPartnerId.value = partnerId

        try {
            const conversation = await loadConversationMessages(partnerId, controller)
            if (requestId !== messageDetailRequestId || controller.signal.aborted) {
                return
            }
            selectedConversationMessages.value = conversation
            selectedMessage.value = conversation.length > 0 ? conversation[conversation.length - 1] : null
        } catch (error) {
            if (!controller.signal.aborted) {
                logger.error('Failed to open message conversation:', error)
                conversationError.value = t('user.message.conversationLoadFailed')
            }
        } finally {
            if (requestId === messageDetailRequestId) conversationLoading.value = false
            if (messageDetailAbortController === controller) {
                messageDetailAbortController = null
            }
        }
    }

    function retryMessageDetail() {
        if (selectedMessage.value) void openMessage(selectedMessage.value)
    }

    function retryConversation() {
        if (selectedMessage.value) {
            void openMessage(selectedMessage.value)
        } else if (lastConversationPartnerId.value != null) {
            void openConversationByPartnerId(lastConversationPartnerId.value)
        }
    }

    async function deleteSelectedMessages() {
        const generation = authStore.sessionGeneration
        const isConfirmed = await confirm(t('common.messages.confirmDelete'))
        if (!isConfirmed || generation !== authStore.sessionGeneration) return
        abortDeleteRequest()
        const controller = new AbortController()
        deleteRequestAbortController = controller
        try {
            const { data } = await messageApi.deleteMessages([...selectedMessages.value], {
                skipGlobalErrorHandler: true,
                signal: controller.signal,
            })
            if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
            if (data.success) {
                toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
                await fetchMessages()
            }
        } catch (error) {
            if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
            logger.error('Failed to delete messages:', error)
            toastStore.addToast(t('common.messages.deleteFailed'), 'error')
        } finally {
            if (deleteRequestAbortController === controller) {
                deleteRequestAbortController = null
            }
        }
    }

    function startReply(msg: MailboxMessageViewModel) {
        if (messageFromBlockedUser.value) {
            toastStore.addToast(t('user.message.blockedByUser'), 'error')
            return
        }
        replyTarget.value = msg
        selectedMessage.value = msg
        isReplyModalOpen.value = false
    }

    function closeReplyModal() {
        isReplyModalOpen.value = false
        replyTarget.value = null
        resetReplyContent()
    }

    function cancelInlineReply() {
        replyTarget.value = null
        resetReplyContent()
    }

    watch(selectedMessage, (val) => {
        if (!val) {
            messageFromBlockedUser.value = false
            selectedConversationMessages.value = []
        }
    })

    watch(
        () => authStore.sessionGeneration,
        () => {
            messageDetailRequestId++
            abortMessageDetailRequest()
            abortMarkAsReadRequests()
            abortConversationRefresh()
            abortDeleteRequest()
            resetMailboxState()
            selectedMessage.value = null
            selectedConversationMessages.value = []
            messageDetailLoading.value = false
            messageDetailError.value = null
            conversationLoading.value = false
            conversationError.value = null
            lastConversationPartnerId.value = null
            selectedMessages.value = []
            replyTarget.value = null
            resetReplyContent()
        },
    )

    onMounted(() => {
        fetchMessages()
    })

    onUnmounted(() => {
        messageDetailRequestId++
        abortMessageDetailRequest()
        abortMarkAsReadRequests()
        abortConversationRefresh()
        abortDeleteRequest()
    })

    return {
        viewType,
        messages,
        loading,
        error,
        selectedMessage,
        selectedConversationMessages,
        messageDetailLoading,
        messageDetailError,
        conversationLoading,
        conversationError,
        selectedMessages,
        page,
        size,
        totalPages,
        isReplyModalOpen,
        replyTarget,
        replyContent,
        isSending,
        fetchMessages,
        handlePageChange,
        handleSizeChange,
        changeViewType,
        openMessage,
        openConversationByPartnerId,
        retryMessageDetail,
        retryConversation,
        deleteSelectedMessages,
        startReply,
        closeReplyModal,
        cancelInlineReply,
        sendReply,
    }
}

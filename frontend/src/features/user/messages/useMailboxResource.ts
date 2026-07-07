import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AxiosError } from 'axios'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import { unwrapApiData } from '@/api/response'
import { useConfirm } from '@/composables/useConfirm'
import { useMailboxListState } from '@/features/user/messages/useMailboxListState'
import { useMessageSubmit } from '@/features/user/messages/useMessageSubmit'
import { useToastStore } from '@/stores/toast'
import type { MailboxMessageViewModel } from '@/types'
import { extractErrorResponse } from '@/utils/errorHandler'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'
import logger from '@/utils/logger'

const NOT_FOUND_CODE = 'C006'

export function useMailboxResource() {
    const { t } = useI18n()
    const toastStore = useToastStore()
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
    } = useMailboxListState()
    const selectedMessage = ref<MailboxMessageViewModel | null>(null)

    const isReplyModalOpen = ref(false)
    const replyTarget = ref<MailboxMessageViewModel | null>(null)
    const {
        content: replyContent,
        isSending,
        send: sendReply,
        reset: resetReplyContent,
    } = useMessageSubmit({
        getReceiverId: () => replyTarget.value?.partnerUserId,
        logMessage: 'Failed to send reply:',
        onSuccess: () => {
            closeReplyModal()
        },
    })
    /** Block relationship can make detail/read fail; show toast only when user attempts reply. */
    const messageFromBlockedUser = ref(false)
    let messageDetailRequestId = 0
    let messageDetailAbortController: AbortController | null = null
    const markAsReadAbortControllers = new Set<AbortController>()

    function abortMessageDetailRequest() {
        messageDetailAbortController?.abort()
        messageDetailAbortController = null
    }

    function abortMarkAsReadRequests() {
        markAsReadAbortControllers.forEach((controller) => controller.abort())
        markAsReadAbortControllers.clear()
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
        if (viewType.value !== 'received' || !wasUnread) {
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
        }
    }

    async function openMessage(msg: MailboxMessageViewModel) {
        const { requestId, messageId, controller } = startMessageDetailRequest(msg)
        try {
            const detail = await loadMessageDetail(messageId, controller)
            if (isStaleMessageDetail(requestId, messageId)) {
                return
            }
            if (detail) {
                selectedMessage.value = detail
            }
            await markMessageAsReadIfNeeded(messageId, msg.isUnread, requestId)
        } catch (error) {
            await handleMessageDetailError(error, requestId, messageId, controller)
        } finally {
            if (messageDetailAbortController === controller) {
                messageDetailAbortController = null
            }
        }
    }

    async function deleteSelectedMessages() {
        const isConfirmed = await confirm(t('common.messages.confirmDelete'))
        if (!isConfirmed) return
        try {
            const { data } = await messageApi.deleteMessages(selectedMessages.value)
            if (data.success) {
                toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
                await fetchMessages()
            }
        } catch (error) {
            logger.error('Failed to delete messages:', error)
            toastStore.addToast(t('common.messages.deleteFailed'), 'error')
        }
    }

    function startReply(msg: MailboxMessageViewModel) {
        if (messageFromBlockedUser.value) {
            toastStore.addToast(t('user.message.blockedByUser'), 'error')
            return
        }
        replyTarget.value = msg
        selectedMessage.value = null
        isReplyModalOpen.value = true
    }

    function closeReplyModal() {
        isReplyModalOpen.value = false
        replyTarget.value = null
        resetReplyContent()
    }

    watch(selectedMessage, (val) => {
        if (!val) messageFromBlockedUser.value = false
    })

    onMounted(() => {
        fetchMessages()
    })

    onUnmounted(() => {
        messageDetailRequestId++
        abortMessageDetailRequest()
        abortMarkAsReadRequests()
    })

    return {
        viewType,
        messages,
        loading,
        error,
        selectedMessage,
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
        deleteSelectedMessages,
        startReply,
        closeReplyModal,
        sendReply,
    }
}

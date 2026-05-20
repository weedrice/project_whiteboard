import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AxiosError } from 'axios'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import type { Message } from '@/types'
import { extractErrorResponse } from '@/utils/errorHandler'
import logger from '@/utils/logger'

const NOT_FOUND_CODE = 'C006'

export function useMailboxResource() {
    const { t } = useI18n()
    const toastStore = useToastStore()
    const { confirm } = useConfirm()

    const viewType = ref<'received' | 'sent'>('received')
    const messages = ref<Message[]>([])
    const loading = ref(false)
    const error = ref<string | null>(null)
    const selectedMessage = ref<Message | null>(null)
    const selectedMessages = ref<number[]>([])

    const page = ref(0)
    const size = ref(15)
    const totalPages = ref(0)

    const isReplyModalOpen = ref(false)
    const replyTarget = ref<Message | null>(null)
    const replyContent = ref('')
    const isSending = ref(false)
    /** Block relationship can make detail/read fail; show toast only when user attempts reply. */
    const messageFromBlockedUser = ref(false)
    let messageListRequestId = 0
    let messageDetailRequestId = 0
    let messageListAbortController: AbortController | null = null
    let messageDetailAbortController: AbortController | null = null
    const markAsReadAbortControllers = new Set<AbortController>()

    function abortMessageListRequest() {
        messageListAbortController?.abort()
        messageListAbortController = null
    }

    function abortMessageDetailRequest() {
        messageDetailAbortController?.abort()
        messageDetailAbortController = null
    }

    function abortMarkAsReadRequests() {
        markAsReadAbortControllers.forEach((controller) => controller.abort())
        markAsReadAbortControllers.clear()
    }

    async function fetchMessages() {
        const requestId = ++messageListRequestId
        abortMessageListRequest()
        const controller = new AbortController()
        messageListAbortController = controller
        loading.value = true
        error.value = null
        messages.value = []
        selectedMessages.value = []
        try {
            const params = {
                page: page.value,
                size: size.value
            }
            const { data } = viewType.value === 'received'
                ? await messageApi.getReceivedMessages(params, { signal: controller.signal })
                : await messageApi.getSentMessages(params, { signal: controller.signal })

            if (requestId === messageListRequestId && data.success) {
                messages.value = data.data?.content || []
                totalPages.value = data.data?.totalPages || 0
            }
        } catch (error) {
            if (requestId === messageListRequestId && !controller.signal.aborted) {
                logger.error('Failed to fetch messages:', error)
                error.value = t('common.messages.loadFailed')
            }
        } finally {
            if (messageListAbortController === controller) {
                messageListAbortController = null
            }
            if (requestId === messageListRequestId) {
                loading.value = false
            }
        }
    }

    function handlePageChange(newPage: number) {
        page.value = newPage
        fetchMessages()
    }

    function handleSizeChange() {
        page.value = 0
        fetchMessages()
    }

    function changeViewType(type: 'received' | 'sent') {
        if (viewType.value === type) return
        viewType.value = type
        page.value = 0
        fetchMessages()
    }

    async function openMessage(msg: Message) {
        const requestId = ++messageDetailRequestId
        abortMessageDetailRequest()
        const controller = new AbortController()
        messageDetailAbortController = controller
        const messageId = msg.messageId
        messageFromBlockedUser.value = false
        selectedMessage.value = msg
        try {
            const { data } = await messageApi.getMessage(messageId, {
                skipGlobalErrorHandler: true,
                signal: controller.signal
            })
            if (requestId !== messageDetailRequestId || selectedMessage.value?.messageId !== messageId) {
                return
            }
            if (data.success && data.data) {
                selectedMessage.value = data.data
            }
            if (viewType.value === 'received' && !msg.isRead) {
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
                msg.isRead = true
                if (requestId !== messageDetailRequestId || selectedMessage.value?.messageId !== messageId) {
                    return
                }
                if (selectedMessage.value?.messageId === messageId) {
                    selectedMessage.value = { ...selectedMessage.value, isRead: true }
                }
            }
        } catch (error) {
            if (requestId !== messageDetailRequestId || selectedMessage.value?.messageId !== messageId) {
                return
            }
            if (controller.signal.aborted) {
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
                fetchMessages()
            }
        } catch (error) {
            logger.error('Failed to delete messages:', error)
            toastStore.addToast(t('common.messages.deleteFailed'), 'error')
        }
    }

    function startReply(msg: Message) {
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
        replyContent.value = ''
    }

    async function sendReply() {
        if (!replyContent.value.trim()) return
        if (!replyTarget.value) return
        isSending.value = true
        try {
            const { data } = await messageApi.sendMessage(
                replyTarget.value.partner.userId,
                replyContent.value,
                { skipGlobalErrorHandler: true }
            )
            if (data.success) {
                toastStore.addToast(t('user.message.sendSuccess'), 'success')
                closeReplyModal()
            }
        } catch (error) {
            logger.error('Failed to send reply:', error)
            const errRes = extractErrorResponse(error as AxiosError)
            const toastMessage = errRes?.code === BLOCKED_BY_USER_CODE
                ? t('user.message.blockedByUser')
                : t('user.message.sendFailed')
            toastStore.addToast(toastMessage, 'error')
        } finally {
            isSending.value = false
        }
    }

    watch(selectedMessage, (val) => {
        if (!val) messageFromBlockedUser.value = false
    })

    onMounted(() => {
        fetchMessages()
    })

    onUnmounted(() => {
        messageListRequestId++
        messageDetailRequestId++
        abortMessageListRequest()
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

import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AxiosError } from 'axios'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import { unwrapApiData } from '@/api/response'
import { useConfirm } from '@/composables/useConfirm'
import { useMailboxListState } from '@/features/user/messages/useMailboxListState'
import { useMessageSubmit } from '@/features/user/messages/useMessageSubmit'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import type { MailboxMessageViewModel, MessageResponse } from '@/types'
import { API_ERROR_CODES } from '@/api/errorCodes'
import { extractErrorResponse } from '@/utils/errorHandler'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'
import logger from '@/utils/logger'
import { subscribeMessageStreamEvents } from '@/features/user/messages/messageStreamEvents'

const NOT_FOUND_CODE = API_ERROR_CODES.NOT_FOUND
const CONVERSATION_PAGE_SIZE = 20

interface ConversationPage {
    messages: MailboxMessageViewModel[]
    page: number
    hasNext: boolean
}

function mergeConversationMessages(
    ...messageGroups: MailboxMessageViewModel[][]
): MailboxMessageViewModel[] {
    const messagesById = new Map<number, MailboxMessageViewModel>()
    messageGroups.flat().forEach((message) => messagesById.set(message.id, message))
    return Array.from(messagesById.values()).sort((left, right) => {
        const createdAtDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
        return createdAtDifference || left.id - right.id
    })
}

function toConversationPage(messagePage: MessageResponse, requestedPage: number): ConversationPage {
    const page = Number.isInteger(messagePage.page) ? messagePage.page : requestedPage
    const hasNext = typeof messagePage.hasNext === 'boolean'
        ? messagePage.hasNext
        : page + 1 < (messagePage.totalPages ?? 0)
    return {
        messages: messagePage.content.map(toMailboxMessageViewModel),
        page,
        hasNext,
    }
}

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
        resetMailboxState,
    } = useMailboxListState()
    const selectedMessage = ref<MailboxMessageViewModel | null>(null)
    const selectedConversationMessages = ref<MailboxMessageViewModel[]>([])
    const messageDetailLoading = ref(false)
    const messageDetailError = ref<string | null>(null)
    const conversationLoading = ref(false)
    const conversationError = ref<string | null>(null)
    const conversationNextPage = ref<number | null>(null)
    const conversationLoadingMore = ref(false)
    const conversationOlderError = ref<string | null>(null)
    const conversationHasMore = computed(() => conversationNextPage.value !== null)
    const lastConversationPartnerId = ref<number | null>(null)
    const mailboxRefreshPending = ref(false)
    const pendingConversationMessageCount = ref(0)

    const isReplyModalOpen = ref(false)
    const replyTarget = ref<MailboxMessageViewModel | null>(null)
    const {
        content: replyContent,
        isSending,
        send: sendReply,
        reset: resetReplyContent,
        cancel: cancelPendingReply,
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
    let conversationPageAbortController: AbortController | null = null
    let deleteRequestAbortController: AbortController | null = null
    const markAsReadAbortControllers = new Set<AbortController>()
    const recentMessageNotificationIds = new Set<number>()

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

    function abortConversationPageRequest() {
        conversationPageAbortController?.abort()
        conversationPageAbortController = null
        conversationLoadingMore.value = false
    }

    function resetConversationPagination() {
        abortConversationPageRequest()
        conversationNextPage.value = null
        conversationOlderError.value = null
    }

    function applyInitialConversationPage(conversationPage: ConversationPage) {
        selectedConversationMessages.value = mergeConversationMessages(
            selectedConversationMessages.value,
            conversationPage.messages,
        )
        conversationNextPage.value = conversationPage.hasNext ? conversationPage.page + 1 : null
        conversationOlderError.value = null
    }

    function abortDeleteRequest() {
        deleteRequestAbortController?.abort()
        deleteRequestAbortController = null
    }

    async function refreshMailboxNow() {
        mailboxRefreshPending.value = false
        await fetchMessages()
    }

    async function requestMailboxRefresh() {
        if (selectedMessage.value) {
            mailboxRefreshPending.value = true
            return
        }
        await refreshMailboxNow()
    }

    function closeConversation() {
        if (isSending.value) return
        const shouldRefreshMailbox = mailboxRefreshPending.value
        mailboxRefreshPending.value = false
        pendingConversationMessageCount.value = 0
        messageDetailRequestId++
        abortMessageDetailRequest()
        abortConversationRefresh()
        resetConversationPagination()
        selectedMessage.value = null
        selectedConversationMessages.value = []
        messageDetailLoading.value = false
        messageDetailError.value = null
        conversationLoading.value = false
        conversationError.value = null
        lastConversationPartnerId.value = null
        isReplyModalOpen.value = false
        replyTarget.value = null
        cancelPendingReply()
        resetReplyContent()
        if (shouldRefreshMailbox) {
            void refreshMailboxNow()
        }
    }

    function isStaleMessageDetail(requestId: number, messageId: number) {
        return requestId !== messageDetailRequestId || selectedMessage.value?.id !== messageId
    }

    function startMessageDetailRequest(msg: MailboxMessageViewModel) {
        const requestId = ++messageDetailRequestId
        abortMessageDetailRequest()
        abortConversationRefresh()
        resetConversationPagination()
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
        pendingConversationMessageCount.value = 0
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
        await requestMailboxRefresh()
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
            pendingConversationMessageCount.value = 0
            await refreshMailboxNow()
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
                applyInitialConversationPage(conversation)
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

    async function loadConversationMessages(
        partnerId: number,
        controller: AbortController,
        page = 0,
    ): Promise<ConversationPage> {
        const { data } = await messageApi.getConversation(partnerId, {
            page,
            size: CONVERSATION_PAGE_SIZE,
            sort: 'createdAt,desc',
        }, {
            skipGlobalErrorHandler: true,
            signal: controller.signal,
        })
        const messagePage = unwrapApiData(data)
        if (data.success && messagePage) {
            return toConversationPage(messagePage, page)
        }
        return { messages: [], page, hasNext: false }
    }

    async function loadOlderConversationMessages() {
        const partnerId = lastConversationPartnerId.value ?? selectedMessage.value?.partnerUserId
        const nextPage = conversationNextPage.value
        if (partnerId == null || nextPage == null || conversationLoadingMore.value) return

        abortConversationPageRequest()
        const controller = new AbortController()
        conversationPageAbortController = controller
        const detailRequestId = messageDetailRequestId
        const generation = authStore.sessionGeneration
        conversationLoadingMore.value = true
        conversationOlderError.value = null

        const isCurrent = () => !controller.signal.aborted
            && conversationPageAbortController === controller
            && detailRequestId === messageDetailRequestId
            && generation === authStore.sessionGeneration
            && partnerId === lastConversationPartnerId.value

        try {
            const conversationPage = await loadConversationMessages(partnerId, controller, nextPage)
            if (!isCurrent()) return
            selectedConversationMessages.value = mergeConversationMessages(
                conversationPage.messages,
                selectedConversationMessages.value,
            )
            conversationNextPage.value = conversationPage.hasNext ? conversationPage.page + 1 : null
        } catch (error) {
            if (!isCurrent()) return
            logger.error('Failed to load older message conversation:', error)
            conversationOlderError.value = t('user.message.conversationLoadFailed')
        } finally {
            if (conversationPageAbortController === controller) {
                conversationPageAbortController = null
                conversationLoadingMore.value = false
            }
        }
    }

    async function refreshConversationAfterReply() {
        const partnerId = replyTarget.value?.partnerUserId ?? selectedMessage.value?.partnerUserId
        if (partnerId == null) return

        mailboxRefreshPending.value = true
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
            selectedConversationMessages.value = mergeConversationMessages(
                selectedConversationMessages.value,
                conversation.messages,
            )
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
        abortConversationRefresh()
        resetConversationPagination()
        const controller = new AbortController()
        messageDetailAbortController = controller
        messageFromBlockedUser.value = false
        selectedMessage.value = null
        selectedConversationMessages.value = []
        conversationLoading.value = true
        conversationError.value = null
        lastConversationPartnerId.value = partnerId
        pendingConversationMessageCount.value = 0

        try {
            const conversation = await loadConversationMessages(partnerId, controller)
            if (requestId !== messageDetailRequestId || controller.signal.aborted) {
                return
            }
            applyInitialConversationPage(conversation)
            selectedMessage.value = selectedConversationMessages.value.at(-1) ?? null
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

    function handleMessageStreamEvent(notification: { notificationId: number; actor: { userId: number } }) {
        if (recentMessageNotificationIds.has(notification.notificationId)) return
        recentMessageNotificationIds.add(notification.notificationId)
        if (recentMessageNotificationIds.size > 100) {
            const oldestNotificationId = recentMessageNotificationIds.values().next().value
            if (oldestNotificationId != null) recentMessageNotificationIds.delete(oldestNotificationId)
        }

        const partnerId = notification.actor.userId
        if (selectedMessage.value) {
            mailboxRefreshPending.value = true
            if (partnerId > 0 && partnerId === lastConversationPartnerId.value) {
                pendingConversationMessageCount.value++
            }
            return
        }

        const generation = authStore.sessionGeneration
        void refreshMailboxNow()
        if (partnerId <= 0 || partnerId !== lastConversationPartnerId.value) return

        abortConversationRefresh()
        const requestId = conversationRefreshRequestId
        const controller = new AbortController()
        conversationRefreshAbortController = controller
        void (async () => {
            try {
                const conversation = await loadConversationMessages(partnerId, controller)
                if (
                    controller.signal.aborted
                    || requestId !== conversationRefreshRequestId
                    || generation !== authStore.sessionGeneration
                    || partnerId !== lastConversationPartnerId.value
                ) return
                selectedConversationMessages.value = mergeConversationMessages(
                    selectedConversationMessages.value,
                    conversation.messages,
                )
            } catch (error) {
                if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
                logger.error('Failed to refresh message conversation from stream:', error)
            } finally {
                if (conversationRefreshAbortController === controller) {
                    conversationRefreshAbortController = null
                }
            }
        })()
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
        if (isSending.value) return
        isReplyModalOpen.value = false
        replyTarget.value = null
        cancelPendingReply()
        resetReplyContent()
    }

    function cancelInlineReply() {
        if (isSending.value) return
        replyTarget.value = null
        cancelPendingReply()
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
            resetConversationPagination()
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
            mailboxRefreshPending.value = false
            pendingConversationMessageCount.value = 0
            recentMessageNotificationIds.clear()
            cancelPendingReply()
            resetReplyContent()
        },
    )

    onMounted(() => {
        fetchMessages()
    })

    const unsubscribeMessageStream = subscribeMessageStreamEvents(handleMessageStreamEvent)

    onUnmounted(() => {
        messageDetailRequestId++
        abortMessageDetailRequest()
        abortMarkAsReadRequests()
        abortConversationRefresh()
        abortConversationPageRequest()
        abortDeleteRequest()
        cancelPendingReply()
        unsubscribeMessageStream()
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
        conversationHasMore,
        conversationLoadingMore,
        conversationOlderError,
        mailboxRefreshPending,
        pendingConversationMessageCount,
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
        closeConversation,
        retryMessageDetail,
        retryConversation,
        loadOlderConversationMessages,
        deleteSelectedMessages,
        startReply,
        closeReplyModal,
        cancelInlineReply,
        sendReply,
    }
}

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
import type { MailboxMessageViewModel } from '@/types'
import { API_ERROR_CODES } from '@/api/errorCodes'
import { extractErrorResponse } from '@/utils/errorHandler'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'
import logger from '@/utils/logger'
import { useMailboxRequestLifecycle } from '@/features/user/messages/useMailboxRequestLifecycle'
import { useMailboxRealtimeSync } from '@/features/user/messages/useMailboxRealtimeSync'
import {
    mergeConversationMessages,
    toConversationPage,
    type ConversationPage,
} from '@/features/user/messages/conversationModel'

const NOT_FOUND_CODE = API_ERROR_CODES.NOT_FOUND
const CONVERSATION_PAGE_SIZE = 20

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
    const requestLifecycle = useMailboxRequestLifecycle(() => authStore.sessionGeneration)

    function cancelConversationPageRequest() {
        requestLifecycle.conversationPage.cancel()
        conversationLoadingMore.value = false
    }

    function resetConversationPagination() {
        cancelConversationPageRequest()
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
        requestLifecycle.messageDetail.cancel()
        requestLifecycle.initialConversation.cancel()
        requestLifecycle.conversationRefresh.cancel()
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

    function startMessageDetailRequest(msg: MailboxMessageViewModel) {
        requestLifecycle.messageDetail.cancel()
        requestLifecycle.initialConversation.cancel()
        requestLifecycle.conversationRefresh.cancel()
        resetConversationPagination()
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
        return {
            messageId,
            detailRequest: requestLifecycle.messageDetail.start(),
            conversationRequest: requestLifecycle.initialConversation.start(),
        }
    }

    async function loadMessageDetail(messageId: number, signal: AbortSignal) {
        const { data } = await messageApi.getMessage(messageId, {
            skipGlobalErrorHandler: true,
            signal,
        })
        const message = unwrapApiData(data)
        if (data.success && message) {
            return toMailboxMessageViewModel(message)
        }
        return null
    }

    async function markMessageAsReadIfNeeded(
        messageId: number,
        wasUnread: boolean,
        isCurrentSelection: () => boolean,
    ) {
        if (viewType.value === 'sent' || !wasUnread || selectedMessage.value?.sentByMe) {
            return
        }

        const request = requestLifecycle.startMarkAsRead()
        try {
            await messageApi.markAsRead(messageId, {
                skipGlobalErrorHandler: true,
                signal: request.signal,
            })
            if (!request.isCurrent()) return
            await requestMailboxRefresh()
            if (!request.isCurrent() || !isCurrentSelection()) return
            if (selectedMessage.value?.id === messageId) {
                selectedMessage.value = markMailboxMessageRead(selectedMessage.value)
            }
        } finally {
            request.finish()
        }
    }

    async function handleMessageDetailError(
        error: unknown,
        messageId: number,
        signal: AbortSignal,
        isCurrentSelection: () => boolean,
    ) {
        if (!isCurrentSelection() || signal.aborted) {
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
        const { messageId, detailRequest, conversationRequest } = startMessageDetailRequest(msg)
        const isCurrentSelection = () => detailRequest.isCurrent()
            && selectedMessage.value?.id === messageId
        const detailTask = (async () => {
            try {
                const detail = await loadMessageDetail(messageId, detailRequest.signal)
                if (!isCurrentSelection()) return
                if (detail) selectedMessage.value = detail
                messageDetailError.value = null
                try {
                    await markMessageAsReadIfNeeded(messageId, msg.isUnread, isCurrentSelection)
                } catch (error) {
                    await handleMessageDetailError(
                        error,
                        messageId,
                        detailRequest.signal,
                        isCurrentSelection,
                    )
                }
            } catch (error) {
                await handleMessageDetailError(error, messageId, detailRequest.signal, isCurrentSelection)
            } finally {
                if (detailRequest.finish()) messageDetailLoading.value = false
            }
        })()
        const conversationTask = (async () => {
            try {
                const conversation = await loadConversationMessages(
                    msg.partnerUserId,
                    conversationRequest.signal,
                )
                if (!conversationRequest.isCurrent() || selectedMessage.value?.id !== messageId) return
                applyInitialConversationPage(conversation)
                conversationError.value = null
            } catch (error) {
                if (conversationRequest.isCurrent()) {
                    logger.error('Failed to load message conversation:', error)
                    conversationError.value = t('user.message.conversationLoadFailed')
                }
            } finally {
                if (conversationRequest.finish()) conversationLoading.value = false
            }
        })()

        await Promise.all([detailTask, conversationTask])
    }

    async function loadConversationMessages(
        partnerId: number,
        signal: AbortSignal,
        page = 0,
    ): Promise<ConversationPage> {
        const { data } = await messageApi.getConversation(partnerId, {
            page,
            size: CONVERSATION_PAGE_SIZE,
            sort: 'createdAt,desc',
        }, {
            skipGlobalErrorHandler: true,
            signal,
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

        const request = requestLifecycle.conversationPage.start()
        conversationLoadingMore.value = true
        conversationOlderError.value = null

        const isCurrent = () => request.isCurrent()
            && partnerId === lastConversationPartnerId.value

        try {
            const conversationPage = await loadConversationMessages(partnerId, request.signal, nextPage)
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
            if (request.finish()) {
                conversationLoadingMore.value = false
            }
        }
    }

    async function refreshConversationAfterReply() {
        const partnerId = replyTarget.value?.partnerUserId ?? selectedMessage.value?.partnerUserId
        if (partnerId == null) return

        mailboxRefreshPending.value = true
        const request = requestLifecycle.conversationRefresh.start()
        try {
            const conversation = await loadConversationMessages(partnerId, request.signal)
            if (!request.isCurrent()) return
            selectedConversationMessages.value = mergeConversationMessages(
                selectedConversationMessages.value,
                conversation.messages,
            )
        } catch (error) {
            if (!request.isCurrent()) return
            logger.error('Failed to refresh message conversation:', error)
        } finally {
            if (request.finish()) {
                replyTarget.value = selectedMessage.value
            }
        }
    }

    async function openConversationByPartnerId(partnerId: number) {
        requestLifecycle.messageDetail.cancel()
        requestLifecycle.initialConversation.cancel()
        requestLifecycle.conversationRefresh.cancel()
        resetConversationPagination()
        const request = requestLifecycle.initialConversation.start()
        messageFromBlockedUser.value = false
        selectedMessage.value = null
        selectedConversationMessages.value = []
        conversationLoading.value = true
        conversationError.value = null
        lastConversationPartnerId.value = partnerId
        pendingConversationMessageCount.value = 0

        try {
            const conversation = await loadConversationMessages(partnerId, request.signal)
            if (!request.isCurrent() || partnerId !== lastConversationPartnerId.value) {
                return
            }
            applyInitialConversationPage(conversation)
            selectedMessage.value = selectedConversationMessages.value.at(-1) ?? null
        } catch (error) {
            if (request.isCurrent()) {
                logger.error('Failed to open message conversation:', error)
                conversationError.value = t('user.message.conversationLoadFailed')
            }
        } finally {
            if (request.finish()) conversationLoading.value = false
        }
    }

    function refreshConversationFromStream(partnerId: number) {
        const request = requestLifecycle.conversationRefresh.start()
        void (async () => {
            try {
                const conversation = await loadConversationMessages(partnerId, request.signal)
                if (!request.isCurrent() || partnerId !== lastConversationPartnerId.value) return
                selectedConversationMessages.value = mergeConversationMessages(
                    selectedConversationMessages.value,
                    conversation.messages,
                )
            } catch (error) {
                if (!request.isCurrent()) return
                logger.error('Failed to refresh message conversation from stream:', error)
            } finally {
                request.finish()
            }
        })()
    }

    const realtimeSync = useMailboxRealtimeSync({
        isConversationOpen: () => selectedMessage.value !== null,
        getConversationPartnerId: () => lastConversationPartnerId.value,
        deferMailboxRefresh: () => {
            mailboxRefreshPending.value = true
        },
        refreshMailbox: () => {
            void refreshMailboxNow()
        },
        refreshConversation: refreshConversationFromStream,
        incrementPendingConversationMessages: () => {
            pendingConversationMessageCount.value++
        },
    })

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
        const request = requestLifecycle.deleteMessages.start()
        try {
            const { data } = await messageApi.deleteMessages([...selectedMessages.value], {
                skipGlobalErrorHandler: true,
                signal: request.signal,
            })
            if (!request.isCurrent()) return
            if (data.success) {
                toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
                await fetchMessages()
            }
        } catch (error) {
            if (!request.isCurrent()) return
            logger.error('Failed to delete messages:', error)
            toastStore.addToast(t('common.messages.deleteFailed'), 'error')
        } finally {
            request.finish()
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
            requestLifecycle.cancelAll()
            conversationNextPage.value = null
            conversationLoadingMore.value = false
            conversationOlderError.value = null
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
            realtimeSync.reset()
            cancelPendingReply()
            resetReplyContent()
        },
    )

    onMounted(() => {
        fetchMessages()
    })

    onUnmounted(() => {
        requestLifecycle.cancelAll()
        cancelPendingReply()
        realtimeSync.stop()
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

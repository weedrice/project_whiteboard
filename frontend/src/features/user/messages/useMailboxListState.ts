import { onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { messageApi } from '@/api/message'
import { unwrapApiData } from '@/api/response'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import { usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import type { MailboxMessageViewModel } from '@/types'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'
import logger from '@/utils/logger'

export type MailboxViewType = 'conversations' | 'received' | 'sent'

export function useMailboxListState() {
    const { t } = useI18n()
    const viewType = ref<MailboxViewType>('conversations')
    const messages = ref<MailboxMessageViewModel[]>([])
    const selectedMessages = ref<number[]>([])
    const {
        page,
        size,
        handlePageChange: setPage,
        handleSizeChange: setSize,
        resetPage,
    } = usePaginatedQueryState({ initialSize: 15 })
    const totalPages = ref(0)
    const messageListTask = useLatestAsyncTask<string>({
        getErrorValue: () => t('common.messages.loadFailed'),
        onError: (caughtError) => logger.error('Failed to fetch messages:', caughtError),
    })
    const { loading, error } = messageListTask

    async function fetchMessages() {
        messages.value = []
        selectedMessages.value = []
        const data = await messageListTask.run(async ({ signal }) => {
            const params = {
                page: page.value,
                size: size.value
            }
            const response = await fetchMessagePage(viewType.value, params, { signal })

            return response.data
        })

        if (data?.success) {
            const messagePage = unwrapApiData(data)
            messages.value = messagePage?.content.map(toMailboxMessageViewModel) || []
            totalPages.value = messagePage?.totalPages || 0
        }
    }

    function handlePageChange(newPage: number) {
        setPage(newPage)
        fetchMessages()
    }

    function handleSizeChange(newSize = size.value) {
        setSize(newSize)
        fetchMessages()
    }

    function changeViewType(type: MailboxViewType) {
        if (viewType.value === type) return
        viewType.value = type
        resetPage()
        fetchMessages()
    }

    function fetchMessagePage(type: MailboxViewType, params: { page: number; size: number }, config: { signal: AbortSignal }) {
        if (type === 'received') {
            return messageApi.getReceivedMessages(params, config)
        }
        if (type === 'sent') {
            return messageApi.getSentMessages(params, config)
        }
        return messageApi.getConversations(params, config)
    }

    function markListMessageRead(messageId: number) {
        const targetIndex = messages.value.findIndex((message) => message.id === messageId)
        if (targetIndex >= 0) {
            messages.value[targetIndex] = markMailboxMessageRead(messages.value[targetIndex])
        }
    }

    onUnmounted(() => {
        messageListTask.reset()
    })

    return {
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
    }
}

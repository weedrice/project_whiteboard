import { onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { messageApi } from '@/api/message'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import type { MailboxMessageViewModel } from '@/types'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/utils/messageViewModel'
import logger from '@/utils/logger'

export type MailboxViewType = 'received' | 'sent'

export function useMailboxListState() {
    const { t } = useI18n()
    const viewType = ref<MailboxViewType>('received')
    const messages = ref<MailboxMessageViewModel[]>([])
    const selectedMessages = ref<number[]>([])
    const page = ref(0)
    const size = ref(15)
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
            const response = viewType.value === 'received'
                ? await messageApi.getReceivedMessages(params, { signal })
                : await messageApi.getSentMessages(params, { signal })

            return response.data
        })

        if (data?.success) {
            messages.value = data.data?.content.map(toMailboxMessageViewModel) || []
            totalPages.value = data.data?.totalPages || 0
        }
    }

    function handlePageChange(newPage: number) {
        page.value = newPage
        fetchMessages()
    }

    function handleSizeChange(newSize = size.value) {
        size.value = newSize
        page.value = 0
        fetchMessages()
    }

    function changeViewType(type: MailboxViewType) {
        if (viewType.value === type) return
        viewType.value = type
        page.value = 0
        fetchMessages()
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

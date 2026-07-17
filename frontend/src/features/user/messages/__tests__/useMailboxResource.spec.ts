import { defineComponent, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BLOCKED_BY_USER_CODE, messageApi } from '@/api/message'
import { apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import type { MailboxMessageViewModel, MessageSummaryDto } from '@/types'
import { useMailboxResource } from '../useMailboxResource'

const mocks = vi.hoisted(() => ({
    fetchMessages: vi.fn(),
    markListMessageRead: vi.fn(),
    toastAdd: vi.fn(),
    confirm: vi.fn(),
    sendReply: vi.fn(),
    resetReplyContent: vi.fn(),
    resetMailboxState: vi.fn(),
    messageSubmitOnSuccess: undefined as (() => void) | undefined,
    listState: {
        viewType: { value: 'received' as 'conversations' | 'received' | 'sent' },
        messages: { value: [] as MailboxMessageViewModel[] },
        loading: { value: false },
        error: { value: null as string | null },
        selectedMessages: { value: [1] },
        page: { value: 0 },
        size: { value: 15 },
        totalPages: { value: 0 },
    },
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('@/api/message', () => ({
    BLOCKED_BY_USER_CODE: 'U009',
    messageApi: {
        getMessage: vi.fn(),
        getConversation: vi.fn(),
        markAsRead: vi.fn(),
        deleteMessages: vi.fn(),
    },
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: mocks.confirm,
    }),
}))

vi.mock('@/features/user/messages/useMailboxListState', () => ({
    useMailboxListState: () => ({
        ...mocks.listState,
        fetchMessages: mocks.fetchMessages,
        handlePageChange: vi.fn(),
        handleSizeChange: vi.fn(),
        changeViewType: vi.fn(),
        markListMessageRead: mocks.markListMessageRead,
        resetMailboxState: mocks.resetMailboxState,
    }),
}))

vi.mock('@/features/user/messages/useMessageSubmit', () => ({
    useMessageSubmit: (options: { onSuccess?: () => void }) => {
      mocks.messageSubmitOnSuccess = options.onSuccess
      return {
        content: { value: '' },
        isSending: { value: false },
        send: mocks.sendReply,
        reset: mocks.resetReplyContent,
      }
    },
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.toastAdd,
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
    },
}))

const message = (id: number, isUnread = true): MailboxMessageViewModel => ({
    id,
    partnerUserId: id + 100,
    partnerName: `User ${id}`,
    body: `Message ${id}`,
    isUnread,
    sentByMe: false,
    createdAt: '2026-06-01T00:00:00',
})

const detailDto = (id: number): MessageSummaryDto => ({
    messageId: id,
    content: `Detail ${id}`,
    partner: {
        userId: id + 100,
        displayName: `User ${id}`,
    },
    isRead: false,
    createdAt: '2026-06-01T00:00:00',
})

function mountMailboxResource() {
    let resource!: ReturnType<typeof useMailboxResource>
    const wrapper = mount(defineComponent({
        setup() {
            resource = useMailboxResource()
            return () => null
        },
    }))

    return { wrapper, resource }
}

describe('useMailboxResource', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
        mocks.confirm.mockResolvedValue(true)
        mocks.listState.viewType.value = 'received'
        mocks.listState.selectedMessages.value = [1]
        mocks.messageSubmitOnSuccess = undefined
        vi.mocked(messageApi.markAsRead).mockResolvedValue(apiSuccessResponse<typeof messageApi.markAsRead>())
        vi.mocked(messageApi.deleteMessages).mockResolvedValue(apiSuccessResponse<typeof messageApi.deleteMessages>())
        vi.mocked(messageApi.getConversation).mockResolvedValue(apiSuccessDataResponse<typeof messageApi.getConversation>({
            content: [],
            page: 0,
            size: 50,
            totalElements: 0,
            totalPages: 0,
            hasNext: false,
            hasPrevious: false,
        }))
    })

    it('aborts stale detail requests and keeps the latest selected message', async () => {
        const first = createDeferred<Awaited<ReturnType<typeof messageApi.getMessage>>>()
        const second = createDeferred<Awaited<ReturnType<typeof messageApi.getMessage>>>()
        vi.mocked(messageApi.getMessage)
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise)
        const { resource } = mountMailboxResource()

        const firstOpen = resource.openMessage(message(1))
        const firstSignal = vi.mocked(messageApi.getMessage).mock.calls[0][1]?.signal
        const secondOpen = resource.openMessage(message(2))

        expect(firstSignal?.aborted).toBe(true)

        second.resolve(apiSuccessDataResponse<typeof messageApi.getMessage>(detailDto(2)))
        await secondOpen
        first.resolve(apiSuccessDataResponse<typeof messageApi.getMessage>(detailDto(1)))
        await firstOpen

        expect(resource.selectedMessage.value?.id).toBe(2)
        expect(mocks.markListMessageRead).toHaveBeenCalledWith(2)
        expect(messageApi.markAsRead).toHaveBeenCalledWith(2, expect.objectContaining({
            signal: expect.any(AbortSignal),
            skipGlobalErrorHandler: true,
        }))
    })

    it('refreshes the list and clears selection when an opened message no longer exists', async () => {
        vi.mocked(messageApi.getMessage).mockRejectedValueOnce({
            response: {
                data: {
                    code: 'C006',
                    message: 'not found',
                },
            },
        })
        const { resource } = mountMailboxResource()

        await resource.openMessage(message(3))

        expect(resource.selectedMessage.value).toBeNull()
        expect(mocks.fetchMessages).toHaveBeenCalledTimes(2)
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.notFound', 'info')
    })

    it('shows the blocked-user toast only when the user tries to reply', async () => {
        vi.mocked(messageApi.getMessage).mockRejectedValueOnce({
            response: {
                data: {
                    code: BLOCKED_BY_USER_CODE,
                    message: 'blocked',
                },
            },
        })
        const blockedMessage = message(4)
        const { resource } = mountMailboxResource()

        await resource.openMessage(blockedMessage)
        await nextTick()

        expect(mocks.toastAdd).not.toHaveBeenCalledWith('user.message.blockedByUser', 'error')

        resource.startReply(blockedMessage)
        await flushPromises()

        expect(resource.isReplyModalOpen.value).toBe(false)
        expect(mocks.toastAdd).toHaveBeenCalledWith('user.message.blockedByUser', 'error')
    })

    it('waits for the message list refresh after deleting selected messages', async () => {
        const { resource } = mountMailboxResource()

        await resource.deleteSelectedMessages()

        expect(mocks.confirm).toHaveBeenCalledWith('common.messages.confirmDelete')
        expect(messageApi.deleteMessages).toHaveBeenCalledWith([1], expect.objectContaining({
            signal: expect.any(AbortSignal),
            skipGlobalErrorHandler: true,
        }))
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.deleteSuccess', 'success')
        expect(mocks.fetchMessages).toHaveBeenCalledTimes(2)
    })

    it('aborts and clears manual mailbox state when the session generation changes', async () => {
        const pending = createDeferred<Awaited<ReturnType<typeof messageApi.getMessage>>>()
        vi.mocked(messageApi.getMessage).mockReturnValueOnce(pending.promise)
        const { resource } = mountMailboxResource()
        const open = resource.openMessage(message(5))
        const signal = vi.mocked(messageApi.getMessage).mock.calls[0][1]?.signal

        useAuthStore().setTokens('new-session-token')
        await nextTick()

        expect(signal?.aborted).toBe(true)
        expect(resource.selectedMessage.value).toBeNull()
        expect(resource.selectedConversationMessages.value).toEqual([])
        expect(mocks.resetMailboxState).toHaveBeenCalled()
        expect(mocks.resetReplyContent).toHaveBeenCalled()

        pending.resolve(apiSuccessDataResponse<typeof messageApi.getMessage>(detailDto(5)))
        await open
        expect(resource.selectedMessage.value).toBeNull()
    })

    it('aborts and discards a reply refresh from the previous session', async () => {
        const pending = createDeferred<Awaited<ReturnType<typeof messageApi.getConversation>>>()
        vi.mocked(messageApi.getConversation).mockReturnValueOnce(pending.promise)
        const { resource } = mountMailboxResource()
        resource.startReply(message(6))

        mocks.messageSubmitOnSuccess?.()
        const signal = vi.mocked(messageApi.getConversation).mock.calls[0][2]?.signal
        useAuthStore().setTokens('next-session-token')
        await nextTick()

        expect(signal?.aborted).toBe(true)
        pending.resolve(apiSuccessDataResponse<typeof messageApi.getConversation>({
            content: [detailDto(6)],
            page: 0,
            size: 50,
            totalElements: 1,
            totalPages: 1,
            hasNext: false,
            hasPrevious: false,
        }))
        await flushPromises()

        expect(resource.selectedConversationMessages.value).toEqual([])
        expect(resource.replyTarget.value).toBeNull()
    })
})

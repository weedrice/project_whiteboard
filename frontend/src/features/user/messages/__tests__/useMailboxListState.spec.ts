import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { messageApi } from '@/api/message'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import type { MessageSummaryDto } from '@/types'
import { useMailboxListState } from '../useMailboxListState'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/api/message', () => ({
    messageApi: {
        getReceivedMessages: vi.fn(),
        getSentMessages: vi.fn(),
        getConversations: vi.fn(),
    },
}))

vi.mock('@/utils/logger', () => ({
    default: { error: vi.fn() },
}))

const messageDto = (id: number): MessageSummaryDto => ({
    messageId: id,
    content: `Message ${id}`,
    partner: { userId: id + 100, displayName: `User ${id}` },
    isRead: false,
    createdAt: '2026-06-01T00:00:00',
})

function page(content: MessageSummaryDto[], currentPage: number, totalPages: number) {
    return apiSuccessDataResponse<typeof messageApi.getConversations>({
        content,
        page: currentPage,
        size: 15,
        totalElements: content.length,
        totalPages,
        hasNext: currentPage + 1 < totalPages,
        hasPrevious: currentPage > 0,
    })
}

function mountListState() {
    let state!: ReturnType<typeof useMailboxListState>
    const wrapper = mount(defineComponent({
        setup() {
            state = useMailboxListState()
            return () => null
        },
    }))
    return { state, wrapper }
}

describe('useMailboxListState', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('clamps and refetches when the current page disappears after deletion', async () => {
        vi.mocked(messageApi.getConversations)
            .mockResolvedValueOnce(page([], 2, 2))
            .mockResolvedValueOnce(page([messageDto(20)], 1, 2))
        const { state } = mountListState()

        state.handlePageChange(2)
        await flushPromises()

        expect(messageApi.getConversations).toHaveBeenNthCalledWith(1, {
            page: 2,
            size: 15,
        }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
        expect(messageApi.getConversations).toHaveBeenNthCalledWith(2, {
            page: 1,
            size: 15,
        }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
        expect(state.page.value).toBe(1)
        expect(state.messages.value.map(({ id }) => id)).toEqual([20])
    })
})

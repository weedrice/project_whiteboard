import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import MyMessages from '../MyMessages.vue'

const messageApi = vi.hoisted(() => ({
    getReceivedMessages: vi.fn(),
    getSentMessages: vi.fn(),
    getMessage: vi.fn(),
    markAsRead: vi.fn(),
    deleteMessages: vi.fn(),
    sendMessage: vi.fn(),
}))

const addToast = vi.hoisted(() => vi.fn())

vi.mock('@/api/message', () => ({
    messageApi,
    BLOCKED_BY_USER_CODE: 'U009',
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast,
    }),
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: vi.fn().mockResolvedValue(true),
    }),
}))

vi.mock('@/utils/errorHandler', () => ({
    extractErrorResponse: vi.fn(),
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
    },
}))

vi.mock('@/utils/date', () => ({
    formatDate: (value: string) => value,
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

const baseModalStub = {
    props: ['isOpen', 'title'],
    template: '<div v-if="isOpen"><slot /></div>',
}

const baseCheckboxStub = {
    props: ['value', 'modelValue'],
    emits: ['update:modelValue'],
    template: '<input type="checkbox" />',
}

describe('MyMessages', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('loads message detail and then calls read endpoint for unread received messages', async () => {
        const listedMessage = {
            messageId: 5,
            content: 'hello',
            partner: { userId: 2, displayName: 'Other' },
            isRead: false,
            createdAt: '2026-04-16T11:00:00',
        }

        messageApi.getReceivedMessages.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [listedMessage],
                    totalPages: 1,
                }
            }
        })
        messageApi.getMessage.mockResolvedValue({
            data: {
                success: true,
                data: { ...listedMessage }
            }
        })
        messageApi.markAsRead.mockResolvedValue({
            data: { success: true }
        })

        const wrapper = mount(MyMessages, {
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    BaseModal: baseModalStub,
                    BaseButton: true,
                    BaseCheckbox: baseCheckboxStub,
                    BaseTextarea: true,
                    BaseSkeleton: true,
                    EmptyState: true,
                    Pagination: true,
                    PageSizeSelector: true,
                    Mail: true,
                }
            }
        })

        await flushPromises()
        await wrapper.find('li').trigger('click')
        await flushPromises()

        expect(messageApi.getMessage).toHaveBeenCalledWith(5, { skipGlobalErrorHandler: true })
        expect(messageApi.markAsRead).toHaveBeenCalledWith(5, { skipGlobalErrorHandler: true })
        expect(messageApi.getMessage.mock.invocationCallOrder[0]).toBeLessThan(messageApi.markAsRead.mock.invocationCallOrder[0])
        expect(listedMessage.isRead).toBe(true)
    })
})

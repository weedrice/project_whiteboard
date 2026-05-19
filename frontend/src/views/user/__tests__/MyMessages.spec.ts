import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import MyMessages from '../MyMessages.vue'
import { extractErrorResponse } from '@/utils/errorHandler'

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

    const mountMyMessages = () => mount(MyMessages, {
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

        const wrapper = mountMyMessages()

        await flushPromises()
        await wrapper.find('li').trigger('click')
        await flushPromises()

        expect(messageApi.getMessage).toHaveBeenCalledWith(5, { skipGlobalErrorHandler: true })
        expect(messageApi.markAsRead).toHaveBeenCalledWith(5, { skipGlobalErrorHandler: true })
        expect(messageApi.getMessage.mock.invocationCallOrder[0]).toBeLessThan(messageApi.markAsRead.mock.invocationCallOrder[0])
        expect(listedMessage.isRead).toBe(true)
    })

    it('closes stale message detail and refreshes the list when detail lookup returns not found', async () => {
        const listedMessage = {
            messageId: 5,
            content: 'hello',
            partner: { userId: 2, displayName: 'Other' },
            isRead: false,
            createdAt: '2026-04-16T11:00:00',
        }

        messageApi.getReceivedMessages
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: {
                        content: [listedMessage],
                        totalPages: 1,
                    }
                }
            })
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: {
                        content: [],
                        totalPages: 0,
                    }
                }
            })
        messageApi.getMessage.mockRejectedValue(new Error('not found'))
        vi.mocked(extractErrorResponse).mockReturnValue({
            code: 'C006',
            message: 'not found',
        })

        const wrapper = mountMyMessages()

        await flushPromises()
        await wrapper.find('li').trigger('click')
        await flushPromises()

        expect(messageApi.getReceivedMessages).toHaveBeenCalledTimes(2)
        expect(messageApi.markAsRead).not.toHaveBeenCalled()
        expect(wrapper.findAllComponents(baseModalStub)[0]?.props('isOpen')).toBe(false)
        expect(addToast).toHaveBeenCalledWith('common.messages.notFound', 'info')
    })

    it('closes stale message detail and refreshes the list when read endpoint returns not found', async () => {
        const listedMessage = {
            messageId: 5,
            content: 'hello',
            partner: { userId: 2, displayName: 'Other' },
            isRead: false,
            createdAt: '2026-04-16T11:00:00',
        }

        messageApi.getReceivedMessages
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: {
                        content: [listedMessage],
                        totalPages: 1,
                    }
                }
            })
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: {
                        content: [],
                        totalPages: 0,
                    }
                }
            })
        messageApi.getMessage.mockResolvedValue({
            data: {
                success: true,
                data: { ...listedMessage }
            }
        })
        messageApi.markAsRead.mockRejectedValue(new Error('not found'))
        vi.mocked(extractErrorResponse).mockReturnValue({
            code: 'C006',
            message: 'not found',
        })

        const wrapper = mountMyMessages()

        await flushPromises()
        await wrapper.find('li').trigger('click')
        await flushPromises()

        expect(messageApi.getReceivedMessages).toHaveBeenCalledTimes(2)
        expect(messageApi.getMessage).toHaveBeenCalledWith(5, { skipGlobalErrorHandler: true })
        expect(messageApi.markAsRead).toHaveBeenCalledWith(5, { skipGlobalErrorHandler: true })
        expect(wrapper.findAllComponents(baseModalStub)[0]?.props('isOpen')).toBe(false)
        expect(addToast).toHaveBeenCalledWith('common.messages.notFound', 'info')
    })

    it('ignores stale detail responses after another message is selected', async () => {
        const firstMessage = {
            messageId: 5,
            content: 'first summary',
            partner: { userId: 2, displayName: 'First' },
            isRead: false,
            createdAt: '2026-04-16T11:00:00',
        }
        const secondMessage = {
            messageId: 6,
            content: 'second summary',
            partner: { userId: 3, displayName: 'Second' },
            isRead: false,
            createdAt: '2026-04-16T11:05:00',
        }
        const resolvers = new Map<number, (value: unknown) => void>()

        messageApi.getReceivedMessages.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [firstMessage, secondMessage],
                    totalPages: 1,
                }
            }
        })
        messageApi.getMessage.mockImplementation((messageId: number) => new Promise((resolve) => {
            resolvers.set(messageId, resolve)
        }))
        messageApi.markAsRead.mockResolvedValue({
            data: { success: true }
        })

        const wrapper = mountMyMessages()

        await flushPromises()
        await wrapper.findAll('li')[0].trigger('click')
        await wrapper.findAll('li')[1].trigger('click')

        resolvers.get(6)?.({
            data: {
                success: true,
                data: { ...secondMessage, content: 'second detail' }
            }
        })
        await flushPromises()

        resolvers.get(5)?.({
            data: {
                success: true,
                data: { ...firstMessage, content: 'first stale detail' }
            }
        })
        await flushPromises()

        expect(wrapper.text()).toContain('second detail')
        expect(wrapper.text()).not.toContain('first stale detail')
        expect(messageApi.markAsRead).toHaveBeenCalledTimes(1)
        expect(messageApi.markAsRead).toHaveBeenCalledWith(6, { skipGlobalErrorHandler: true })
        expect(firstMessage.isRead).toBe(false)
        expect(secondMessage.isRead).toBe(true)
    })

    it('keeps the list read state when read completion is stale for the modal', async () => {
        const firstMessage = {
            messageId: 5,
            content: 'first summary',
            partner: { userId: 2, displayName: 'First' },
            isRead: false,
            createdAt: '2026-04-16T11:00:00',
        }
        const secondMessage = {
            messageId: 6,
            content: 'second summary',
            partner: { userId: 3, displayName: 'Second' },
            isRead: false,
            createdAt: '2026-04-16T11:05:00',
        }
        let resolveFirstRead: ((value: unknown) => void) | undefined

        messageApi.getReceivedMessages.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [firstMessage, secondMessage],
                    totalPages: 1,
                }
            }
        })
        messageApi.getMessage.mockImplementation((messageId: number) => Promise.resolve({
            data: {
                success: true,
                data: {
                    ...(messageId === 5 ? firstMessage : secondMessage),
                    content: messageId === 5 ? 'first detail' : 'second detail'
                }
            }
        }))
        messageApi.markAsRead.mockImplementation((messageId: number) => {
            if (messageId === 5) {
                return new Promise((resolve) => {
                    resolveFirstRead = resolve
                })
            }
            return Promise.resolve({ data: { success: true } })
        })

        const wrapper = mountMyMessages()

        await flushPromises()
        await wrapper.findAll('li')[0].trigger('click')
        await flushPromises()
        await wrapper.findAll('li')[1].trigger('click')
        await flushPromises()

        resolveFirstRead?.({ data: { success: true } })
        await flushPromises()

        expect(firstMessage.isRead).toBe(true)
        expect(wrapper.text()).toContain('second detail')
        expect(wrapper.text()).not.toContain('first detail')
    })
})

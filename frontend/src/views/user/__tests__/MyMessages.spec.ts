import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { toMailboxMessageViewModel } from '@/utils/messageViewModel'
import { getExposedVm } from '@/test/vue-test-helpers'
import { createDeferred } from '@/test/async'
import {
    addToast,
    baseModalStub,
    extractErrorResponseMock,
    messageApi,
    messageOpenButtons,
    mountMyMessages,
    routerMocks,
    type MyMessagesExposed,
} from './MyMessages.test-harness'

describe('MyMessages', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routerMocks.route.query = {}
        messageApi.getConversations.mockImplementation((params, config) => messageApi.getReceivedMessages(params, config))
        messageApi.getConversation.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [],
                    totalPages: 0,
                },
            },
        })
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
        await messageOpenButtons(wrapper)[0].trigger('click')
        await flushPromises()

        expect(messageApi.getMessage).toHaveBeenCalledWith(5, expect.objectContaining({ skipGlobalErrorHandler: true }))
        expect(messageApi.markAsRead).toHaveBeenCalledWith(5, expect.objectContaining({ skipGlobalErrorHandler: true }))
        expect(messageApi.getMessage.mock.invocationCallOrder[0]).toBeLessThan(messageApi.markAsRead.mock.invocationCallOrder[0])
        expect(listedMessage.isRead).toBe(false)
    })

    it('shows an error state instead of empty state when the message list fails to load', async () => {
        messageApi.getReceivedMessages.mockRejectedValue(new Error('network'))

        const wrapper = mountMyMessages()

        await flushPromises()

        const errorState = wrapper.find('[data-testid="error-state"]')
        expect(errorState.exists()).toBe(true)
        expect(errorState.text()).toBe('common.messages.loadFailed')
        expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(false)
    })

    it('shows the shared blank-content warning for empty replies', async () => {
        const listedMessage = {
            messageId: 5,
            content: 'hello',
            partner: { userId: 2, displayName: 'Other' },
            isRead: true,
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

        const wrapper = mountMyMessages()
        await flushPromises()

        const exposed = getExposedVm<MyMessagesExposed>(wrapper)
        exposed.startReply(toMailboxMessageViewModel(listedMessage))
        exposed.replyContent = '   '
        await exposed.sendReply()

        expect(addToast).toHaveBeenCalledWith('user.message.inputContent', 'warning')
        expect(messageApi.sendMessage).not.toHaveBeenCalled()
    })

    it('renders conversation messages with visual direction and inline reply controls', async () => {
        const listedMessage = {
            messageId: 5,
            content: 'received summary',
            partner: { userId: 2, displayName: 'Other' },
            isRead: true,
            sentByMe: false,
            createdAt: '2026-04-16T11:00:00',
        }
        const sentMessage = {
            messageId: 6,
            content: 'sent reply',
            partner: { userId: 2, displayName: 'Other' },
            isRead: true,
            sentByMe: true,
            createdAt: '2026-04-16T11:05:00',
        }

        messageApi.getReceivedMessages.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [listedMessage],
                    totalPages: 1,
                },
            },
        })
        messageApi.getMessage.mockResolvedValue({
            data: {
                success: true,
                data: listedMessage,
            },
        })
        messageApi.getConversation.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [listedMessage, sentMessage],
                    totalPages: 1,
                },
            },
        })

        const wrapper = mountMyMessages()
        await flushPromises()
        await messageOpenButtons(wrapper)[0].trigger('click')
        await flushPromises()

        expect(wrapper.find('[data-message-direction="received"]').exists()).toBe(true)
        expect(wrapper.find('[data-message-direction="sent"]').exists()).toBe(true)
        expect(wrapper.find('base-textarea-stub').attributes('label')).toBe('user.message.replyTitle')
        expect(wrapper.find('base-textarea-stub').attributes('maxlength')).toBe('5000')
        const modal = wrapper.findComponent(baseModalStub)
        expect(modal.props('size')).toBe('2xl')
        expect(modal.props('title')).toBe('user.message.detailTitle')
        expect(modal.props('mobileFull')).toBe(true)
        expect(modal.props('mobileFitContent')).toBe(true)

        const content = wrapper.find('[data-testid="message-detail-content"]')
        expect(content.classes()).not.toContain('p-4')
        expect(content.classes()).not.toContain('sm:p-6')
    })

    it('removes an invalid partner query instead of leaving a stale conversation route', async () => {
        routerMocks.route.query = { partnerId: 'invalid' }

        mountMyMessages()
        await flushPromises()

        expect(routerMocks.replace).toHaveBeenCalledWith({ query: {} })
        expect(messageApi.getConversation).not.toHaveBeenCalled()
    })

    it('clears the partner query when the deep-linked conversation modal closes', async () => {
        routerMocks.route.query = { partnerId: '2', page: '3' }
        messageApi.getConversation.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [{
                        messageId: 5,
                        content: 'deep-linked message',
                        partner: { userId: 2, displayName: 'Other' },
                        isRead: true,
                        createdAt: '2026-04-16T11:00:00',
                    }],
                    totalPages: 1,
                },
            },
        })

        const wrapper = mountMyMessages()
        await flushPromises()
        const modal = wrapper.findComponent(baseModalStub)
        expect(modal.props('isOpen')).toBe(true)

        modal.vm.$emit('close')
        await flushPromises()

        expect(routerMocks.replace).toHaveBeenCalledWith({ query: { page: '3' } })
        expect(modal.props('isOpen')).toBe(false)
    })

    it('shows a retryable conversation error without hiding the loaded message detail', async () => {
        const listedMessage = {
            messageId: 5,
            content: 'loaded detail',
            partner: { userId: 2, displayName: 'Other' },
            isRead: true,
            createdAt: '2026-04-16T11:00:00',
        }
        messageApi.getReceivedMessages.mockResolvedValue({
            data: { success: true, data: { content: [listedMessage], totalPages: 1 } },
        })
        messageApi.getMessage.mockResolvedValue({
            data: { success: true, data: listedMessage },
        })
        messageApi.getConversation.mockRejectedValue(new Error('offline'))

        const wrapper = mountMyMessages()
        await flushPromises()
        await messageOpenButtons(wrapper)[0].trigger('click')
        await flushPromises()

        expect(wrapper.text()).toContain('loaded detail')
        expect(wrapper.find('[data-testid="error-state"]').text()).toBe('user.message.conversationLoadFailed')
    })

    it('exposes mailbox view and message row controls to assistive technology', async () => {
        messageApi.getReceivedMessages.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [
                        {
                            messageId: 5,
                            content: 'hello',
                            partner: { userId: 2, displayName: 'Other' },
                            isRead: true,
                            createdAt: '2026-04-16T11:00:00',
                        },
                    ],
                    totalPages: 1,
                }
            }
        })

        const wrapper = mountMyMessages()
        await flushPromises()

        const receivedButton = wrapper.findAll('button').find((button) => button.text() === 'user.message.received')
        const sentButton = wrapper.findAll('button').find((button) => button.text() === 'user.message.sent')
        const conversationsButton = wrapper.findAll('button').find((button) => button.text() === 'user.message.conversations')

        expect(wrapper.get('h1').text()).toContain('user.message.boxTitle')
        expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe('user.message.boxTitle')
        expect(conversationsButton?.attributes('aria-pressed')).toBe('true')
        expect(receivedButton?.attributes('aria-pressed')).toBe('false')
        expect(sentButton?.attributes('aria-pressed')).toBe('false')
        expect(messageOpenButtons(wrapper)[0].attributes('aria-label')).toBe('user.message.openMessage')
        expect(wrapper.get('label.sr-only').text()).toBe('user.message.selectMessage')
    })

    it('ignores stale detail responses when another message is selected first', async () => {
        const firstMessage = {
            messageId: 1,
            content: 'first listed',
            partner: { userId: 2, displayName: 'First User' },
            isRead: true,
            createdAt: '2026-04-16T11:00:00',
        }
        const secondMessage = {
            messageId: 2,
            content: 'second listed',
            partner: { userId: 3, displayName: 'Second User' },
            isRead: true,
            createdAt: '2026-04-16T12:00:00',
        }
        const firstDetail = createDeferred<{
            data: { success: boolean; data: typeof firstMessage }
        }>()
        const secondDetail = createDeferred<{
            data: { success: boolean; data: typeof secondMessage }
        }>()

        messageApi.getReceivedMessages.mockResolvedValue({
            data: {
                success: true,
                data: {
                    content: [firstMessage, secondMessage],
                    totalPages: 1,
                }
            }
        })
        messageApi.getMessage.mockImplementation((messageId: number) => {
            return messageId === 1 ? firstDetail.promise : secondDetail.promise
        })

        const wrapper = mountMyMessages()

        await flushPromises()

        const messages = messageOpenButtons(wrapper)
        await messages[0].trigger('click')
        await messages[1].trigger('click')

        secondDetail.resolve({
            data: {
                success: true,
                data: { ...secondMessage, content: 'second detail' },
            }
        })
        await flushPromises()

        firstDetail.resolve({
            data: {
                success: true,
                data: { ...firstMessage, content: 'first stale detail' },
            }
        })
        await flushPromises()

        expect(wrapper.text()).toContain('second detail')
        expect(wrapper.text()).not.toContain('first stale detail')
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
        extractErrorResponseMock.mockReturnValue({
            code: 'C006',
            message: 'not found',
        })

        const wrapper = mountMyMessages()

        await flushPromises()
        await messageOpenButtons(wrapper)[0].trigger('click')
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
        extractErrorResponseMock.mockReturnValue({
            code: 'C006',
            message: 'not found',
        })

        const wrapper = mountMyMessages()

        await flushPromises()
        await messageOpenButtons(wrapper)[0].trigger('click')
        await flushPromises()

        expect(messageApi.getReceivedMessages).toHaveBeenCalledTimes(2)
        expect(messageApi.getMessage).toHaveBeenCalledWith(5, expect.objectContaining({ skipGlobalErrorHandler: true }))
        expect(messageApi.markAsRead).toHaveBeenCalledWith(5, expect.objectContaining({ skipGlobalErrorHandler: true }))
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
        await messageOpenButtons(wrapper)[0].trigger('click')
        await messageOpenButtons(wrapper)[1].trigger('click')

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
        expect(messageApi.markAsRead).toHaveBeenCalledWith(6, expect.objectContaining({ skipGlobalErrorHandler: true }))
        expect(firstMessage.isRead).toBe(false)
        expect(secondMessage.isRead).toBe(false)
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
        await messageOpenButtons(wrapper)[0].trigger('click')
        await flushPromises()
        await messageOpenButtons(wrapper)[1].trigger('click')
        await flushPromises()

        resolveFirstRead?.({ data: { success: true } })
        await flushPromises()

        expect(firstMessage.isRead).toBe(false)
        expect(wrapper.text()).toContain('second detail')
        expect(wrapper.text()).not.toContain('first detail')
    })
})

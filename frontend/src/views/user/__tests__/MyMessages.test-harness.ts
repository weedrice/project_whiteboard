import { mount } from '@vue/test-utils'
import { vi } from 'vitest'
import MyMessages from '../MyMessages.vue'
import { toMailboxMessageViewModel } from '@/utils/messageViewModel'

export type MyMessagesExposed = {
    startReply: (message: ReturnType<typeof toMailboxMessageViewModel>) => void
    replyContent: string
    sendReply: () => Promise<void>
}

const messageApi = vi.hoisted(() => ({
    getReceivedMessages: vi.fn(),
    getSentMessages: vi.fn(),
    getConversations: vi.fn(),
    getConversation: vi.fn(),
    getMessage: vi.fn(),
    markAsRead: vi.fn(),
    deleteMessages: vi.fn(),
    sendMessage: vi.fn(),
}))

const addToast = vi.hoisted(() => vi.fn())
const extractErrorResponseMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/message', () => ({
    messageApi,
    BLOCKED_BY_USER_CODE: 'U009',
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({
        sessionGeneration: 0,
    }),
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: vi.fn().mockResolvedValue(true),
    }),
}))

vi.mock('@/utils/errorHandler', () => ({
    extractErrorResponse: extractErrorResponseMock,
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

vi.mock('vue-router', () => ({
    useRoute: () => ({
        query: {},
    }),
}))

export { addToast, extractErrorResponseMock, messageApi }

export const baseModalStub = {
    props: {
        isOpen: Boolean,
        title: String,
        size: String,
        mobileFull: Boolean,
        mobileFitContent: Boolean,
    },
    template: '<div v-if="isOpen"><slot /></div>',
}

const baseCheckboxStub = {
    props: ['id', 'value', 'modelValue', 'label', 'labelClass'],
    emits: ['update:modelValue'],
    template: '<label :class="labelClass"><input :id="id" type="checkbox" />{{ label }}</label>',
}

const errorStateStub = {
    name: 'ErrorState',
    props: ['message', 'showRetry'],
    emits: ['retry'],
    template: '<div data-testid="error-state">{{ message }}</div>',
}

export function mountMyMessages() {
    return mount(MyMessages, {
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
                ErrorState: errorStateStub,
                Pagination: true,
                PageSizeSelector: true,
                Mail: true,
            }
        }
    })
}

export const messageOpenButtons = (wrapper: ReturnType<typeof mount>) => wrapper.findAll('li > button')

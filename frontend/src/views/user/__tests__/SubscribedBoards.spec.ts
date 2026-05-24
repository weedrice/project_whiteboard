import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SubscribedBoards from '../SubscribedBoards.vue'
import { boardApi } from '@/api/board'
import { userApi } from '@/api/user'

const mockConfirm = vi.fn()
const mockInvalidateQueries = vi.fn()

vi.mock('@/api/user', () => ({
    userApi: {
        getMySubscriptions: vi.fn()
    }
}))

vi.mock('@/api/board', () => ({
    boardApi: {
        unsubscribeBoard: vi.fn(),
        updateSubscriptionOrder: vi.fn()
    }
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key
    })
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: vi.fn()
    })
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: mockConfirm
    })
}))

vi.mock('@/composables/useErrorHandler', () => ({
    useErrorHandler: () => ({
        handleSilentError: vi.fn(),
        handleError: vi.fn()
    })
}))

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({
        invalidateQueries: mockInvalidateQueries
    })
}))

const BaseButtonStub = defineComponent({
    emits: ['click'],
    setup(_, { slots, emit }) {
        return () => h('button', { onClick: (event: MouseEvent) => emit('click', event) }, slots.default?.())
    }
})

const DraggableStub = defineComponent({
    name: 'DraggableStub',
    props: {
        modelValue: {
            type: Array,
            default: () => []
        }
    },
    emits: ['end'],
    setup(props, { slots }) {
        return () => h('ul', (props.modelValue as unknown[]).map(element => slots.item?.({ element })))
    }
})

const subscriptionPage = {
    success: true,
    data: {
        content: [{
            boardId: 1,
            boardName: 'Free',
            boardUrl: 'free',
            description: 'Free board',
            sortOrder: 1,
            isActive: true,
            accessState: 'ACCESSIBLE'
        }],
        totalPages: 1
    }
}

function mountView() {
    return mount(SubscribedBoards, {
        global: {
            mocks: {
                $t: (key: string) => key,
                $router: {
                    push: vi.fn()
                }
            },
            stubs: {
                BaseButton: BaseButtonStub,
                BaseSkeleton: true,
                EmptyState: true,
                draggable: DraggableStub,
                RouterLink: RouterLinkStub,
                Users: true,
                Menu: true
            }
        }
    })
}

describe('SubscribedBoards', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockConfirm.mockResolvedValue(true)
        vi.mocked(userApi.getMySubscriptions).mockResolvedValue({ data: subscriptionPage } as never)
        vi.mocked(boardApi.unsubscribeBoard).mockResolvedValue({ data: { success: true } } as never)
        vi.mocked(boardApi.updateSubscriptionOrder).mockResolvedValue({ data: { success: true } } as never)
    })

    it('invalidates board subscription caches after unsubscribe', async () => {
        const wrapper = mountView()
        await flushPromises()

        await wrapper.find('button').trigger('click')
        await flushPromises()

        expect(boardApi.unsubscribeBoard).toHaveBeenCalledWith('free')
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards', 'subscriptions'] })
    })

    it('invalidates board subscription caches after reorder', async () => {
        const wrapper = mountView()
        await flushPromises()

        wrapper.getComponent(DraggableStub).vm.$emit('end')
        await flushPromises()

        expect(boardApi.updateSubscriptionOrder).toHaveBeenCalledWith(['free'])
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards', 'subscriptions'] })
    })

    it('renders accessible subscriptions as real board links', async () => {
        const wrapper = mountView()
        await flushPromises()

        const link = wrapper.getComponent(RouterLinkStub)

        expect(link.props('to')).toBe('/board/free')
        expect(link.attributes('aria-label')).toBe('Free')
    })
})

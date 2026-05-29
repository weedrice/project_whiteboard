import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ScrapList from '../ScrapList.vue'
import PointHistory from '../PointHistory.vue'
import MyReports from '../MyReports.vue'

const userApi = vi.hoisted(() => ({
    getMyScraps: vi.fn(),
    getMyPointHistories: vi.fn(),
}))

const reportApi = vi.hoisted(() => ({
    getMyReports: vi.fn(),
}))

vi.mock('@/api/user', () => ({ userApi }))
vi.mock('@/api/report', () => ({ reportApi }))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
    },
}))

vi.mock('@/utils/date', () => ({
    formatDate: (value: string) => value,
}))

const emptyStateStub = {
    name: 'EmptyState',
    template: '<div data-testid="empty-state" />',
}

const errorStateStub = {
    name: 'ErrorState',
    props: ['message', 'showRetry'],
    emits: ['retry'],
    template: '<button type="button" data-testid="error-state" @click="$emit(\'retry\')">{{ message }}</button>',
}

const mountList = (component: typeof ScrapList) => {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
            },
        },
    })

    return mount(component, {
        global: {
            plugins: [[VueQueryPlugin, { queryClient }]],
            mocks: {
                $t: (key: string) => key,
            },
            stubs: {
                BaseBadge: true,
                BaseSkeleton: true,
                EmptyState: emptyStateStub,
                ErrorState: errorStateStub,
                PageSizeSelector: true,
                Pagination: true,
                PostList: true,
            },
        },
    })
}

describe('paginated user lists', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        userApi.getMyScraps.mockRejectedValue(new Error('network'))
        userApi.getMyPointHistories.mockRejectedValue(new Error('network'))
        reportApi.getMyReports.mockRejectedValue(new Error('network'))
    })

    it('shows an error state when scraps fail to load', async () => {
        const wrapper = mountList(ScrapList)

        await flushPromises()

        expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    })

    it('shows an error state when point history fails to load', async () => {
        const wrapper = mountList(PointHistory)

        await flushPromises()

        expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    })

    it('shows an error state when reports fail to load', async () => {
        const wrapper = mountList(MyReports)

        await flushPromises()

        expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    })
})

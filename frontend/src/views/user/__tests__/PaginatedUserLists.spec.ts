import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ScrapList from '../ScrapList.vue'
import PointHistory from '../PointHistory.vue'
import MyReports from '../MyReports.vue'

const userApi = vi.hoisted(() => ({
    getMyScraps: vi.fn(),
    getScrapFolders: vi.fn(),
    createScrapFolder: vi.fn(),
    updateScrapFolder: vi.fn(),
    deleteScrapFolder: vi.fn(),
    getMyPointHistories: vi.fn(),
}))

const confirmMock = vi.hoisted(() => vi.fn())

const reportApi = vi.hoisted(() => ({
    getMyReports: vi.fn(),
}))

vi.mock('@/api/user', () => ({ userApi }))
vi.mock('@/api/report', () => ({ reportApi }))
vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({ sessionGeneration: 0 }),
}))
vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: confirmMock,
    }),
}))

vi.mock('vue-i18n', async (importOriginal) => {
    const actual = await importOriginal<typeof import('vue-i18n')>()

    return {
        ...actual,
        useI18n: () => ({
            t: (key: string) => key,
        }),
    }
})

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

const baseBadgeStub = {
    name: 'BaseBadge',
    template: '<span data-testid="base-badge"><slot /></span>',
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
                BaseBadge: baseBadgeStub,
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
        userApi.getScrapFolders.mockResolvedValue({
            data: {
                success: true,
                data: [],
            },
        })
        userApi.createScrapFolder.mockResolvedValue({ data: { success: true, data: {} } })
        userApi.updateScrapFolder.mockResolvedValue({ data: { success: true, data: {} } })
        userApi.deleteScrapFolder.mockResolvedValue({ data: { success: true } })
        confirmMock.mockResolvedValue(true)
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

    it('confirms before deleting a scrap folder and includes the scrap count', async () => {
        userApi.getMyScraps
            .mockResolvedValueOnce({
                data: {
                    data: {
                        content: [],
                        page: 0,
                        size: 15,
                        totalElements: 0,
                        totalPages: 0,
                        hasNext: false,
                        hasPrevious: false,
                        last: true,
                    },
                },
            })
            .mockResolvedValueOnce({
                data: {
                    data: {
                        content: [],
                        page: 0,
                        size: 1,
                        totalElements: 3,
                        totalPages: 1,
                        hasNext: false,
                        hasPrevious: false,
                        last: true,
                    },
                },
            })
        userApi.getScrapFolders.mockResolvedValue({
            data: {
                success: true,
                data: [{ folderId: 7, name: 'Keep', sortOrder: 0 }],
            },
        })

        const wrapper = mountList(ScrapList)
        await flushPromises()

        const deleteButton = wrapper.find('[aria-label="user.scrapList.deleteFolder"]')
        await deleteButton.trigger('click')
        await flushPromises()

        expect(userApi.getMyScraps).toHaveBeenCalledWith({ folderId: 7, page: 0, size: 1 })
        expect(confirmMock).toHaveBeenCalledWith(
            'user.scrapList.deleteConfirmMessage',
            'user.scrapList.deleteConfirmTitle',
            'common.delete',
            'common.cancel',
        )
        expect(userApi.deleteScrapFolder).toHaveBeenCalledWith(7)
    })

    it('updates a scrap folder name inline', async () => {
        userApi.getMyScraps.mockResolvedValue({
            data: {
                data: {
                    content: [],
                    page: 0,
                    size: 15,
                    totalElements: 0,
                    totalPages: 0,
                    hasNext: false,
                    hasPrevious: false,
                    last: true,
                },
            },
        })
        userApi.getScrapFolders.mockResolvedValue({
            data: {
                success: true,
                data: [{ folderId: 7, name: 'Old', sortOrder: 0 }],
            },
        })

        const wrapper = mountList(ScrapList)
        await flushPromises()

        await wrapper.find('[aria-label="user.scrapList.editFolder"]').trigger('click')
        await wrapper.get('#scrap-folder-edit-7').setValue('New')
        await wrapper.find('[aria-label="user.scrapList.saveFolder"]').trigger('click')
        await flushPromises()

        expect(userApi.updateScrapFolder).toHaveBeenCalledWith(7, { name: 'New' })
    })

    it('distinguishes earned and spent point history rows', async () => {
        userApi.getMyPointHistories.mockResolvedValueOnce({
            data: {
                data: {
                    content: [
                        {
                            historyId: 1,
                            type: 'EARN',
                            amount: 50,
                            balanceAfter: 150,
                            description: 'post reward',
                            createdAt: '2026-07-08T10:00:00',
                        },
                        {
                            historyId: 2,
                            type: 'SPEND',
                            amount: -20,
                            balanceAfter: 130,
                            description: 'shop purchase',
                            createdAt: '2026-07-08T11:00:00',
                        },
                        {
                            historyId: 3,
                            type: 'EXPIRE',
                            amount: -10,
                            balanceAfter: 120,
                            description: 'expired points',
                            createdAt: '2026-07-08T12:00:00',
                        },
                    ],
                    page: 0,
                    size: 15,
                    totalElements: 3,
                    totalPages: 1,
                    hasNext: false,
                    hasPrevious: false,
                    last: true,
                },
            },
        })

        const wrapper = mountList(PointHistory)

        await flushPromises()

        expect(wrapper.text()).toContain('user.pointsHistory.transaction.earned')
        expect(wrapper.text()).toContain('user.pointsHistory.transaction.spent')
        expect(wrapper.text()).toContain('user.pointsHistory.transaction.expired')
        expect(wrapper.text()).toContain('+50 P')
        expect(wrapper.text()).toContain('-20 P')
        expect(wrapper.text()).toContain('-10 P')
        expect(wrapper.text()).toContain('150 P')
        expect(wrapper.text()).toContain('130 P')
        expect(wrapper.text()).toContain('120 P')
    })

    it('shows an error state when reports fail to load', async () => {
        const wrapper = mountList(MyReports)

        await flushPromises()

        expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    })
})

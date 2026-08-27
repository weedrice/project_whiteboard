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

const commonCodeApi = vi.hoisted(() => ({
    getDetails: vi.fn(),
}))

vi.mock('@/api/user', () => ({ userApi }))
vi.mock('@/api/report', () => ({ reportApi }))
vi.mock('@/api/commonCode', () => ({ commonCodeApi }))
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

const pageResponse = () => ({
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
        commonCodeApi.getDetails.mockResolvedValue({
            data: {
                success: true,
                data: ['EARN', 'SPEND', 'EXPIRE', 'PENALTY', 'REWARD_REVERSAL'].map((codeValue, index) => ({
                    id: index + 1,
                    typeCode: 'POINT_CHANGE_TYPE',
                    codeValue,
                    codeName: codeValue,
                    sortOrder: (index + 1) * 10,
                    isActive: true,
                })),
            },
        })
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

        expect(userApi.getMyScraps).toHaveBeenCalledWith(
            { folderId: 7, page: 0, size: 1 },
            { signal: expect.any(AbortSignal) },
        )
        expect(confirmMock).toHaveBeenCalledWith(
            'user.scrapList.deleteConfirmMessage',
            'user.scrapList.deleteConfirmTitle',
            'common.delete',
            'common.cancel',
        )
        expect(userApi.deleteScrapFolder).toHaveBeenCalledWith(7, { signal: expect.any(AbortSignal) })
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

        expect(userApi.updateScrapFolder).toHaveBeenCalledWith(
            7,
            { name: 'New' },
            { signal: expect.any(AbortSignal) },
        )
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

    it('filters point history by explicit type and resets to the first page', async () => {
        userApi.getMyPointHistories.mockResolvedValue(pageResponse())
        const wrapper = mountList(PointHistory)
        await flushPromises()

        await wrapper.get('#point-history-type-filter').setValue('PENALTY')
        await flushPromises()

        expect(userApi.getMyPointHistories).toHaveBeenLastCalledWith(
            { page: 0, size: 15, type: 'PENALTY' },
            { signal: expect.any(AbortSignal) },
        )
    })

    it('uses the active point common code order and hides unsupported values', async () => {
        commonCodeApi.getDetails.mockResolvedValueOnce({
            data: {
                success: true,
                data: [
                    { id: 1, typeCode: 'POINT_CHANGE_TYPE', codeValue: 'SPEND', codeName: '사용', sortOrder: 10, isActive: true },
                    { id: 2, typeCode: 'POINT_CHANGE_TYPE', codeValue: 'ADMIN_ADJ', codeName: '조정', sortOrder: 20, isActive: true },
                    { id: 3, typeCode: 'POINT_CHANGE_TYPE', codeValue: 'EARN', codeName: '획득', sortOrder: 30, isActive: true },
                    { id: 4, typeCode: 'POINT_CHANGE_TYPE', codeValue: 'EXPIRE', codeName: '만료', sortOrder: 40, isActive: false },
                ],
            },
        })
        userApi.getMyPointHistories.mockResolvedValue(pageResponse())

        const wrapper = mountList(PointHistory)
        await flushPromises()
        const select = wrapper.get('#point-history-type-filter').element as HTMLSelectElement
        const values = Array.from(select.options)
            .map((option) => option.value)

        expect(values).toEqual(['', 'SPEND', 'EARN'])
    })

    it('shows an error state when reports fail to load', async () => {
        const wrapper = mountList(MyReports)

        await flushPromises()

        expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    })
})

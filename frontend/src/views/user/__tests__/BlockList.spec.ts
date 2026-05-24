import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BlockList from '../BlockList.vue'
import { userApi } from '@/api/user'

vi.mock('@/api/user', () => ({
    userApi: {
        getBlockList: vi.fn(),
    },
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
    },
}))

vi.mock('vue-i18n', () => ({
    createI18n: vi.fn(() => ({
        global: {
            t: (key: string) => key,
        },
    })),
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

const errorStateStub = {
    name: 'ErrorState',
    props: ['message', 'showRetry'],
    emits: ['retry'],
    template: '<button type="button" data-testid="error-state" @click="$emit(\'retry\')">{{ message }}</button>',
}

const mountBlockList = () => mount(BlockList, {
    global: {
        mocks: {
            $t: (key: string) => key,
        },
        stubs: {
            BaseSkeleton: true,
            BlockButton: true,
            EmptyState: true,
            ErrorState: errorStateStub,
            UserX: true,
        },
    },
})

describe('BlockList', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('shows an error state instead of an empty state when loading fails', async () => {
        vi.mocked(userApi.getBlockList).mockRejectedValueOnce(new Error('network'))

        const wrapper = mountBlockList()
        await flushPromises()

        expect(wrapper.get('[data-testid="error-state"]').text()).toBe('common.messages.loadFailed')
        expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(false)
    })

    it('retries loading blocked users from the error state', async () => {
        vi.mocked(userApi.getBlockList)
            .mockRejectedValueOnce(new Error('network'))
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: [],
                },
            } as never)

        const wrapper = mountBlockList()
        await flushPromises()

        await wrapper.get('[data-testid="error-state"]').trigger('click')
        await flushPromises()

        expect(userApi.getBlockList).toHaveBeenCalledTimes(2)
    })
})

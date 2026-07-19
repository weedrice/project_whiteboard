import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import UserManagement from '../UserManagement.vue'
import type { User } from '@/types'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => {
    const user: User = {
        userId: 7,
        loginId: 'hong',
        displayName: '홍길동',
        email: 'hong@example.com',
        role: 'USER',
        status: 'ACTIVE',
        isEmailVerified: true,
        isSuperAdmin: false,
        createdAt: '2026-01-01T00:00:00',
        lastLoginAt: '2026-01-02T00:00:00',
    }
    const updateUserStatus = vi.fn()
    const refetchUsers = vi.fn()
    const confirmWithReason = vi.fn()
    const authStore = { sessionGeneration: 1, user: { userId: 99 } }

    return {
        user,
        updateUserStatus,
        refetchUsers,
        confirmWithReason,
        authStore,
        userParams: null as { value: { page: number; size: number } } | null,
    }
})

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string, params?: Record<string, string>) => ({
            'admin.users.title': '사용자 관리',
            'admin.users.description': '사용자를 관리합니다.',
            'admin.users.filters.userSearch': '사용자 검색',
            'admin.users.searchPlaceholder': '사용자 검색',
            'admin.users.table.status': '상태',
            'admin.users.table.joinedAt': '가입일',
            'admin.common.detail': '상세',
            'admin.common.rowDetailAria': `${params?.name} 상세 보기`,
            'common.id': 'ID',
            'common.loginId': '로그인 ID',
            'common.displayName': '닉네임',
            'common.email': '이메일',
            'common.noData': '데이터가 없습니다',
        }[key] ?? key),
    }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: vi.fn(),
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => mocks.authStore,
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirmWithReason: mocks.confirmWithReason,
    }),
}))

vi.mock('@/features/admin/useAdmin', () => ({
    useAdmin: () => ({
        useUsers: (params: { value: { page: number; size: number } }) => {
            mocks.userParams = params
            return {
            data: ref({
                content: [mocks.user],
                totalElements: 1,
                totalPages: 1,
                number: 0,
            }),
            isLoading: ref(false),
            isError: ref(false),
            refetch: mocks.refetchUsers,
        }
        },
        useUpdateUserStatus: () => ({
            mutateAsync: mocks.updateUserStatus,
        }),
    }),
}))

const iconStub = defineComponent({
    setup() {
        return () => h('span')
    },
})

const BaseInputStub = defineComponent({
    props: {
        id: String,
        modelValue: String,
        label: String,
        placeholder: String,
        hideLabel: Boolean,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('div', [
            props.label ? h('label', { for: props.id, class: props.hideLabel ? 'sr-only' : '' }, props.placeholder || props.label) : null,
            h('input', {
                id: props.id,
                value: props.modelValue,
                placeholder: props.placeholder,
                onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
            }),
        ])
    },
})

const BaseBadgeStub = defineComponent({
    template: '<span><slot /></span>',
})

const UserDetailModalStub = defineComponent({
    props: {
        isOpen: Boolean,
        userId: Number,
    },
    template: '<div data-test="user-detail-modal" :data-open="String(isOpen)" :data-user-id="userId ?? \'\'"></div>',
})

describe('UserManagement', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.authStore.sessionGeneration = 1
        mocks.authStore.user = { userId: 99 }
        mocks.user.status = 'ACTIVE'
        mocks.confirmWithReason.mockResolvedValue('policy violation')
        mocks.updateUserStatus.mockResolvedValue(undefined)
        mocks.refetchUsers.mockResolvedValue({
            data: {
                content: [mocks.user],
                totalElements: 1,
                totalPages: 1,
                number: 0,
            },
        })
        mocks.userParams = null
    })

    it('opens user detail from the row action button', async () => {
        const wrapper = mount(UserManagement, {
            global: {
                stubs: {
                    Search: iconStub,
                    BaseInput: BaseInputStub,
                    BaseBadge: BaseBadgeStub,
                    UserDetailModal: UserDetailModalStub,
                    Pagination: true,
                },
            },
        })

        const detailButton = wrapper.get('button[aria-label="홍길동 상세 보기"]')
        expect(detailButton.text()).toBe('상세')

        await detailButton.trigger('click')

        const modal = wrapper.get('[data-test="user-detail-modal"]')
        expect(modal.attributes('data-open')).toBe('true')
        expect(modal.attributes('data-user-id')).toBe('7')
        expect(mocks.updateUserStatus).not.toHaveBeenCalled()
    })

    it('connects a visible label to the user search field', () => {
        const wrapper = mount(UserManagement, {
            global: {
                stubs: {
                    Search: iconStub,
                    BaseInput: BaseInputStub,
                    BaseBadge: BaseBadgeStub,
                    UserDetailModal: UserDetailModalStub,
                    Pagination: true,
                },
            },
        })

        const label = wrapper.get('label[for="admin-user-filter-q"]')

        expect(label.text()).toBe('사용자 검색')
        expect(wrapper.get('input[placeholder="사용자 검색"]').attributes('placeholder')).toBe('사용자 검색')
    })

    it('connects the page-size label to its select control', () => {
        const wrapper = mount(UserManagement, {
            global: {
                stubs: {
                    Search: iconStub,
                    BaseInput: BaseInputStub,
                    BaseBadge: BaseBadgeStub,
                    UserDetailModal: UserDetailModalStub,
                    Pagination: true,
                },
            },
        })

        expect(wrapper.find('label[for="admin-user-page-size"]').exists()).toBe(true)
        expect(wrapper.find('select#admin-user-page-size').exists()).toBe(true)
    })

    it('does not change status when the session changes while confirmation is open', async () => {
        const confirmation = createDeferred<string | null>()
        mocks.confirmWithReason.mockReturnValueOnce(confirmation.promise)
        const wrapper = mount(UserManagement, {
            global: {
                stubs: {
                    Search: iconStub,
                    BaseInput: BaseInputStub,
                    BaseBadge: BaseBadgeStub,
                    UserDetailModal: UserDetailModalStub,
                    Pagination: true,
                },
            },
        })

        await wrapper.get('button[aria-label="admin.users.status.SUSPENDED"]').trigger('click')
        mocks.authStore.sessionGeneration = 2
        confirmation.resolve('policy violation')
        await flushPromises()

        expect(mocks.updateUserStatus).not.toHaveBeenCalled()
    })

    it('does not change status when the target status changes while confirmation is open', async () => {
        const confirmation = createDeferred<string | null>()
        mocks.confirmWithReason.mockReturnValueOnce(confirmation.promise)
        const wrapper = mount(UserManagement, {
            global: {
                stubs: {
                    Search: iconStub,
                    BaseInput: BaseInputStub,
                    BaseBadge: BaseBadgeStub,
                    UserDetailModal: UserDetailModalStub,
                    Pagination: true,
                },
            },
        })

        await wrapper.get('button[aria-label="admin.users.status.SUSPENDED"]').trigger('click')
        mocks.user.status = 'SUSPENDED'
        confirmation.resolve('policy violation')
        await flushPromises()

        expect(mocks.updateUserStatus).not.toHaveBeenCalled()
    })

    it('clamps and refetches when a status change removes the last filtered-page item', async () => {
        mocks.refetchUsers
            .mockResolvedValueOnce({
                data: {
                    content: [],
                    totalElements: 10,
                    totalPages: 2,
                    number: 2,
                },
            })
            .mockResolvedValueOnce({
                data: {
                    content: [mocks.user],
                    totalElements: 10,
                    totalPages: 2,
                    number: 1,
                },
            })
        const wrapper = mount(UserManagement, {
            global: {
                stubs: {
                    Search: iconStub,
                    BaseInput: BaseInputStub,
                    BaseBadge: BaseBadgeStub,
                    UserDetailModal: UserDetailModalStub,
                    Pagination: true,
                },
            },
        })
        const table = wrapper.findComponent({ name: 'AdminPaginatedTable' })
        table.vm.$emit('pageChange', 2)
        await wrapper.vm.$nextTick()
        expect(mocks.userParams?.value.page).toBe(2)

        await wrapper.get('button[aria-label="admin.users.status.SUSPENDED"]').trigger('click')
        await flushPromises()

        expect(mocks.updateUserStatus).toHaveBeenCalledOnce()
        expect(mocks.refetchUsers).toHaveBeenCalledTimes(2)
        expect(mocks.userParams?.value.page).toBe(1)
    })
})

import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import UserDetailModal from '../UserDetailModal.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key
    })
}))

vi.mock('@/utils/date', () => ({
    formatDate: (value: string) => value
}))

vi.mock('@/utils/commentContent', () => ({
    isEmoticonOnlyContent: () => false,
    renderCommentContentHtml: (content?: string) => content ?? ''
}))

vi.mock('@/composables/useAdmin', () => ({
    useAdmin: () => ({
        useAdminUserDetail: () => ({
            data: ref({
                userId: 1,
                loginId: 'writer',
                displayName: 'Writer',
                email: 'writer@test.com',
                status: 'ACTIVE',
                role: 'USER',
                isEmailVerified: true,
                createdAt: '2026-04-21T00:00:00',
                postCount: 1,
                commentCount: 1,
                subscriptionCount: 1,
                reportSummary: { pendingCount: 0 },
                sanctionSummary: { count: 0 }
            }),
            isLoading: ref(false)
        }),
        useAdminUserPosts: () => ({
            data: ref({
                content: [{
                    postId: 10,
                    title: 'deleted post',
                    boardName: 'Free',
                    boardUrl: 'free',
                    viewCount: 1,
                    likeCount: 2,
                    commentCount: 3,
                    deleted: true,
                    notice: true,
                    nsfw: false,
                    spoiler: false,
                    secret: true,
                    authorType: 'USER',
                    createdAt: '2026-04-21T00:00:00'
                }],
                number: 0,
                totalPages: 1
            }),
            isLoading: ref(false)
        }),
        useAdminUserComments: () => ({
            data: ref({
                content: [{
                    commentId: 20,
                    content: 'hidden comment',
                    authorType: 'USER',
                    parentId: 19,
                    depth: 1,
                    likeCount: 4,
                    deleted: true,
                    createdAt: '2026-04-21T00:00:00',
                    post: {
                        postId: 30,
                        title: 'post title',
                        boardId: 40,
                        boardName: 'Secret',
                        boardUrl: 'secret',
                        deleted: true,
                        boardActive: false,
                        boardPublic: false
                    }
                }],
                number: 0,
                totalPages: 1
            }),
            isLoading: ref(false)
        }),
        useAdminUserSubscriptions: () => ({
            data: ref({
                content: [{
                    boardId: 50,
                    boardName: 'Hidden',
                    boardUrl: 'hidden',
                    sortOrder: 7,
                    role: 'MEMBER',
                    boardActive: false,
                    boardPublic: false,
                    subscriptionAccessible: false,
                    inaccessibleReason: 'INACTIVE'
                }],
                number: 0,
                totalPages: 1
            }),
            isLoading: ref(false)
        })
    })
}))

const BaseModalStub = defineComponent({
    props: {
        isOpen: Boolean,
        title: String
    },
    setup(props, { slots }) {
        return () => props.isOpen ? h('div', { 'data-test': 'modal' }, [
            h('h1', props.title),
            slots.default?.()
        ]) : null
    }
})

const BaseBadgeStub = defineComponent({
    setup(_, { slots }) {
        return () => h('span', slots.default?.())
    }
})

const BaseButtonStub = defineComponent({
    emits: ['click'],
    setup(_, { slots, emit }) {
        return () => h('button', { onClick: () => emit('click') }, slots.default?.())
    }
})

describe('UserDetailModal', () => {
    it('renders admin-specific metadata for posts, comments, and subscriptions', async () => {
        const wrapper = mount(UserDetailModal, {
            props: {
                isOpen: true,
                userId: 1
            },
            global: {
                stubs: {
                    BaseModal: BaseModalStub,
                    BaseBadge: BaseBadgeStub,
                    BaseButton: BaseButtonStub
                }
            }
        })

        expect(wrapper.text()).toContain('deleted post')
        expect(wrapper.text()).toContain('Free')

        await wrapper.findAll('button')[1]?.trigger('click')
        expect(wrapper.text()).toContain('hidden comment')
        expect(wrapper.text()).toContain('depth 1')

        await wrapper.findAll('button')[2]?.trigger('click')
        expect(wrapper.text()).toContain('/hidden')
        expect(wrapper.text()).toContain('MEMBER')
    })
})

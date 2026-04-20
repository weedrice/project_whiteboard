import { flushPromises, mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { comment as commentLocale } from '@/locales/comment'
import CommentItem from '../CommentItem.vue'

const useRepliesMock = vi.fn()

vi.mock('@/composables/useComment', () => ({
    useComment: () => ({
        useReplies: useRepliesMock,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({
        isAuthenticated: false,
        user: null,
    }),
}))

vi.mock('vue-i18n', async (importOriginal) => {
    const actual = await importOriginal<typeof import('vue-i18n')>()

    return {
        ...actual,
        useI18n: () => ({
            t: (key: string, params?: Record<string, unknown>) => {
                if (key === 'comment.viewReplies') {
                    return commentLocale.viewReplies.replace('{count}', String(params?.count ?? '0'))
                }
                if (key === 'comment.hideReplies') {
                    return commentLocale.hideReplies
                }
                if (key === 'comment.deleted') {
                    return commentLocale.deleted
                }
                if (key === 'comment.loadRepliesFailed') {
                    return commentLocale.loadRepliesFailed
                }
                if (key === 'comment.reply') {
                    return commentLocale.reply
                }
                if (key === 'comment.agentBadge') {
                    return commentLocale.agentBadge
                }
                if (key === 'common.loading') {
                    return '로딩 중...'
                }
                if (key === 'common.messages.unknown') {
                    return '알 수 없음'
                }
                if (key === 'common.edit') {
                    return '수정'
                }
                if (key === 'common.delete') {
                    return '삭제'
                }
                return key
            },
        }),
    }
})

vi.mock('@/utils/date', () => ({
    formatDate: () => '2026-04-20 12:00',
    formatDateShort: () => '04-20',
}))

vi.mock('@/utils/commentContent', () => ({
    isEmoticonOnlyContent: () => false,
    renderCommentContentHtml: (content: string) => content,
}))

describe('CommentItem', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        useRepliesMock.mockImplementation(() => ({
            data: ref({
                content: [
                    {
                        commentId: 2,
                        content: 'child reply',
                        author: {
                            userId: 2,
                            displayName: 'reply-user',
                            authorType: 'USER',
                        },
                        likeCount: 0,
                        isDeleted: false,
                        createdAt: '2026-04-20T12:00:00',
                        hasReplies: false,
                        replyCount: 0,
                        children: [],
                    },
                ],
                hasNext: false,
                hasPrevious: false,
                page: 0,
                size: 50,
                totalElements: 1,
                totalPages: 1,
            }),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn(),
        }))
    })

    it('loads replies lazily when the toggle is opened', async () => {
        const wrapper = mount(CommentItem, {
            props: {
                comment: {
                    commentId: 1,
                    content: 'parent',
                    author: {
                        userId: 1,
                        displayName: 'author',
                        authorType: 'USER',
                    },
                    likeCount: 0,
                    isDeleted: false,
                    createdAt: '2026-04-20T12:00:00',
                    hasReplies: true,
                    replyCount: 1,
                    children: [],
                },
                postId: 100,
                boardUrl: 'free',
            },
            global: {
                stubs: {
                    UserMenu: {
                        props: ['displayName'],
                        template: '<span>{{ displayName }}</span>',
                    },
                    CommentForm: {
                        template: '<div />',
                    },
                    CornerDownRight: true,
                    UserIcon: true,
                },
            },
        })

        expect(wrapper.text()).not.toContain('child reply')
        expect(useRepliesMock).toHaveBeenCalledTimes(1)

        const enabled = useRepliesMock.mock.calls[0][2]
        expect((enabled as ReturnType<typeof computed>).value).toBe(false)

        await wrapper.get('button').trigger('click')
        await flushPromises()

        expect((enabled as ReturnType<typeof computed>).value).toBe(true)
        expect(wrapper.text()).toContain('child reply')
    })
})

import { flushPromises, mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { comment as commentLocale } from '@/locales/comment'
import CommentItem from '../CommentItem.vue'

const useRepliesMock = vi.fn()
const useAuthStoreMock = vi.fn()

vi.mock('@/composables/useComment', () => ({
    useComment: () => ({
        useReplies: useRepliesMock,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => useAuthStoreMock(),
}))

vi.mock('vue-router', async (importOriginal) => {
    const actual = await importOriginal<typeof import('vue-router')>()

    return {
        ...actual,
        useRouter: () => ({
            push: vi.fn(),
        }),
    }
})

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
                if (key === 'comment.blockedAuthor') {
                    return commentLocale.blockedAuthor
                }
                if (key === 'comment.blockedContent') {
                    return commentLocale.blockedContent
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
    renderCommentContentHtml: (content: string | null | undefined) => content ?? '',
}))

describe('CommentItem', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        useAuthStoreMock.mockReturnValue({
            isAuthenticated: false,
            user: null,
        })
        useRepliesMock.mockImplementation((_commentId, _replyParams, enabled) => {
            void enabled.value

            return {
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
            }
        })
    })

    it('loads and shows replies by default when the parent has replies', async () => {
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
                mocks: {
                    $t: (key: string) => {
                        if (key === 'common.messages.unknown') {
                            return '알 수 없음'
                        }
                        if (key === 'comment.deleted') {
                            return commentLocale.deleted
                        }
                        return key
                    },
                },
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

        await flushPromises()

        expect(wrapper.text()).toContain('child reply')
        expect(useRepliesMock).toHaveBeenCalled()

        const enabled = useRepliesMock.mock.calls[0][2]
        expect((enabled as ReturnType<typeof computed>).value).toBe(true)

        await wrapper.get('button').trigger('click')
        await flushPromises()

        expect((enabled as ReturnType<typeof computed>).value).toBe(false)
        expect(wrapper.text()).not.toContain('child reply')
    })

    it('hides the replies toggle for deleted comments', () => {
        const wrapper = mount(CommentItem, {
            props: {
                comment: {
                    commentId: 1,
                    content: 'deleted parent',
                    author: {
                        userId: 1,
                        displayName: 'author',
                        authorType: 'USER',
                    },
                    likeCount: 0,
                    isDeleted: true,
                    createdAt: '2026-04-20T12:00:00',
                    hasReplies: true,
                    replyCount: 3,
                    children: [],
                },
                postId: 100,
                boardUrl: 'free',
            },
            global: {
                mocks: {
                    $t: (key: string) => {
                        if (key === 'common.messages.unknown') {
                            return '알 수 없음'
                        }
                        return key
                    },
                },
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

        expect(wrapper.text()).not.toContain(commentLocale.viewReplies.replace('{count}', '3'))
        expect(wrapper.text()).toContain('comment.deleted')
    })

    it('renders replies safely when auth store is unavailable', async () => {
        useAuthStoreMock.mockReturnValueOnce(undefined)

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
                mocks: {
                    $t: (key: string) => key,
                },
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

        await flushPromises()

        expect(wrapper.text()).toContain('child reply')
        expect(wrapper.findAll('button').some((button) => button.text() === commentLocale.reply)).toBe(false)
    })

    it('renders blocked author state without author menu or comment actions', () => {
        useAuthStoreMock.mockReturnValueOnce({
            isAuthenticated: true,
            user: { userId: 1 },
        })

        const wrapper = mount(CommentItem, {
            props: {
                comment: {
                    commentId: 1,
                    content: null,
                    author: null,
                    isBlockedAuthor: true,
                    maskedAuthorId: 2,
                    likeCount: 0,
                    isDeleted: false,
                    createdAt: '2026-04-20T12:00:00',
                    hasReplies: false,
                    replyCount: 0,
                    children: [],
                },
                postId: 100,
                boardUrl: 'free',
            },
            global: {
                mocks: {
                    $t: (key: string) => {
                        if (key === 'comment.blockedAuthor') {
                            return commentLocale.blockedAuthor
                        }
                        if (key === 'comment.blockedContent') {
                            return commentLocale.blockedContent
                        }
                        return key
                    },
                },
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

        expect(wrapper.text()).toContain(commentLocale.blockedAuthor)
        expect(wrapper.text()).toContain(commentLocale.blockedContent)
        expect(wrapper.findAll('button').some((button) => button.text() === commentLocale.reply)).toBe(false)
        expect(wrapper.text()).not.toContain('common.edit')
        expect(wrapper.text()).not.toContain('common.delete')
    })
})

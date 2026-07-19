import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    mapCommentNotificationRoute,
    mapPostNotificationRoute,
    useNotificationNavigation
} from '../useNotificationNavigation'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { messageApi } from '@/api/message'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import type { Notification } from '@/types'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => ({
    routerPush: vi.fn(),
    markAsRead: vi.fn(),
    addToast: vi.fn(),
    loggerError: vi.fn(),
    auth: { sessionGeneration: 1 },
}))

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: mocks.routerPush,
    }),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('@/api/post', () => ({
    postApi: {
        getPost: vi.fn(),
    },
}))

vi.mock('@/api/comment', () => ({
    commentApi: {
        getComment: vi.fn(),
    },
}))

vi.mock('@/api/message', () => ({
    messageApi: {
        getMessage: vi.fn(),
    },
}))

vi.mock('@/features/notifications/queries/useNotification', () => ({
    useNotification: () => ({
        useMarkAsRead: () => ({
            mutate: mocks.markAsRead,
        }),
    }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => mocks.auth,
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: mocks.loggerError,
    },
}))

const makeNotification = (overrides: Partial<Notification>): Notification => ({
    notificationId: 10,
    message: 'message',
    sourceType: 'POST',
    sourceId: 99,
    isRead: false,
    createdAt: '2026-05-18T00:00:00Z',
    actor: {
        userId: 1,
        displayName: 'User',
    },
    actorDisplayName: 'User',
    actorInitial: 'U',
    ...overrides,
    notificationType: overrides.notificationType ?? 'COMMENT',
})

describe('useNotificationNavigation', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.auth.sessionGeneration = 1
    })

    it('marks unread post notifications as read and navigates to the post route', async () => {
        vi.mocked(postApi.getPost).mockResolvedValueOnce(
            apiSuccessDataResponse<typeof postApi.getPost>({
                board: {
                    boardUrl: 'free',
                },
            })
        )

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({ sourceType: 'POST', sourceId: 99 }))

        expect(mocks.markAsRead).toHaveBeenCalledWith(10)
        expect(mocks.routerPush).toHaveBeenCalledWith('/board/free/post/99')
    })

    it('uses an internal targetUrl without fetching the source resource', async () => {
        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({
            sourceType: 'COMMENT',
            sourceId: 50,
            targetUrl: '/board/free/post/99#comment-50',
        }))

        expect(mocks.markAsRead).toHaveBeenCalledWith(10)
        expect(postApi.getPost).not.toHaveBeenCalled()
        expect(commentApi.getComment).not.toHaveBeenCalled()
        expect(mocks.routerPush).toHaveBeenCalledWith('/board/free/post/99#comment-50')
    })

    it('resolves a message source before opening a generic mailbox target', async () => {
        vi.mocked(messageApi.getMessage).mockResolvedValueOnce(
            apiSuccessDataResponse<typeof messageApi.getMessage>({
                messageId: 72,
                content: 'hello',
                partner: { userId: 31, displayName: 'Partner' },
                isRead: false,
                createdAt: '2026-07-19T00:00:00Z',
            }),
        )

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({
            sourceType: 'MESSAGE',
            sourceId: 72,
            targetUrl: '/mypage/messages',
        }))

        expect(messageApi.getMessage).toHaveBeenCalledWith(72, {
            signal: expect.any(AbortSignal),
            skipGlobalErrorHandler: true,
        })
        expect(mocks.routerPush).toHaveBeenCalledWith({
            name: 'MyMessages',
            query: { partnerId: '31' },
        })
    })

    it('ignores unsafe absolute targetUrl values and falls back to source lookup', async () => {
        vi.mocked(postApi.getPost).mockResolvedValueOnce(
            apiSuccessDataResponse<typeof postApi.getPost>({
                board: {
                    boardUrl: 'free',
                },
            })
        )

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({
            sourceType: 'POST',
            sourceId: 99,
            targetUrl: '//evil.example/path',
        }))

        expect(postApi.getPost).toHaveBeenCalledWith(99, {
            signal: expect.any(AbortSignal),
            skipGlobalErrorHandler: true,
        })
        expect(mocks.routerPush).toHaveBeenCalledWith('/board/free/post/99')
    })

    it('navigates comment notifications to the parent post comment anchor', async () => {
        vi.mocked(commentApi.getComment).mockResolvedValueOnce(
            apiSuccessDataResponse<typeof commentApi.getComment>({
                commentId: 50,
                post: {
                    postId: 77,
                    boardUrl: 'notice',
                },
            })
        )

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({ sourceType: 'COMMENT', sourceId: 50, isRead: true }))

        expect(mocks.markAsRead).not.toHaveBeenCalled()
        expect(mocks.routerPush).toHaveBeenCalledWith('/board/notice/post/77#comment-50')
    })

    it('supports flat comment navigation fields from the backend response', async () => {
        vi.mocked(commentApi.getComment).mockResolvedValueOnce(
            apiSuccessDataResponse<typeof commentApi.getComment>({
                commentId: 52,
                boardUrl: 'free',
                postId: 78,
            })
        )

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({ sourceType: 'COMMENT', sourceId: 52 }))

        expect(mocks.routerPush).toHaveBeenCalledWith('/board/free/post/78#comment-52')
    })

    it('uses the toast option for comment navigation failures', async () => {
        vi.mocked(commentApi.getComment).mockRejectedValueOnce(new Error('missing'))

        const { navigateFromNotification } = useNotificationNavigation({ showCommentFailureToast: true })
        await navigateFromNotification(makeNotification({ sourceType: 'COMMENT', sourceId: 51 }))

        expect(mocks.addToast).toHaveBeenCalledWith('common.messages.notFound', 'warning')
        expect(mocks.loggerError).toHaveBeenCalledWith('Failed to navigate to comment:', expect.any(Error))
    })

    it('does not navigate system notifications', async () => {
        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({ sourceType: 'SYSTEM', isRead: true }))

        expect(postApi.getPost).not.toHaveBeenCalled()
        expect(commentApi.getComment).not.toHaveBeenCalled()
        expect(mocks.routerPush).not.toHaveBeenCalled()
    })

    it('ignores an older source lookup after a newer navigation starts', async () => {
        const olderResponse = createDeferred<Awaited<ReturnType<typeof postApi.getPost>>>()
        vi.mocked(postApi.getPost).mockReturnValueOnce(olderResponse.promise)
        const { navigateFromNotification } = useNotificationNavigation()

        const olderNavigation = navigateFromNotification(makeNotification({ sourceType: 'POST', sourceId: 99 }))
        await navigateFromNotification(makeNotification({
            sourceType: 'SYSTEM',
            targetUrl: '/notifications',
        }))
        olderResponse.resolve(apiSuccessDataResponse<typeof postApi.getPost>({
            board: { boardUrl: 'free' },
        }))
        await olderNavigation

        expect(mocks.routerPush).toHaveBeenCalledTimes(1)
        expect(mocks.routerPush).toHaveBeenCalledWith('/notifications')
    })

    it('maps backend post/comment navigation payloads to routes', () => {
        expect(mapPostNotificationRoute({ board: { boardUrl: 'free' } }, 99)).toBe('/board/free/post/99')
        expect(mapPostNotificationRoute({ board: { boardUrl: 'free board' } }, 'a/b')).toBe('/board/free%20board/post/a%2Fb')
        expect(mapPostNotificationRoute({ board: null }, 99)).toBeNull()
        expect(mapCommentNotificationRoute({
            post: {
                boardUrl: 'notice',
                postId: 77,
            },
        }, 50)).toBe('/board/notice/post/77#comment-50')
        expect(mapCommentNotificationRoute({
            post: {
                boardUrl: 'notice board',
                postId: '7/7',
            },
        }, '5/0')).toBe('/board/notice%20board/post/7%2F7#comment-5%2F0')
        expect(mapCommentNotificationRoute({ boardUrl: 'free', postId: 78 }, 52)).toBe('/board/free/post/78#comment-52')
        expect(mapCommentNotificationRoute({ boardUrl: 'free' }, 52)).toBeNull()
    })
})

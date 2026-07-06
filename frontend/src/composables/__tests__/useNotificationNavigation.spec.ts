import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    mapCommentNotificationRoute,
    mapPostNotificationRoute,
    useNotificationNavigation
} from '../useNotificationNavigation'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import type { Notification } from '@/types'

const mocks = vi.hoisted(() => ({
    routerPush: vi.fn(),
    markAsRead: vi.fn(),
    addToast: vi.fn(),
    loggerError: vi.fn(),
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

vi.mock('@/composables/useNotification', () => ({
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
})

describe('useNotificationNavigation', () => {
    beforeEach(() => {
        vi.clearAllMocks()
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

        expect(postApi.getPost).toHaveBeenCalledWith(99)
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

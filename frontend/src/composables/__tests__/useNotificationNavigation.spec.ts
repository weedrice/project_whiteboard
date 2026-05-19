import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useNotificationNavigation } from '../useNotificationNavigation'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
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
    ...overrides,
})

describe('useNotificationNavigation', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('marks unread post notifications as read and navigates to the post route', async () => {
        vi.mocked(postApi.getPost).mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    board: {
                        boardUrl: 'free',
                    },
                },
            },
        } as never)

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({ sourceType: 'POST', sourceId: 99 }))

        expect(mocks.markAsRead).toHaveBeenCalledWith(10)
        expect(mocks.routerPush).toHaveBeenCalledWith('/board/free/post/99')
    })

    it('navigates comment notifications to the parent post comment anchor', async () => {
        vi.mocked(commentApi.getComment).mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    commentId: 50,
                    post: {
                        postId: 77,
                        boardUrl: 'notice',
                    },
                },
            },
        } as never)

        const { navigateFromNotification } = useNotificationNavigation()
        await navigateFromNotification(makeNotification({ sourceType: 'COMMENT', sourceId: 50, isRead: true }))

        expect(mocks.markAsRead).not.toHaveBeenCalled()
        expect(mocks.routerPush).toHaveBeenCalledWith('/board/notice/post/77#comment-50')
    })

    it('supports flat comment navigation fields from the backend response', async () => {
        vi.mocked(commentApi.getComment).mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    commentId: 52,
                    boardUrl: 'free',
                    postId: 78,
                },
            },
        } as never)

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
})

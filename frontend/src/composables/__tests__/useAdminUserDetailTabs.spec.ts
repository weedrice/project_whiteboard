import { describe, expect, it, vi, beforeEach } from 'vitest'
import { nextTick, ref, type Ref } from 'vue'
import { useAdminUserDetailTabs } from '../useAdminUserDetailTabs'

const captured = vi.hoisted(() => ({
    postsUserId: null as Ref<number | null> | null,
    commentsUserId: null as Ref<number | null> | null,
    subscriptionsUserId: null as Ref<number | null> | null,
    postsParams: null as Ref<{ page?: number, size?: number }> | null,
    commentsParams: null as Ref<{ page?: number, size?: number }> | null,
    subscriptionsParams: null as Ref<{ page?: number, size?: number }> | null,
}))

vi.mock('@/composables/useAdmin', () => ({
    useAdmin: () => ({
        useAdminUserPosts: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
            captured.postsUserId = userId
            captured.postsParams = params
            return {
                data: ref({
                    number: 0,
                    totalPages: 3,
                    content: [{
                        postId: 1,
                        boardId: 2,
                        boardName: 'Free',
                        boardUrl: 'free',
                        categoryId: 3,
                        categoryName: 'Notice',
                        title: 'post title',
                        authorType: 'AGENT',
                        agentId: 4,
                        agentName: 'Helper',
                        viewCount: 10,
                        likeCount: 2,
                        commentCount: 5,
                        deleted: false,
                        notice: true,
                        nsfw: false,
                        spoiler: true,
                        secret: false,
                        createdAt: '2026-04-21T00:00:00'
                    }]
                }),
                isLoading: ref(false)
            }
        },
        useAdminUserComments: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
            captured.commentsUserId = userId
            captured.commentsParams = params
            return {
                data: ref({
                    number: 0,
                    totalPages: 3,
                    content: [{
                        commentId: 5,
                        content: 'comment body',
                        authorType: 'USER',
                        parentId: 4,
                        depth: 1,
                        likeCount: 7,
                        deleted: true,
                        createdAt: '2026-04-22T00:00:00',
                        post: {
                            postId: 6,
                            title: 'origin post',
                            boardId: 7,
                            boardName: 'Secret',
                            boardUrl: 'secret',
                            deleted: true,
                            boardActive: false,
                            boardPublic: false
                        }
                    }]
                }),
                isLoading: ref(false)
            }
        },
        useAdminUserSubscriptions: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
            captured.subscriptionsUserId = userId
            captured.subscriptionsParams = params
            return {
                data: ref({
                    number: 0,
                    totalPages: 3,
                    content: [{
                        boardId: 8,
                        boardName: 'Hidden',
                        boardUrl: 'hidden',
                        sortOrder: 9,
                        role: 'MEMBER',
                        boardActive: false,
                        boardPublic: false,
                        subscriptionAccessible: false,
                        inaccessibleReason: 'INACTIVE'
                    }]
                }),
                isLoading: ref(false)
            }
        },
    })
}))

describe('useAdminUserDetailTabs', () => {
    beforeEach(() => {
        captured.postsUserId = null
        captured.commentsUserId = null
        captured.subscriptionsUserId = null
        captured.postsParams = null
        captured.commentsParams = null
        captured.subscriptionsParams = null
    })

    it('passes the user id only to the active tab query', async () => {
        const isOpen = ref(true)
        const userId = ref<number | null>(7)
        const tabs = useAdminUserDetailTabs({ isOpen, userId })

        expect(captured.postsUserId?.value).toBe(7)
        expect(captured.commentsUserId?.value).toBeNull()
        expect(captured.subscriptionsUserId?.value).toBeNull()

        tabs.activeTab.value = 'comments'
        await nextTick()

        expect(captured.postsUserId?.value).toBeNull()
        expect(captured.commentsUserId?.value).toBe(7)
        expect(captured.subscriptionsUserId?.value).toBeNull()

        tabs.activeTab.value = 'subscriptions'
        await nextTick()

        expect(captured.postsUserId?.value).toBeNull()
        expect(captured.commentsUserId?.value).toBeNull()
        expect(captured.subscriptionsUserId?.value).toBe(7)
    })

    it('resets the active tab and page params when the modal opens', async () => {
        const isOpen = ref(true)
        const userId = ref<number | null>(7)
        const tabs = useAdminUserDetailTabs({ isOpen, userId })

        tabs.activeTab.value = 'comments'
        tabs.nextPostsPage()
        await nextTick()

        expect(captured.postsParams?.value.page).toBe(1)

        isOpen.value = false
        await nextTick()
        isOpen.value = true
        await nextTick()

        expect(tabs.activeTab.value).toBe('posts')
        expect(captured.postsParams?.value.page).toBe(0)
        expect(captured.commentsParams?.value.page).toBe(0)
        expect(captured.subscriptionsParams?.value.page).toBe(0)
    })

    it('maps raw admin tab DTOs to view-model items', () => {
        const isOpen = ref(true)
        const userId = ref<number | null>(7)
        const tabs = useAdminUserDetailTabs({ isOpen, userId })

        expect(tabs.postItems.value[0]).toMatchObject({
            postId: 1,
            title: 'post title',
            categoryText: '카테고리: Notice',
            statsText: '조회 10 · 추천 2 · 댓글 5'
        })
        expect(tabs.postItems.value[0].badges.map((badge) => badge.label)).toEqual([
            '노출중',
            '공지',
            '스포일러',
            'Agent Helper'
        ])
        expect(tabs.commentItems.value[0]).toMatchObject({
            commentId: 5,
            content: 'comment body',
            statsText: '좋아요 7 · depth 1'
        })
        expect(tabs.commentItems.value[0].badges.map((badge) => badge.label)).toEqual([
            '삭제됨',
            '답글',
            '원문 삭제',
            '게시판 비활성',
            '비공개 게시판'
        ])
        expect(tabs.subscriptionItems.value[0]).toMatchObject({
            boardId: 8,
            boardName: 'Hidden',
            boardPath: '/hidden',
            sortOrderText: '정렬 순서 9'
        })
        expect(tabs.subscriptionItems.value[0].badges.map((badge) => badge.label)).toEqual([
            '비활성',
            '비활성',
            '비공개',
            'MEMBER'
        ])
    })
})

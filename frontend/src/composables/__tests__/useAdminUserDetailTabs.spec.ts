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
                data: ref({ number: 0, totalPages: 3, content: [] }),
                isLoading: ref(false)
            }
        },
        useAdminUserComments: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
            captured.commentsUserId = userId
            captured.commentsParams = params
            return {
                data: ref({ number: 0, totalPages: 3, content: [] }),
                isLoading: ref(false)
            }
        },
        useAdminUserSubscriptions: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
            captured.subscriptionsUserId = userId
            captured.subscriptionsParams = params
            return {
                data: ref({ number: 0, totalPages: 3, content: [] }),
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
})

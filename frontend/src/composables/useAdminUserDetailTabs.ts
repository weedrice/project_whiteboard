import { computed, ref, watch, type Ref } from 'vue'
import { useAdmin } from '@/composables/useAdmin'

export type AdminUserDetailTab = 'posts' | 'comments' | 'subscriptions'

interface UseAdminUserDetailTabsOptions {
    isOpen: Ref<boolean>
    userId: Ref<number | null>
    tabSize?: number
}

export function useAdminUserDetailTabs({
    isOpen,
    userId,
    tabSize = 10
}: UseAdminUserDetailTabsOptions) {
    const { useAdminUserPosts, useAdminUserComments, useAdminUserSubscriptions } = useAdmin()

    const activeTab = ref<AdminUserDetailTab>('posts')
    const postsPage = ref(0)
    const commentsPage = ref(0)
    const subscriptionsPage = ref(0)

    const openedUserId = computed<number | null>(() => (isOpen.value ? userId.value : null))
    const activePostsUserId = computed<number | null>(() => (
        activeTab.value === 'posts' ? openedUserId.value : null
    ))
    const activeCommentsUserId = computed<number | null>(() => (
        activeTab.value === 'comments' ? openedUserId.value : null
    ))
    const activeSubscriptionsUserId = computed<number | null>(() => (
        activeTab.value === 'subscriptions' ? openedUserId.value : null
    ))

    const postsParams = computed(() => ({ page: postsPage.value, size: tabSize }))
    const commentsParams = computed(() => ({ page: commentsPage.value, size: tabSize }))
    const subscriptionsParams = computed(() => ({ page: subscriptionsPage.value, size: tabSize }))

    const { data: userPosts, isLoading: isPostsLoading } = useAdminUserPosts(activePostsUserId, postsParams)
    const { data: userComments, isLoading: isCommentsLoading } = useAdminUserComments(activeCommentsUserId, commentsParams)
    const { data: userSubscriptions, isLoading: isSubscriptionsLoading } = useAdminUserSubscriptions(
        activeSubscriptionsUserId,
        subscriptionsParams
    )

    watch(isOpen, (open) => {
        if (!open) return
        activeTab.value = 'posts'
        postsPage.value = 0
        commentsPage.value = 0
        subscriptionsPage.value = 0
    })

    function prevPostsPage() {
        if (!userPosts.value) return
        if (userPosts.value.number > 0) postsPage.value -= 1
    }

    function nextPostsPage() {
        if (!userPosts.value) return
        if (userPosts.value.number + 1 < userPosts.value.totalPages) postsPage.value += 1
    }

    function prevCommentsPage() {
        if (!userComments.value) return
        if (userComments.value.number > 0) commentsPage.value -= 1
    }

    function nextCommentsPage() {
        if (!userComments.value) return
        if (userComments.value.number + 1 < userComments.value.totalPages) commentsPage.value += 1
    }

    function prevSubscriptionsPage() {
        if (!userSubscriptions.value) return
        if (userSubscriptions.value.number > 0) subscriptionsPage.value -= 1
    }

    function nextSubscriptionsPage() {
        if (!userSubscriptions.value) return
        if (userSubscriptions.value.number + 1 < userSubscriptions.value.totalPages) subscriptionsPage.value += 1
    }

    return {
        activeTab,
        isCommentsLoading,
        isPostsLoading,
        isSubscriptionsLoading,
        nextCommentsPage,
        nextPostsPage,
        nextSubscriptionsPage,
        prevCommentsPage,
        prevPostsPage,
        prevSubscriptionsPage,
        userComments,
        userPosts,
        userSubscriptions,
    }
}

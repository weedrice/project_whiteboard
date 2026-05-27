import { computed, ref, watch, type Ref } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { formatDate } from '@/utils/date'
import type { AdminUserCommentItem, AdminUserPostItem, AdminUserSubscriptionItem } from '@/types'

export type AdminUserDetailTab = 'posts' | 'comments' | 'subscriptions'
type AdminUserTabBadgeVariant = 'success' | 'danger' | 'warning' | 'gray'

interface AdminUserTabBadge {
    label: string
    variant: AdminUserTabBadgeVariant
}

export interface AdminUserPostViewItem {
    postId: number
    title: string
    badges: AdminUserTabBadge[]
    metaText: string
    categoryText: string
    statsText: string
}

export interface AdminUserCommentViewItem {
    commentId: number
    content: string
    badges: AdminUserTabBadge[]
    metaText: string
    statsText: string
}

export interface AdminUserSubscriptionViewItem {
    boardId: number
    boardName: string
    badges: AdminUserTabBadge[]
    boardPath: string
    sortOrderText: string
}

interface UseAdminUserDetailTabsOptions {
    isOpen: Ref<boolean>
    userId: Ref<number | null>
    tabSize?: number
}

function getAgentBadgeLabel(item: { authorType: 'USER' | 'AGENT', agentName?: string | null, agentId?: number | null }) {
    if (item.authorType !== 'AGENT') return null
    return `Agent ${item.agentName || item.agentId}`
}

function getSubscriptionStateLabel(reason?: string | null) {
    if (reason === 'INACTIVE') return '비활성'
    if (reason === 'PRIVATE') return '비공개'
    if (reason === 'RESTRICTED') return '접근 제한'
    return '접근 가능'
}

function toPostBadges(post: AdminUserPostItem): AdminUserTabBadge[] {
    const badges: AdminUserTabBadge[] = [{
        label: post.deleted ? '삭제됨' : '노출중',
        variant: post.deleted ? 'danger' : 'gray'
    }]
    const agentBadgeLabel = getAgentBadgeLabel(post)

    if (post.notice) badges.push({ label: '공지', variant: 'warning' })
    if (post.secret) badges.push({ label: '비밀글', variant: 'warning' })
    if (post.nsfw) badges.push({ label: 'NSFW', variant: 'danger' })
    if (post.spoiler) badges.push({ label: '스포일러', variant: 'gray' })
    if (agentBadgeLabel) badges.push({ label: agentBadgeLabel, variant: 'gray' })

    return badges
}

function toCommentBadges(comment: AdminUserCommentItem): AdminUserTabBadge[] {
    const badges: AdminUserTabBadge[] = [{
        label: comment.deleted ? '삭제됨' : '노출중',
        variant: comment.deleted ? 'danger' : 'gray'
    }]
    const agentBadgeLabel = getAgentBadgeLabel(comment)

    if (comment.parentId) badges.push({ label: '답글', variant: 'gray' })
    if (comment.post.deleted) badges.push({ label: '원문 삭제', variant: 'warning' })
    if (!comment.post.boardActive) badges.push({ label: '게시판 비활성', variant: 'warning' })
    if (!comment.post.boardPublic) badges.push({ label: '비공개 게시판', variant: 'gray' })
    if (agentBadgeLabel) badges.push({ label: agentBadgeLabel, variant: 'gray' })

    return badges
}

function toSubscriptionBadges(board: AdminUserSubscriptionItem): AdminUserTabBadge[] {
    return [
        {
            label: getSubscriptionStateLabel(board.inaccessibleReason),
            variant: board.subscriptionAccessible ? 'success' : 'warning'
        },
        {
            label: board.boardActive ? '활성' : '비활성',
            variant: board.boardActive ? 'success' : 'warning'
        },
        {
            label: board.boardPublic ? '공개' : '비공개',
            variant: board.boardPublic ? 'gray' : 'warning'
        },
        {
            label: board.role,
            variant: 'gray'
        }
    ]
}

function toPostViewItem(post: AdminUserPostItem): AdminUserPostViewItem {
    return {
        postId: post.postId,
        title: post.title,
        badges: toPostBadges(post),
        metaText: `${post.boardName} · /${post.boardUrl} · ${formatDate(post.createdAt)}`,
        categoryText: post.categoryName ? `카테고리: ${post.categoryName}` : '',
        statsText: `조회 ${post.viewCount.toLocaleString()} · 추천 ${post.likeCount.toLocaleString()} · 댓글 ${post.commentCount.toLocaleString()}`
    }
}

function toCommentViewItem(comment: AdminUserCommentItem): AdminUserCommentViewItem {
    return {
        commentId: comment.commentId,
        content: comment.content,
        badges: toCommentBadges(comment),
        metaText: `${comment.post.title} · /${comment.post.boardUrl} · ${formatDate(comment.createdAt)}`,
        statsText: `좋아요 ${comment.likeCount.toLocaleString()} · depth ${comment.depth}`
    }
}

function toSubscriptionViewItem(board: AdminUserSubscriptionItem): AdminUserSubscriptionViewItem {
    return {
        boardId: board.boardId,
        boardName: board.boardName,
        badges: toSubscriptionBadges(board),
        boardPath: `/${board.boardUrl}`,
        sortOrderText: `정렬 순서 ${board.sortOrder ?? '-'}`
    }
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
    const postItems = computed(() => userPosts.value?.content.map(toPostViewItem) ?? [])
    const commentItems = computed(() => userComments.value?.content.map(toCommentViewItem) ?? [])
    const subscriptionItems = computed(() => userSubscriptions.value?.content.map(toSubscriptionViewItem) ?? [])

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
        commentItems,
        commentsPage,
        isCommentsLoading,
        isPostsLoading,
        isSubscriptionsLoading,
        nextCommentsPage,
        nextPostsPage,
        postItems,
        postsPage,
        nextSubscriptionsPage,
        prevCommentsPage,
        prevPostsPage,
        prevSubscriptionsPage,
        subscriptionItems,
        subscriptionsPage,
        userComments,
        userPosts,
        userSubscriptions,
    }
}

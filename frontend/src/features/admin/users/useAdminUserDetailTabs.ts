import { computed, ref, watch, type Ref } from 'vue'
import { useAdmin } from '@/features/admin/useAdmin'
import { formatDate } from '@/utils/date'
import { formatInteger } from '@/utils/numberFormat'
import type { AdminUserCommentItem, AdminUserPostItem, AdminUserSubscriptionItem, SanctionHistoryItem } from '@/types'

export type AdminUserDetailTab = 'posts' | 'comments' | 'subscriptions' | 'sanctions'
type AdminUserTabBadgeVariant = 'success' | 'danger' | 'warning' | 'gray'
type Translate = (key: string, params?: Record<string, unknown>) => string

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

export interface AdminUserSanctionViewItem {
    sanctionId: number
    typeLabel: string
    typeVariant: AdminUserTabBadgeVariant
    remark: string
    periodText: string
    processorText: string
    contentText: string
}

interface UseAdminUserDetailTabsOptions {
    isOpen: Ref<boolean>
    userId: Ref<number | null>
    tabSize?: number
    t: Translate
}

interface PageNavigationState {
    number: number
    totalPages: number
}

function movePage(
    page: Ref<number>,
    pageData: Ref<PageNavigationState | null | undefined>,
    direction: -1 | 1
) {
    const currentPage = pageData.value
    if (!currentPage) return

    if (direction < 0 && currentPage.number > 0) {
        page.value -= 1
    }

    if (direction > 0 && currentPage.number + 1 < currentPage.totalPages) {
        page.value += 1
    }
}

function getAgentBadgeLabel(item: { authorType: 'USER' | 'AGENT', agentName?: string | null, agentId?: number | null }) {
    if (item.authorType !== 'AGENT') return null
    return `Agent ${item.agentName || item.agentId}`
}

function getSubscriptionStateLabel(t: Translate, reason?: string | null) {
    if (reason === 'INACTIVE') return t('admin.users.detailTabs.subscriptionInactive')
    if (reason === 'PRIVATE') return t('admin.users.detailTabs.subscriptionPrivate')
    if (reason === 'RESTRICTED') return t('admin.users.detailTabs.subscriptionRestricted')
    return t('admin.users.detailTabs.subscriptionAccessible')
}

function toPostBadges(post: AdminUserPostItem, t: Translate): AdminUserTabBadge[] {
    const badges: AdminUserTabBadge[] = [{
        label: post.deleted ? t('common.deleted') : t('admin.users.detailTabs.visible'),
        variant: post.deleted ? 'danger' : 'gray'
    }]
    const agentBadgeLabel = getAgentBadgeLabel(post)

    if (post.notice) badges.push({ label: t('admin.users.detailTabs.notice'), variant: 'warning' })
    if (post.secret) badges.push({ label: t('admin.users.detailTabs.secret'), variant: 'warning' })
    if (post.nsfw) badges.push({ label: 'NSFW', variant: 'danger' })
    if (post.spoiler) badges.push({ label: t('admin.users.detailTabs.spoiler'), variant: 'gray' })
    if (agentBadgeLabel) badges.push({ label: agentBadgeLabel, variant: 'gray' })

    return badges
}

function toCommentBadges(comment: AdminUserCommentItem, t: Translate): AdminUserTabBadge[] {
    const badges: AdminUserTabBadge[] = [{
        label: comment.deleted ? t('common.deleted') : t('admin.users.detailTabs.visible'),
        variant: comment.deleted ? 'danger' : 'gray'
    }]
    const agentBadgeLabel = getAgentBadgeLabel(comment)

    if (comment.parentId) badges.push({ label: t('admin.users.detailTabs.reply'), variant: 'gray' })
    if (comment.post.deleted) badges.push({ label: t('admin.users.detailTabs.sourceDeleted'), variant: 'warning' })
    if (!comment.post.boardActive) badges.push({ label: t('admin.users.detailTabs.boardInactive'), variant: 'warning' })
    if (!comment.post.boardPublic) badges.push({ label: t('admin.users.detailTabs.boardPrivate'), variant: 'gray' })
    if (agentBadgeLabel) badges.push({ label: agentBadgeLabel, variant: 'gray' })

    return badges
}

function toSubscriptionBadges(board: AdminUserSubscriptionItem, t: Translate): AdminUserTabBadge[] {
    return [
        {
            label: getSubscriptionStateLabel(t, board.inaccessibleReason),
            variant: board.subscriptionAccessible ? 'success' : 'warning'
        },
        {
            label: board.boardActive ? t('admin.users.detailTabs.boardActive') : t('admin.users.detailTabs.boardInactiveShort'),
            variant: board.boardActive ? 'success' : 'warning'
        },
        {
            label: board.boardPublic ? t('admin.users.detailTabs.boardPublic') : t('admin.users.detailTabs.boardPrivateShort'),
            variant: board.boardPublic ? 'gray' : 'warning'
        },
        {
            label: board.role,
            variant: 'gray'
        }
    ]
}

function toPostViewItem(post: AdminUserPostItem, t: Translate): AdminUserPostViewItem {
    return {
        postId: post.postId,
        title: post.title,
        badges: toPostBadges(post, t),
        metaText: `${post.boardName} · /${post.boardUrl} · ${formatDate(post.createdAt)}`,
        categoryText: post.categoryName ? t('admin.users.detailTabs.category', { name: post.categoryName }) : '',
        statsText: t('admin.users.detailTabs.postStats', {
            views: formatInteger(post.viewCount),
            likes: formatInteger(post.likeCount),
            comments: formatInteger(post.commentCount)
        })
    }
}

function toCommentViewItem(comment: AdminUserCommentItem, t: Translate): AdminUserCommentViewItem {
    return {
        commentId: comment.commentId,
        content: comment.content,
        badges: toCommentBadges(comment, t),
        metaText: `${comment.post.title} · /${comment.post.boardUrl} · ${formatDate(comment.createdAt)}`,
        statsText: t('admin.users.detailTabs.commentStats', { likes: formatInteger(comment.likeCount), depth: comment.depth })
    }
}

function toSubscriptionViewItem(board: AdminUserSubscriptionItem, t: Translate): AdminUserSubscriptionViewItem {
    return {
        boardId: board.boardId,
        boardName: board.boardName,
        badges: toSubscriptionBadges(board, t),
        boardPath: `/${board.boardUrl}`,
        sortOrderText: t('admin.users.detailTabs.sortOrder', { order: board.sortOrder ?? '-' })
    }
}

function toSanctionViewItem(sanction: SanctionHistoryItem, t: Translate): AdminUserSanctionViewItem {
    const typeVariant: AdminUserTabBadgeVariant = sanction.type === 'BAN'
        ? 'danger'
        : sanction.type === 'MUTE' ? 'warning' : 'gray'
    const end = sanction.endDate ? formatDate(sanction.endDate) : t('admin.sanction.permanent')

    return {
        sanctionId: sanction.sanctionId,
        typeLabel: getSanctionTypeLabel(sanction.type, t),
        typeVariant,
        remark: sanction.remark || '-',
        periodText: t('admin.sanction.historyPeriod', {
            start: formatDate(sanction.startDate),
            end,
        }),
        processorText: sanction.adminId == null
            ? '-'
            : t('admin.sanction.historyProcessor', { id: sanction.adminId }),
        contentText: sanction.contentId == null || !sanction.contentType
            ? ''
            : t('admin.sanction.relatedContent', { type: sanction.contentType, id: sanction.contentId }),
    }
}

function getSanctionTypeLabel(type: SanctionHistoryItem['type'], t: Translate) {
    if (type === 'WARNING') return t('admin.sanction.types.WARNING')
    if (type === 'MUTE') return t('admin.sanction.types.MUTE')
    return t('admin.sanction.types.BAN')
}

export function useAdminUserDetailTabs({
    isOpen,
    userId,
    tabSize = 10,
    t
}: UseAdminUserDetailTabsOptions) {
    const { useAdminUserPosts, useAdminUserComments, useAdminUserSubscriptions, useAdminUserSanctions } = useAdmin()

    const activeTab = ref<AdminUserDetailTab>('posts')
    const postsPage = ref(0)
    const commentsPage = ref(0)
    const subscriptionsPage = ref(0)
    const sanctionsPage = ref(0)

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
    const activeSanctionsUserId = computed<number | null>(() => (
        activeTab.value === 'sanctions' ? openedUserId.value : null
    ))

    const postsParams = computed(() => ({ page: postsPage.value, size: tabSize }))
    const commentsParams = computed(() => ({ page: commentsPage.value, size: tabSize }))
    const subscriptionsParams = computed(() => ({ page: subscriptionsPage.value, size: tabSize }))
    const sanctionsParams = computed(() => ({ page: sanctionsPage.value, size: tabSize }))

    const { data: userPosts, isLoading: isPostsLoading, isError: isPostsError, refetch: refetchPosts } = useAdminUserPosts(activePostsUserId, postsParams)
    const { data: userComments, isLoading: isCommentsLoading, isError: isCommentsError, refetch: refetchComments } = useAdminUserComments(activeCommentsUserId, commentsParams)
    const { data: userSubscriptions, isLoading: isSubscriptionsLoading, isError: isSubscriptionsError, refetch: refetchSubscriptions } = useAdminUserSubscriptions(
        activeSubscriptionsUserId,
        subscriptionsParams
    )
    const { data: userSanctions, isLoading: isSanctionsLoading, isError: isSanctionsError, refetch: refetchSanctions } = useAdminUserSanctions(
        activeSanctionsUserId,
        sanctionsParams
    )
    const postItems = computed(() => userPosts.value?.content.map((post) => toPostViewItem(post, t)) ?? [])
    const commentItems = computed(() => userComments.value?.content.map((comment) => toCommentViewItem(comment, t)) ?? [])
    const subscriptionItems = computed(() => userSubscriptions.value?.content.map((board) => toSubscriptionViewItem(board, t)) ?? [])
    const sanctionItems = computed(() => userSanctions.value?.content.map((sanction) => toSanctionViewItem(sanction, t)) ?? [])

    watch(isOpen, (open) => {
        if (!open) return
        activeTab.value = 'posts'
        postsPage.value = 0
        commentsPage.value = 0
        subscriptionsPage.value = 0
        sanctionsPage.value = 0
    })

    function prevPostsPage() {
        movePage(postsPage, userPosts, -1)
    }

    function nextPostsPage() {
        movePage(postsPage, userPosts, 1)
    }

    function prevCommentsPage() {
        movePage(commentsPage, userComments, -1)
    }

    function nextCommentsPage() {
        movePage(commentsPage, userComments, 1)
    }

    function prevSubscriptionsPage() {
        movePage(subscriptionsPage, userSubscriptions, -1)
    }

    function nextSubscriptionsPage() {
        movePage(subscriptionsPage, userSubscriptions, 1)
    }

    function prevSanctionsPage() {
        movePage(sanctionsPage, userSanctions, -1)
    }

    function nextSanctionsPage() {
        movePage(sanctionsPage, userSanctions, 1)
    }

    return {
        activeTab,
        commentItems,
        commentsPage,
        isCommentsLoading,
        isCommentsError,
        isPostsLoading,
        isPostsError,
        isSubscriptionsLoading,
        isSubscriptionsError,
        isSanctionsLoading,
        isSanctionsError,
        nextCommentsPage,
        nextPostsPage,
        postItems,
        postsPage,
        nextSubscriptionsPage,
        nextSanctionsPage,
        prevCommentsPage,
        prevPostsPage,
        prevSubscriptionsPage,
        prevSanctionsPage,
        refetchComments,
        refetchPosts,
        refetchSubscriptions,
        refetchSanctions,
        subscriptionItems,
        subscriptionsPage,
        sanctionItems,
        sanctionsPage,
        userComments,
        userPosts,
        userSubscriptions,
        userSanctions,
    }
}

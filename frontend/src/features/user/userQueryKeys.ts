/** Shared query-key factory for the user feature and its compatibility consumers. */

const normalizePublicUserId = (userId: string | number) => String(userId)

export interface UserQueryPaginationParams {
    page?: number
    size?: number
    sort?: string
    [key: string]: unknown
}

export const userQueryKeys = {
    me: ['user', 'me'] as const,
    profile: (userId: string | number) => ['user', normalizePublicUserId(userId)] as const,
    settings: ['user', 'settings'] as const,
    blocksRoot: ['user', 'blocks'] as const,
    blocks: (params: Readonly<UserQueryPaginationParams> = {}) => ['user', 'blocks', { ...params }] as const,
    notificationSettings: ['user', 'notification-settings'] as const,
    keywordSubscriptions: ['user', 'keyword-subscriptions'] as const,
    agents: ['user', 'agents'] as const,
    sessions: ['user', 'sessions'] as const,
    loginHistory: (params: Readonly<UserQueryPaginationParams> = {}) =>
        ['user', 'login-history', { ...params }] as const,
    pointsRoot: ['user', 'points'] as const,
    draftsRoot: ['user', 'drafts'] as const,
    scheduledPostsRoot: ['user', 'scheduled-posts'] as const,
    badgesRoot: ['user', 'badges'] as const,
    scrapFolders: ['user', 'scrap-folders'] as const,
    postSeries: ['user', 'post-series'] as const,
    myPoints: (userIdentity?: string | number | null) =>
        ['user', 'points', 'me', userIdentity ?? 'anonymous'] as const,
    myPosts: (params: Readonly<UserQueryPaginationParams>) => ['user', 'me', 'posts', { ...params }] as const,
    myComments: (params: Readonly<UserQueryPaginationParams>) => ['user', 'me', 'comments', { ...params }] as const,
    publicPosts: (userId: string | number, params: Readonly<UserQueryPaginationParams> = {}) =>
        ['user', normalizePublicUserId(userId), 'posts', { ...params }] as const,
    publicPostsRoot: (userId: string | number) => ['user', normalizePublicUserId(userId), 'posts'] as const,
    publicComments: (userId: string | number, params: Readonly<UserQueryPaginationParams> = {}) =>
        ['user', normalizePublicUserId(userId), 'comments', { ...params }] as const,
    publicCommentsRoot: (userId: string | number) => ['user', normalizePublicUserId(userId), 'comments'] as const,
    drafts: (params: Readonly<UserQueryPaginationParams> = {}) => ['user', 'drafts', { ...params }] as const,
    scheduledPosts: (params: Readonly<UserQueryPaginationParams> = {}) => ['user', 'scheduled-posts', { ...params }] as const,
    badges: (userId: string | number) => ['user', 'badges', userId] as const,
    scraps: (params: Readonly<UserQueryPaginationParams> = {}) => ['user', 'scraps', { ...params }] as const,
    pointHistories: (params: Readonly<UserQueryPaginationParams> = {}) =>
        ['user', 'points', 'history', { ...params }] as const,
    recentlyViewedPosts: (params?: Readonly<UserQueryPaginationParams>) =>
        ['user', 'history', 'views', params ? { ...params } : undefined] as const,
}

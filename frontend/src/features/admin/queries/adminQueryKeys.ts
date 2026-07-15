function snapshotParams<TParams extends object>(params: TParams): TParams {
    return { ...params }
}

export const adminQueryKeys = {
    adminsRoot: ['admin', 'admins'] as const,
    admins: <TParams extends object>(params: TParams) => ['admin', 'admins', snapshotParams(params)] as const,
    superAdmins: ['admin', 'super'] as const,
    usersRoot: ['admin', 'users'] as const,
    users: <TParams extends object>(params: TParams) => ['admin', 'users', snapshotParams(params)] as const,
    userDetailRoot: ['admin', 'users', 'detail'] as const,
    userDetail: (userId: number | null) => ['admin', 'users', 'detail', userId] as const,
    userPosts: <TParams extends object>(userId: number | null, params: TParams) =>
        ['admin', 'users', 'detail', userId, 'posts', snapshotParams(params)] as const,
    userComments: <TParams extends object>(userId: number | null, params: TParams) =>
        ['admin', 'users', 'detail', userId, 'comments', snapshotParams(params)] as const,
    userSubscriptions: <TParams extends object>(userId: number | null, params: TParams) =>
        ['admin', 'users', 'detail', userId, 'subscriptions', snapshotParams(params)] as const,
    reportsRoot: ['admin', 'reports'] as const,
    reports: <TParams extends object>(params: TParams) => ['admin', 'reports', snapshotParams(params)] as const,
    moderationAuditsRoot: ['admin', 'moderation-audits'] as const,
    moderationAudits: <TParams extends object>(params: TParams) =>
        ['admin', 'moderation-audits', snapshotParams(params)] as const,
    ipBlocksRoot: ['admin', 'ip-blocks'] as const,
    ipBlocks: <TParams extends object>(params: TParams) => ['admin', 'ip-blocks', snapshotParams(params)] as const,
    configs: ['admin', 'configs'] as const,
    stats: ['admin', 'stats'] as const,
    deepStats: (days: 30 | 90) => ['admin', 'stats', 'deep', days] as const,
    boards: ['admin', 'boards'] as const,
    boardManager: (boardId: number | null) => ['admin', 'board-manager', boardId] as const,
    boardManagerById: (boardId: number) => ['admin', 'board-manager', boardId] as const,
    errorLogsRoot: ['admin', 'error-logs'] as const,
    errorLogs: <TParams extends object>(params: TParams) => ['admin', 'error-logs', snapshotParams(params)] as const,
    errorLogDetailRoot: ['admin', 'error-logs', 'detail'] as const,
    errorLogDetail: (errorLogId: number | null) => ['admin', 'error-logs', 'detail', errorLogId] as const,
    errorLogDetailById: (errorLogId: number) => ['admin', 'error-logs', 'detail', errorLogId] as const,
    errorLogStats: ['admin', 'error-log-stats'] as const,
}

export const adminInquiryQueryKeys = {
    list: ['admin', 'inquiry-posts'] as const,
    listPage: (page: number, size: number, sort: string) =>
        ['admin', 'inquiry-posts', page, size, sort] as const,
    detail: (selectedPostId: number | null) =>
        ['admin', 'inquiry-post-detail', selectedPostId] as const,
}

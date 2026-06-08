import { computed, type Ref } from 'vue'

export const adminQueryKeys = {
    adminsRoot: ['admin', 'admins'] as const,
    admins: (params: Ref<unknown>) => ['admin', 'admins', params] as const,
    superAdmins: ['admin', 'super'] as const,
    usersRoot: ['admin', 'users'] as const,
    users: (params: Ref<unknown>) => ['admin', 'users', params] as const,
    userDetailRoot: ['admin', 'users', 'detail'] as const,
    userDetail: (userId: Ref<number | null>) => ['admin', 'users', 'detail', userId] as const,
    userPosts: (userId: Ref<number | null>, params: Ref<unknown>) =>
        ['admin', 'users', 'detail', userId, 'posts', params] as const,
    userComments: (userId: Ref<number | null>, params: Ref<unknown>) =>
        ['admin', 'users', 'detail', userId, 'comments', params] as const,
    userSubscriptions: (userId: Ref<number | null>, params: Ref<unknown>) =>
        ['admin', 'users', 'detail', userId, 'subscriptions', params] as const,
    reportsRoot: ['admin', 'reports'] as const,
    reports: (params: Ref<unknown>) => ['admin', 'reports', params] as const,
    ipBlocksRoot: ['admin', 'ip-blocks'] as const,
    ipBlocks: (params: Ref<unknown>) => ['admin', 'ip-blocks', params] as const,
    configs: ['admin', 'configs'] as const,
    stats: ['admin', 'stats'] as const,
    boards: ['admin', 'boards'] as const,
    boardManager: (boardId: Ref<number | null>) => computed(() => ['admin', 'board-manager', boardId.value] as const),
    boardManagerById: (boardId: number) => ['admin', 'board-manager', boardId] as const,
    errorLogsRoot: ['admin', 'error-logs'] as const,
    errorLogs: (params: Ref<unknown>) => ['admin', 'error-logs', params] as const,
    errorLogStats: ['admin', 'error-log-stats'] as const,
}

export const adminInquiryQueryKeys = {
    list: ['admin', 'inquiry-posts'] as const,
    listPage: (page: Ref<number>, size: Ref<number>, sort: Ref<string>) =>
        ['admin', 'inquiry-posts', page, size, sort] as const,
    detail: (selectedPostId: Ref<number | null>) =>
        ['admin', 'inquiry-post-detail', selectedPostId] as const,
}

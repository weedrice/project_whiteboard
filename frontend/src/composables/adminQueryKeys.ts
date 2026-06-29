import { computed, type Ref } from 'vue'

export const adminQueryKeys = {
    adminsRoot: ['admin', 'admins'] as const,
    admins: (params: Ref<unknown>) => computed(() => ['admin', 'admins', params.value ?? {}] as const),
    superAdmins: ['admin', 'super'] as const,
    usersRoot: ['admin', 'users'] as const,
    users: (params: Ref<unknown>) => computed(() => ['admin', 'users', params.value ?? {}] as const),
    userDetailRoot: ['admin', 'users', 'detail'] as const,
    userDetail: (userId: Ref<number | null>) => computed(() => ['admin', 'users', 'detail', userId.value] as const),
    userPosts: (userId: Ref<number | null>, params: Ref<unknown>) =>
        computed(() => ['admin', 'users', 'detail', userId.value, 'posts', params.value ?? {}] as const),
    userComments: (userId: Ref<number | null>, params: Ref<unknown>) =>
        computed(() => ['admin', 'users', 'detail', userId.value, 'comments', params.value ?? {}] as const),
    userSubscriptions: (userId: Ref<number | null>, params: Ref<unknown>) =>
        computed(() => ['admin', 'users', 'detail', userId.value, 'subscriptions', params.value ?? {}] as const),
    reportsRoot: ['admin', 'reports'] as const,
    reports: (params: Ref<unknown>) => computed(() => ['admin', 'reports', params.value ?? {}] as const),
    ipBlocksRoot: ['admin', 'ip-blocks'] as const,
    ipBlocks: (params: Ref<unknown>) => computed(() => ['admin', 'ip-blocks', params.value ?? {}] as const),
    configs: ['admin', 'configs'] as const,
    stats: ['admin', 'stats'] as const,
    boards: ['admin', 'boards'] as const,
    boardManager: (boardId: Ref<number | null>) => computed(() => ['admin', 'board-manager', boardId.value] as const),
    boardManagerById: (boardId: number) => ['admin', 'board-manager', boardId] as const,
    errorLogsRoot: ['admin', 'error-logs'] as const,
    errorLogs: (params: Ref<unknown>) => computed(() => ['admin', 'error-logs', params.value ?? {}] as const),
    errorLogStats: ['admin', 'error-log-stats'] as const,
}

export const adminInquiryQueryKeys = {
    list: ['admin', 'inquiry-posts'] as const,
    listPage: (page: Ref<number>, size: Ref<number>, sort: Ref<string>) =>
        computed(() => ['admin', 'inquiry-posts', page.value, size.value, sort.value] as const),
    detail: (selectedPostId: Ref<number | null>) =>
        computed(() => ['admin', 'inquiry-post-detail', selectedPostId.value] as const),
}

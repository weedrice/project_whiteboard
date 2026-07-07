import { computed, type Ref } from 'vue'

export interface UserQueryPaginationParams {
    page?: number
    size?: number
    sort?: string
    [key: string]: unknown
}

export const userQueryKeys = {
    me: ['user', 'me'] as const,
    profile: (userId: Ref<string | number>) => ['user', userId] as const,
    settings: ['user', 'settings'] as const,
    blocksRoot: ['user', 'blocks'] as const,
    blocks: (params?: Ref<UserQueryPaginationParams>) => computed(() => ['user', 'blocks', params?.value ?? {}] as const),
    notificationSettings: ['user', 'notification-settings'] as const,
    agents: ['user', 'agents'] as const,
    pointsRoot: ['user', 'points'] as const,
    draftsRoot: ['user', 'drafts'] as const,
    myPoints: (userIdentity?: Ref<string | number | null | undefined>) =>
        computed(() => ['user', 'points', 'me', userIdentity?.value ?? 'anonymous'] as const),
    myPosts: (params: UserQueryPaginationParams) => ['user', 'me', 'posts', params] as const,
    myComments: (params: UserQueryPaginationParams) => ['user', 'me', 'comments', params] as const,
    drafts: (params?: Ref<UserQueryPaginationParams>) => computed(() => ['user', 'drafts', params?.value ?? {}] as const),
    scraps: (params?: Ref<UserQueryPaginationParams>) => computed(() => ['user', 'scraps', params?.value ?? {}] as const),
    pointHistories: (params?: Ref<UserQueryPaginationParams>) =>
        computed(() => ['user', 'points', 'history', params?.value ?? {}] as const),
    recentlyViewedPosts: (params?: Ref<UserQueryPaginationParams>) => ['user', 'history', 'views', params] as const,
}

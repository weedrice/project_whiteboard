import type { Ref } from 'vue'

export const boardQueryKeys = {
    all: ['boards'] as const,
    subscriptions: ['boards', 'subscriptions'] as const,
    subscriptionsBySize: (size: number) => ['boards', 'subscriptions', size] as const,
    detailRoot: ['board', 'detail'] as const,
    detail: (boardUrl: string | Ref<string>) => ['board', 'detail', boardUrl] as const,
    postsRoot: ['board', 'posts'] as const,
    posts: <TParams>(
        boardUrl: Ref<string>,
        params: Ref<TParams>,
        isSearching?: Ref<boolean>,
    ) => ['board', 'posts', boardUrl, params, isSearching] as const,
    notices: (boardUrl: Ref<string>) => ['board', 'notices', boardUrl] as const,
    categories: (boardUrl: Ref<string>) => ['board', 'categories', boardUrl] as const,
    managerCandidates: <TParams>(boardUrl: Ref<string>, params: Ref<TParams>) =>
        ['board', 'manager-candidates', boardUrl, params] as const,
}

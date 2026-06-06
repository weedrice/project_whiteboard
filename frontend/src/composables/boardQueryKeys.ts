import type { Ref } from 'vue'

export const boardQueryKeys = {
    all: ['boards'] as const,
    subscriptions: ['boards', 'subscriptions'] as const,
    subscriptionsBySize: (size: number) => ['boards', 'subscriptions', size] as const,
    detail: (boardUrl: string | Ref<string>) => ['board', boardUrl] as const,
    posts: <TParams>(
        boardUrl: Ref<string>,
        params: Ref<TParams>,
        isSearching?: Ref<boolean>,
    ) => ['board', boardUrl, 'posts', params, isSearching] as const,
    notices: (boardUrl: Ref<string>) => ['board', boardUrl, 'notices'] as const,
    categories: (boardUrl: Ref<string>) => ['board', boardUrl, 'categories'] as const,
    managerCandidates: <TParams>(boardUrl: Ref<string>, params: Ref<TParams>) =>
        ['board', boardUrl, 'manager-candidates', params] as const,
}

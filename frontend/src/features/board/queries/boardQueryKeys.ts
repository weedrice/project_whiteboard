export const boardQueryKeys = {
    all: ['boards'] as const,
    subscriptions: ['boards', 'subscriptions'] as const,
    subscriptionsBySize: (size: number) => ['boards', 'subscriptions', size] as const,
    detailRoot: ['board', 'detail'] as const,
    detail: (boardUrl: string) => ['board', 'detail', boardUrl] as const,
    postsRoot: ['board', 'posts'] as const,
    postsByBoard: (boardUrl: string) => ['board', 'posts', boardUrl] as const,
    infinitePostsByBoard: (boardUrl: string) => ['board', 'posts', 'infinite', boardUrl] as const,
    posts: <TParams extends object>(
        boardUrl: string,
        params: Readonly<TParams>,
        isSearching?: boolean,
    ) => ['board', 'posts', boardUrl, { ...params }, isSearching] as const,
    infinitePosts: <TParams extends object>(
        boardUrl: string,
        params: Readonly<TParams>,
        isSearching?: boolean,
    ) => ['board', 'posts', 'infinite', boardUrl, { ...params }, isSearching] as const,
    notices: (boardUrl: string) => ['board', 'notices', boardUrl] as const,
    categories: (boardUrl: string) => ['board', 'categories', boardUrl] as const,
    managerCandidatesRoot: (boardUrl: string) => ['board', 'manager-candidates', boardUrl] as const,
    managerCandidates: <TParams extends object>(boardUrl: string, params: Readonly<TParams>) =>
        ['board', 'manager-candidates', boardUrl, { ...params }] as const,
    managerReportsRoot: (boardUrl: string) => ['board', 'manager-reports', boardUrl] as const,
    managerReports: <TParams extends object>(boardUrl: string, params: Readonly<TParams>) =>
        ['board', 'manager-reports', boardUrl, { ...params }] as const,
    managerAuditsRoot: (boardUrl: string) => ['board', 'manager-audits', boardUrl] as const,
    managerAudits: <TParams extends object>(boardUrl: string, params: Readonly<TParams>) =>
        ['board', 'manager-audits', boardUrl, { ...params }] as const,
}

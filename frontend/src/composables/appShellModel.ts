import { BOARD_DETAIL_SEARCH_INPUT_ID } from '@/features/board/detail/useBoardDetailNavigation'

type AppRouteLike = {
    name?: unknown
    meta: {
        requiresAuth?: unknown
        guestOnly?: unknown
        roles?: unknown
    }
}

export const noIndexRouteNames = new Set([
    'login',
    'signup',
    'find-account',
    'forgot-password',
    'reset-password',
    'oauth-callback',
    'mypage',
    'user-settings',
    'point-history',
    'MyScraps',
    'MyMessages',
    'MyNotifications',
    'MyReports',
    'BlockList',
    'RecentViewed',
    'SubscribedBoards',
    'board-create',
    'board-edit',
    'post-write',
    'post-edit',
    'AdminDashboard',
    'UserManagement',
    'BoardManagement',
    'AdminManagement',
    'ReportManagement',
    'SecuritySettings',
    'GlobalSettings',
    'ErrorLogManagement',
    'error',
])

export const boardSearchRouteNames = new Set(['board-detail', 'post-detail'])

export function shouldNoIndexAppRoute(route: AppRouteLike) {
    if (route.meta.requiresAuth || route.meta.guestOnly || route.meta.roles) {
        return true
    }

    return Boolean(route.name && noIndexRouteNames.has(String(route.name)))
}

export function getAppSearchInput(routeName: unknown, doc: Document = document): HTMLInputElement | null {
    if (routeName && boardSearchRouteNames.has(String(routeName))) {
        const boardSearchInput = doc.getElementById(BOARD_DETAIL_SEARCH_INPUT_ID) as HTMLInputElement | null
        if (boardSearchInput) {
            return boardSearchInput
        }
    }

    const globalSearchInput = doc.getElementById('global-search-input') as HTMLInputElement | null
    if (globalSearchInput) {
        return globalSearchInput
    }

    return null
}

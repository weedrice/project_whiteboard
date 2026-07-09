import axios from 'axios'

const ROUTE_FETCH_ERROR_STATUSES = new Set([403, 404, 500])

export function getStringRouteParam(param: unknown): string {
    return typeof param === 'string' ? param : ''
}

export function getRouteFetchErrorStatus(error: unknown): string {
    if (!axios.isAxiosError(error)) {
        return '500'
    }

    const status = error.response?.status
    return status && ROUTE_FETCH_ERROR_STATUSES.has(status) ? String(status) : '500'
}

export function isReservedBoardUrl(boardUrl: string): boolean {
    return boardUrl.toLowerCase() === 'inquiry'
}

export function shouldRedirectToOnboarding(
    routeName: unknown,
    skipOnboarding: boolean | undefined,
    onboardingCompletedAt: string | null | undefined,
): boolean {
    return routeName !== 'onboarding'
        && !skipOnboarding
        && !onboardingCompletedAt
}

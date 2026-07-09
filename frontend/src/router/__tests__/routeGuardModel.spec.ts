import { describe, expect, it } from 'vitest'
import { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import {
    getRouteFetchErrorStatus,
    getStringRouteParam,
    isReservedBoardUrl,
    shouldRedirectToOnboarding,
} from '@/router/routeGuardModel'

function axiosStatusError(status: number) {
    return new AxiosError('failed', undefined, undefined, undefined, {
        status,
        statusText: String(status),
        headers: {},
        config: { headers: {} } as InternalAxiosRequestConfig,
        data: null,
    })
}

describe('routeGuardModel', () => {
    it('normalizes route params to a single string value', () => {
        expect(getStringRouteParam('free')).toBe('free')
        expect(getStringRouteParam(['free'])).toBe('')
        expect(getStringRouteParam(undefined)).toBe('')
        expect(getStringRouteParam(10)).toBe('')
    })

    it('maps known fetch errors to route error statuses', () => {
        expect(getRouteFetchErrorStatus(axiosStatusError(403))).toBe('403')
        expect(getRouteFetchErrorStatus(axiosStatusError(404))).toBe('404')
        expect(getRouteFetchErrorStatus(axiosStatusError(500))).toBe('500')
    })

    it('falls back to 500 for unknown errors and unsupported statuses', () => {
        expect(getRouteFetchErrorStatus(axiosStatusError(401))).toBe('500')
        expect(getRouteFetchErrorStatus(new Error('boom'))).toBe('500')
        expect(getRouteFetchErrorStatus(null)).toBe('500')
    })

    it('detects reserved board urls case-insensitively', () => {
        expect(isReservedBoardUrl('inquiry')).toBe(true)
        expect(isReservedBoardUrl('Inquiry')).toBe(true)
        expect(isReservedBoardUrl('free')).toBe(false)
        expect(isReservedBoardUrl('')).toBe(false)
    })

    it('detects routes that should enter onboarding', () => {
        expect(shouldRedirectToOnboarding('mypage', false, null)).toBe(true)
        expect(shouldRedirectToOnboarding('onboarding', false, null)).toBe(false)
        expect(shouldRedirectToOnboarding('mypage', true, null)).toBe(false)
        expect(shouldRedirectToOnboarding('mypage', false, '2026-07-09T00:00:00')).toBe(false)
    })
})

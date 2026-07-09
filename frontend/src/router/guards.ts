import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { userSettingsQueryKey } from '@/composables/useUser'
import { queryClient } from '@/queryClient'
import {
    ensureHydratedAuth,
    guardBoardAccess,
    guardEmoticonOwner,
    guardPostAuthor,
} from '@/router/resourceAccessGuards'
import { getStringRouteParam, isReservedBoardUrl, shouldRedirectToOnboarding } from '@/router/routeGuardModel'
import { saveLoginRedirect } from '@/utils/authRedirect'
import { QUERY_STALE_TIME } from '@/utils/constants'

declare module 'vue-router' {
    interface RouteMeta {
        requiresAuth?: boolean
        guestOnly?: boolean
        roles?: string[]
        layout?: string
        requiresWritableBoard?: boolean
        requiresBoardAdmin?: boolean
        requiresEmoticonOwner?: boolean
        requiresPostAuthor?: boolean
        skipOnboarding?: boolean
    }
}

export function createAppNavigationGuard() {
    return async (to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
        const authStore = useAuthStore()

        if (!await ensureHydratedAuth(to, next)) {
            return
        }

        if (authStore.user?.status === 'SANCTIONED') {
            await authStore.handleSanctionedSession()
            next({ name: 'login' })
            return
        }

        const boardUrlParam = getStringRouteParam(to.params.boardUrl)
        if (isReservedBoardUrl(boardUrlParam)) {
            next({ name: 'error', query: { status: '404' } })
            return
        }

        if (to.meta.guestOnly && to.name !== 'oauth-callback' && !authStore.isAuthenticated) {
            if (from.name && !from.meta.guestOnly) {
                saveLoginRedirect(from.fullPath)
            }
        }

        if (to.meta.requiresAuth && !authStore.isAuthenticated) {
            next({ name: 'login', query: { redirect: to.fullPath } })
            return
        }
        if (to.meta.roles && !to.meta.roles.includes(authStore.user?.role || '')) {
            next({ name: 'home' })
            return
        }
        if (to.meta.guestOnly && authStore.isAuthenticated && authStore.user && to.name !== 'oauth-callback') {
            next({ name: 'home' })
            return
        }

        if (authStore.isAuthenticated) {
            try {
                const settings = await queryClient.fetchQuery({
                    queryKey: userSettingsQueryKey,
                    queryFn: async () => unwrapAxiosApiData(await userApi.getUserSettings()),
                    staleTime: QUERY_STALE_TIME.MEDIUM,
                })
                if (shouldRedirectToOnboarding(to.name, to.meta.skipOnboarding, settings?.onboardingCompletedAt)) {
                    next({ name: 'onboarding', query: { redirect: to.fullPath } })
                    return
                }
            } catch {
                // Settings failures should not block route entry.
            }
        }

        if (!await guardEmoticonOwner(to, next)) return
        if (!await guardBoardAccess(to, next)) return
        if (!await guardPostAuthor(to, next)) return

        next()
    }
}

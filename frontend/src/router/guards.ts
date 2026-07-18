import type { RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { userSettingsSessionQueryKey } from '@/features/user/useUser'
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
import { isSessionGenerationCurrent } from '@/queryAuthScope'

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
    return async (to: RouteLocationNormalized, from: RouteLocationNormalized) => {
        const authStore = useAuthStore()

        const authResult = await ensureHydratedAuth(to)
        if (authResult !== true) return authResult

        if (authStore.user?.status === 'SANCTIONED') {
            await authStore.handleSanctionedSession()
            return { name: 'login' }
        }

        const boardUrlParam = getStringRouteParam(to.params.boardUrl)
        if (isReservedBoardUrl(boardUrlParam)) {
            return { name: 'error', query: { status: '404' } }
        }

        if (to.meta.guestOnly && to.name !== 'oauth-callback' && !authStore.isAuthenticated) {
            if (from.name && !from.meta.guestOnly) {
                saveLoginRedirect(from.fullPath)
            }
        }

        if (to.meta.requiresAuth && !authStore.isAuthenticated) {
            return { name: 'login', query: { redirect: to.fullPath } }
        }
        if (to.meta.roles && !to.meta.roles.includes(authStore.user?.role || '')) {
            return { name: 'home' }
        }
        if (to.meta.guestOnly && authStore.isAuthenticated && authStore.user && to.name !== 'oauth-callback') {
            return { name: 'home' }
        }

        if (authStore.isAuthenticated) {
            const sessionGeneration = authStore.sessionGeneration
            try {
                const settings = await queryClient.fetchQuery({
                    queryKey: userSettingsSessionQueryKey(sessionGeneration),
                    meta: { authScoped: true },
                    queryFn: async ({ signal }) => unwrapAxiosApiData(await userApi.getUserSettings({ signal })),
                    staleTime: QUERY_STALE_TIME.MEDIUM,
                })
                if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false

                if (shouldRedirectToOnboarding(to.name, to.meta.skipOnboarding, settings?.onboardingCompletedAt)) {
                    return { name: 'onboarding', query: { redirect: to.fullPath } }
                }
            } catch {
                if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false
                // Settings failures should not block route entry.
            }
        }

        const emoticonResult = await guardEmoticonOwner(to)
        if (emoticonResult !== true) return emoticonResult
        const boardResult = await guardBoardAccess(to)
        if (boardResult !== true) return boardResult
        const postResult = await guardPostAuthor(to)
        if (postResult !== true) return postResult

        return true
    }
}

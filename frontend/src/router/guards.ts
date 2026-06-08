import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { emoticonApi } from '@/api/emoticon'
import { postApi } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import { queryClient } from '@/queryClient'
import {
    BOARD_WRITE_FORBIDDEN_MESSAGE_KEY,
    canUserWriteBoardPost,
    fetchBoardForWriteAccess,
} from '@/composables/useBoardWriteAccess'
import { emoticonDetailQueryKey } from '@/composables/useEmoticonEditResource'
import { postDetailQueryKey } from '@/composables/postQueryKeys'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { saveLoginRedirect } from '@/utils/authRedirect'
import logger from '@/utils/logger'
import i18n from '@/i18n'
import { normalizePostReactionFlags, type PostReactionAlias } from '@/utils/postViewModel'
import type { Post } from '@/types'

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
    }
}

const getStringRouteParam = (param: unknown) => typeof param === 'string' ? param : ''

async function fetchPostForAuthorGuard(postId: string): Promise<Post> {
    return queryClient.fetchQuery({
        queryKey: postDetailQueryKey(postId, false),
        queryFn: async () => {
            const post = unwrapAxiosApiData(await postApi.getPost(postId, {
                params: {
                    incrementView: false,
                },
            }))
            return normalizePostReactionFlags(post as PostReactionAlias)
        },
        retry: false,
    })
}

async function ensureHydratedAuth(to: RouteLocationNormalized, next: NavigationGuardNext) {
    const authStore = useAuthStore()
    if (authStore.user || !authStore.accessToken) {
        return true
    }

    try {
        const didFetchUser = await authStore.fetchUser()
        if (to.meta.requiresAuth && (!didFetchUser || !authStore.user)) {
            if (!authStore.isAuthenticated) {
                await authStore.logout()
                next({ name: 'login', query: { redirect: to.fullPath } })
            } else {
                next({ name: 'error', query: { status: '503' } })
            }
            return false
        }
    } catch {
        await authStore.logout()
        if (to.meta.requiresAuth) {
            next({ name: 'login', query: { redirect: to.fullPath } })
            return false
        }
    }

    return true
}

async function guardEmoticonOwner(to: RouteLocationNormalized, next: NavigationGuardNext) {
    if (!to.meta.requiresEmoticonOwner) {
        return true
    }

    const authStore = useAuthStore()
    const emoticonIdParam = typeof to.params.emoticonId === 'string' ? Number(to.params.emoticonId) : NaN
    if (!Number.isFinite(emoticonIdParam)) {
        next({ name: 'error', query: { status: '404' } })
        return false
    }

    try {
        const emoticon = await queryClient.fetchQuery({
            queryKey: emoticonDetailQueryKey(emoticonIdParam),
            queryFn: () => emoticonApi.getEmoticonData(emoticonIdParam),
            retry: false,
            staleTime: QUERY_STALE_TIME.SHORT,
        })
        if (emoticon.creatorId !== authStore.user?.userId) {
            useToastStore().addToast(i18n.global.t('emoticon.edit.noPermission'), 'error')
            next({ name: 'emoticon-detail', params: { emoticonId: to.params.emoticonId } })
            return false
        }
    } catch (error) {
        logger.error('Failed to verify emoticon edit access:', error)
        next({ name: 'error', query: { status: '500' } })
        return false
    }

    return true
}

async function guardBoardAccess(to: RouteLocationNormalized, next: NavigationGuardNext) {
    if (!to.meta.requiresBoardAdmin && !to.meta.requiresWritableBoard) {
        return true
    }

    const authStore = useAuthStore()
    const boardUrl = getStringRouteParam(to.params.boardUrl)
    if (!boardUrl) {
        next({ name: 'error', query: { status: '404' } })
        return false
    }

    try {
        const board = await fetchBoardForWriteAccess(queryClient, boardUrl)
        if (to.meta.requiresBoardAdmin && !board.isAdmin) {
            useToastStore().addToast(i18n.global.t('common.messages.boardManageForbidden'), 'error')
            next({ name: 'board-detail', params: { boardUrl } })
            return false
        }
        if (to.meta.requiresWritableBoard && !canUserWriteBoardPost(board, authStore.isAuthenticated, authStore.user?.role)) {
            useToastStore().addToast(i18n.global.t(BOARD_WRITE_FORBIDDEN_MESSAGE_KEY), 'error')
            next({ name: 'board-detail', params: { boardUrl } })
            return false
        }
    } catch (error) {
        logger.error('Failed to verify board access:', error)
        next({ name: 'error', query: { status: '500' } })
        return false
    }

    return true
}

async function guardPostAuthor(to: RouteLocationNormalized, next: NavigationGuardNext) {
    if (!to.meta.requiresPostAuthor) {
        return true
    }

    const authStore = useAuthStore()
    const boardUrl = getStringRouteParam(to.params.boardUrl)
    const postId = getStringRouteParam(to.params.postId)
    const currentUserId = authStore.user?.userId
    if (!boardUrl || !postId) {
        next({ name: 'error', query: { status: '404' } })
        return false
    }
    if (!currentUserId) {
        next({ name: 'error', query: { status: '503' } })
        return false
    }

    try {
        const post = await fetchPostForAuthorGuard(postId)
        if (post.author.userId !== currentUserId) {
            useToastStore().addToast(i18n.global.t('common.messages.postEditForbidden'), 'error')
            next({ name: 'post-detail', params: { boardUrl, postId } })
            return false
        }
    } catch (error) {
        logger.error('Failed to verify post author:', error)
        next({ name: 'error', query: { status: '500' } })
        return false
    }

    return true
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

        const boardUrlParam = getStringRouteParam(to.params.boardUrl).toLowerCase()
        if (boardUrlParam === 'inquiry') {
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

        if (!await guardEmoticonOwner(to, next)) return
        if (!await guardBoardAccess(to, next)) return
        if (!await guardPostAuthor(to, next)) return

        next()
    }
}

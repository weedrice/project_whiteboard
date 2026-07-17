import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
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
} from '@/features/board/access/useBoardWriteAccess'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { postDetailQueryKey } from '@/features/board/posts/queries/postQueryKeys'
import { QUERY_STALE_TIME } from '@/utils/constants'
import logger from '@/utils/logger'
import i18n from '@/i18n'
import type { Post } from '@/types'
import { getRouteFetchErrorStatus, getStringRouteParam } from '@/router/routeGuardModel'
import {
    AUTH_SCOPED_QUERY_META,
    isSessionGenerationCurrent,
    sessionQueryKey,
} from '@/queryAuthScope'

async function fetchPostForAuthorGuard(postId: string, sessionGeneration: number): Promise<Post> {
    return queryClient.fetchQuery({
        queryKey: sessionQueryKey(sessionGeneration, postDetailQueryKey(postId, false)),
        queryFn: async () => {
            const post = unwrapAxiosApiData(await postApi.getPost(postId, {
                params: {
                    incrementView: false,
                },
            }))
            return post
        },
        retry: false,
        meta: AUTH_SCOPED_QUERY_META,
    })
}

type ResourceGuardResult = true | false | RouteLocationRaw

export async function ensureHydratedAuth(to: RouteLocationNormalized): Promise<ResourceGuardResult> {
    const authStore = useAuthStore()
    if (authStore.user) {
        return true
    }

    if (!authStore.accessToken) {
        const didBootstrap = await authStore.bootstrapSession()
        if (!didBootstrap && to.meta.requiresAuth) {
            return { name: 'login', query: { redirect: to.fullPath } }
        }
        return true
    }

    try {
        const didFetchUser = await authStore.fetchUser()
        if (to.meta.requiresAuth && (!didFetchUser || !authStore.user)) {
            if (!authStore.isAuthenticated) {
                await authStore.logout()
                return { name: 'login', query: { redirect: to.fullPath } }
            } else {
                return { name: 'error', query: { status: '503' } }
            }
        }
    } catch {
        await authStore.logout()
        if (to.meta.requiresAuth) {
            return { name: 'login', query: { redirect: to.fullPath } }
        }
    }

    return true
}

export async function guardEmoticonOwner(to: RouteLocationNormalized): Promise<ResourceGuardResult> {
    if (!to.meta.requiresEmoticonOwner) {
        return true
    }

    const authStore = useAuthStore()
    const sessionGeneration = authStore.sessionGeneration
    const emoticonIdParam = typeof to.params.emoticonId === 'string' ? Number(to.params.emoticonId) : NaN
    if (!Number.isFinite(emoticonIdParam)) {
        return { name: 'error', query: { status: '404' } }
    }

    try {
        const emoticon = await queryClient.fetchQuery({
            queryKey: emoticonQueryKeys.detail(emoticonIdParam),
            queryFn: () => emoticonApi.getEmoticonData(emoticonIdParam),
            retry: false,
            staleTime: QUERY_STALE_TIME.SHORT,
        })
        if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false

        if (emoticon.creatorId !== authStore.user?.userId) {
            useToastStore().addToast(i18n.global.t('emoticon.edit.noPermission'), 'error')
            return { name: 'emoticon-detail', params: { emoticonId: to.params.emoticonId } }
        }
    } catch (error) {
        if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false
        logger.error('Failed to verify emoticon edit access:', error)
        return { name: 'error', query: { status: getRouteFetchErrorStatus(error) } }
    }

    return true
}

export async function guardBoardAccess(to: RouteLocationNormalized): Promise<ResourceGuardResult> {
    if (!to.meta.requiresBoardAdmin && !to.meta.requiresWritableBoard) {
        return true
    }

    const authStore = useAuthStore()
    const sessionGeneration = authStore.sessionGeneration
    const boardUrl = getStringRouteParam(to.params.boardUrl)
    if (!boardUrl) {
        return { name: 'error', query: { status: '404' } }
    }

    try {
        const board = await fetchBoardForWriteAccess(
            queryClient,
            boardUrl,
            sessionGeneration,
        )
        if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false

        if (to.meta.requiresBoardAdmin && !board.isAdmin) {
            useToastStore().addToast(i18n.global.t('common.messages.boardManageForbidden'), 'error')
            return { name: 'board-detail', params: { boardUrl } }
        }
        if (to.meta.requiresWritableBoard && !canUserWriteBoardPost(board, authStore.isAuthenticated, authStore.user?.role)) {
            useToastStore().addToast(i18n.global.t(BOARD_WRITE_FORBIDDEN_MESSAGE_KEY), 'error')
            return { name: 'board-detail', params: { boardUrl } }
        }
    } catch (error) {
        if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false
        logger.error('Failed to verify board access:', error)
        return { name: 'error', query: { status: getRouteFetchErrorStatus(error) } }
    }

    return true
}

export async function guardPostAuthor(to: RouteLocationNormalized): Promise<ResourceGuardResult> {
    if (!to.meta.requiresPostAuthor) {
        return true
    }

    const authStore = useAuthStore()
    const sessionGeneration = authStore.sessionGeneration
    const boardUrl = getStringRouteParam(to.params.boardUrl)
    const postId = getStringRouteParam(to.params.postId)
    const currentUserId = authStore.user?.userId
    if (!boardUrl || !postId) {
        return { name: 'error', query: { status: '404' } }
    }
    if (!currentUserId) {
        return { name: 'error', query: { status: '503' } }
    }

    try {
        const post = await fetchPostForAuthorGuard(postId, sessionGeneration)
        if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false

        if (post.author.userId !== currentUserId) {
            useToastStore().addToast(i18n.global.t('common.messages.postEditForbidden'), 'error')
            return { name: 'post-detail', params: { boardUrl, postId } }
        }
    } catch (error) {
        if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return false
        logger.error('Failed to verify post author:', error)
        return { name: 'error', query: { status: getRouteFetchErrorStatus(error) } }
    }

    return true
}

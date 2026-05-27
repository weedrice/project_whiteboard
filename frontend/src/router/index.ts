import { createRouter, createWebHistory, type RouteLocationNormalized, type NavigationGuardNext } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { boardApi } from '@/api/board'
import { emoticonApi } from '@/api/emoticon'
import { queryClient } from '@/queryClient'
import { canWriteBoardPost } from '@/utils/board'
import { QUERY_STALE_TIME } from '@/utils/constants'
import logger from '@/utils/logger'
import i18n from '@/i18n'
import type { BoardDetail } from '@/types'

const CHUNK_RELOAD_KEY = 'chunk-reload-attempted'

const isSamePostDetailRoute = (to: RouteLocationNormalized, from: RouteLocationNormalized) => (
    to.name === 'post-detail'
    && from.name === 'post-detail'
    && to.params.boardUrl === from.params.boardUrl
    && to.params.postId === from.params.postId
)

const isPostDetailListPageNavigation = (to: RouteLocationNormalized, from: RouteLocationNormalized) => (
    isSamePostDetailRoute(to, from)
    && String(to.query.page ?? '1') !== String(from.query.page ?? '1')
)

// Extend RouteMeta interface
declare module 'vue-router' {
    interface RouteMeta {
        requiresAuth?: boolean
        guestOnly?: boolean
        roles?: string[]
        layout?: string
        requiresWritableBoard?: boolean
        requiresBoardAdmin?: boolean
        requiresEmoticonOwner?: boolean
    }
}

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition
        }
        if (to.hash) {
            return { el: to.hash, behavior: 'smooth' }
        }
        if (isPostDetailListPageNavigation(to, from)) {
            return false
        }
        return { top: 0 }
    },
    routes: [
        {
            path: '/',
            name: 'home',
            component: () => import('@/views/home/HomeFeed.vue')
        },

        {
            path: '/auth',
            component: () => import('@/views/auth/AuthLayout.vue'),
            meta: { guestOnly: true },
            children: [
                {
                    path: 'login',
                    alias: '/login',
                    name: 'login',
                    component: () => import('@/views/auth/LoginPage.vue')
                },
                {
                    path: 'signup',
                    alias: '/signup',
                    name: 'signup',
                    component: () => import('@/views/auth/SignupPage.vue')
                },
                {
                    path: 'find',
                    alias: '/find',
                    name: 'find-account',
                    component: () => import('@/views/auth/FindAccountPage.vue')
                },
                {
                    path: 'forgot-password',
                    name: 'forgot-password',
                    component: () => import('@/views/auth/ForgotPasswordPage.vue')
                },
                {
                    path: 'reset-password',
                    name: 'reset-password',
                    alias: '/reset-password',
                    component: () => import('@/views/auth/ResetPasswordPage.vue'),
                    meta: { guestOnly: false }
                },
                {
                    path: 'oauth/callback',
                    name: 'oauth-callback',
                    component: () => import('@/views/auth/OAuthCallback.vue'),
                    meta: { guestOnly: false } // OAuth callback should not be blocked by guestOnly check
                }
            ]
        },
        {
            path: '/mypage',
            component: () => import('@/views/user/MyPage.vue'),
            meta: { requiresAuth: true },
            children: [
                {
                    path: '',
                    name: 'mypage',
                    component: () => import('@/views/user/MyPageDashboard.vue')
                },
                {
                    path: 'settings',
                    name: 'user-settings',
                    component: () => import('@/views/user/UserSettings.vue')
                },
                {
                    path: 'points',
                    name: 'point-history',
                    component: () => import('@/views/user/PointHistory.vue')
                },
                {
                    path: 'scraps',
                    name: 'MyScraps',
                    component: () => import('@/views/user/ScrapList.vue')
                },
                {
                    path: 'messages',
                    name: 'MyMessages',
                    component: () => import('@/views/user/MyMessages.vue')
                },
                {
                    path: 'notifications',
                    name: 'MyNotifications',
                    component: () => import('@/views/user/MyNotifications.vue')
                },
                {
                    path: 'reports',
                    name: 'MyReports',
                    component: () => import('@/views/user/MyReports.vue')
                },
                {
                    path: 'blocked',
                    name: 'BlockList',
                    component: () => import('@/views/user/BlockList.vue')
                },
                {
                    path: 'recent',
                    name: 'RecentViewed',
                    component: () => import('@/views/search/RecentViewed.vue')
                },
                {
                    path: 'subscriptions',
                    name: 'SubscribedBoards',
                    component: () => import('@/views/user/SubscribedBoards.vue')
                }
            ]
        },
        {
            path: '/board/create',
            name: 'board-create',
            component: () => import('@/views/board/BoardCreate.vue'),
            meta: { requiresAuth: true }
        },
        // Emoticon routes
        {
            path: '/emoticons',
            name: 'emoticon-list',
            component: () => import('@/views/emoticon/EmoticonList.vue')
        },
        {
            path: '/emoticons/register',
            name: 'emoticon-register',
            component: () => import('@/views/emoticon/EmoticonRegister.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/emoticons/:emoticonId',
            name: 'emoticon-detail',
            component: () => import('@/views/emoticon/EmoticonDetail.vue')
        },
        {
            path: '/emoticons/:emoticonId/edit',
            name: 'emoticon-edit',
            component: () => import('@/views/emoticon/EmoticonEdit.vue'),
            meta: { requiresAuth: true, requiresEmoticonOwner: true }
        },
        {
            path: '/boards',
            name: 'all-boards',
            component: () => import('@/views/board/AllBoardsPage.vue')
        },
        {
            path: '/board/:boardUrl/edit',
            name: 'board-edit',
            component: () => import('@/views/board/BoardEdit.vue'),
            meta: { requiresAuth: true, requiresBoardAdmin: true }
        },
        {
            path: '/inquiry',
            name: 'inquiry-write',
            component: () => import('@/views/board/InquiryWrite.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/board/:boardUrl/write',
            name: 'post-write',
            component: () => import('@/views/board/PostWrite.vue'),
            meta: { requiresAuth: true, requiresWritableBoard: true }
        },
        {
            path: '/board/:boardUrl',
            name: 'board-detail',
            component: () => import('@/views/board/BoardDetail.vue'),
            children: [
                {
                    path: 'post/:postId',
                    name: 'post-detail',
                    component: () => import('@/views/board/PostDetail.vue')
                }
            ]
        },
        {
            path: '/board/:boardUrl/post/:postId/edit',
            name: 'post-edit',
            component: () => import(/* webpackChunkName: "post-editor" */ '@/views/board/PostEdit.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/admin',
            meta: { requiresAuth: true, roles: ['SUPER_ADMIN'], layout: 'AdminLayout' },
            children: [
                {
                    path: 'dashboard',
                    name: 'AdminDashboard',
                    component: () => import('@/views/admin/AdminDashboard.vue')
                },
                {
                    path: 'users',
                    name: 'UserManagement',
                    component: () => import('@/views/admin/UserManagement.vue')
                },
                {
                    path: 'boards',
                    name: 'BoardManagement',
                    component: () => import('@/views/admin/BoardManagement.vue')
                },
                {
                    path: 'inquiries',
                    name: 'InquiryManagement',
                    component: () => import('@/views/admin/AdminInquiryPosts.vue')
                },
                {
                    path: 'admins',
                    name: 'AdminManagement',
                    component: () => import('@/views/admin/AdminManagement.vue')
                },
                {
                    path: 'reports',
                    name: 'ReportManagement',
                    component: () => import('@/views/admin/ReportManagement.vue')
                },
                {
                    path: 'security',
                    name: 'SecuritySettings',
                    component: () => import('@/views/admin/SecuritySettings.vue')
                },
                {
                    path: 'settings',
                    name: 'GlobalSettings',
                    component: () => import('@/views/admin/GlobalSettings.vue')
                },
                {
                    path: 'error-logs',
                    name: 'ErrorLogManagement',
                    component: () => import('@/views/admin/ErrorLogManagement.vue')
                },
                {
                    path: '',
                    redirect: '/admin/dashboard'
                }
            ]
        },
        {
            path: '/privacy',
            name: 'privacy-policy',
            component: () => import('@/views/PrivacyPolicy.vue')
        },
        {
            path: '/terms',
            name: 'terms-of-service',
            component: () => import('@/views/TermsOfService.vue')
        },
        {
            path: '/error',
            name: 'error',
            component: () => import('@/views/common/ErrorPage.vue')
        },
        {
            path: '/search',
            name: 'search',
            component: () => import('@/views/search/SearchPage.vue')
        },
        {
            path: '/:pathMatch(.*)*',
            redirect: { name: 'error', query: { status: '404' } }
        }
    ],
})

router.beforeEach(async (to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
    const authStore = useAuthStore()

    // Initialize auth state from local storage if needed
    if (!authStore.user && authStore.accessToken) {
        try {
            const didFetchUser = await authStore.fetchUser()
            if (to.meta.requiresAuth && (!didFetchUser || !authStore.user)) {
                if (!authStore.isAuthenticated) {
                    await authStore.logout()
                    next({ name: 'login', query: { redirect: to.fullPath } })
                } else {
                    next({ name: 'error', query: { status: '503' } })
                }
                return
            }
        } catch (error) {
            // Token might be invalid
            await authStore.logout()
            if (to.meta.requiresAuth) {
                next({ name: 'login', query: { redirect: to.fullPath } })
                return
            }
        }
    }

    // Sanction check
    if (authStore.user?.status === 'SANCTIONED') {
        await authStore.handleSanctionedSession()
        next({ name: 'login' })
        return
    }

    const boardUrlParam = typeof to.params.boardUrl === 'string' ? to.params.boardUrl.toLowerCase() : ''
    if (boardUrlParam === 'inquiry') {
        next({ name: 'error', query: { status: '404' } })
        return
    }

    // Save previous path before guest-only navigation for post-login redirect
    if (to.meta.guestOnly && to.name !== 'oauth-callback' && !authStore.isAuthenticated) {
        if (from.name && !from.meta.guestOnly) {
            sessionStorage.setItem('loginRedirect', from.fullPath)
        }
    }

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        next({ name: 'login', query: { redirect: to.fullPath } })
    } else if (to.meta.roles && !to.meta.roles.includes(authStore.user?.role || '')) {
        // Role check (e.g. SUPER_ADMIN)
        next({ name: 'home' })
    } else if (to.meta.guestOnly && authStore.isAuthenticated && authStore.user && to.name !== 'oauth-callback') {
        // OAuth callback should be allowed even if authenticated (handles re-login scenarios)
        next({ name: 'home' })
    } else {
        if (to.meta.requiresEmoticonOwner) {
            const emoticonIdParam = typeof to.params.emoticonId === 'string' ? Number(to.params.emoticonId) : NaN
            if (!Number.isFinite(emoticonIdParam)) {
                next({ name: 'error', query: { status: '404' } })
                return
            }

            try {
                const emoticon = await emoticonApi.getEmoticonData(emoticonIdParam)
                if (emoticon.creatorId !== authStore.user?.userId) {
                    useToastStore().addToast(i18n.global.t('emoticon.edit.noPermission'), 'error')
                    next({ name: 'emoticon-detail', params: { emoticonId: to.params.emoticonId } })
                    return
                }
            } catch (error) {
                logger.error('Failed to verify emoticon edit access:', error)
                next({ name: 'error', query: { status: '500' } })
                return
            }
        }

        if (to.meta.requiresBoardAdmin || to.meta.requiresWritableBoard) {
            const boardUrl = typeof to.params.boardUrl === 'string' ? to.params.boardUrl : ''
            if (!boardUrl) {
                next({ name: 'error', query: { status: '404' } })
                return
            }

            try {
                const board = await queryClient.fetchQuery({
                    queryKey: ['board', boardUrl],
                    queryFn: async () => {
                        const { data } = await boardApi.getBoard(boardUrl)
                        return data.data as BoardDetail
                    },
                    staleTime: QUERY_STALE_TIME.SHORT,
                })
                if (to.meta.requiresBoardAdmin && !board.isAdmin) {
                    useToastStore().addToast('You do not have permission to manage this board.', 'error')
                    next({ name: 'board-detail', params: { boardUrl } })
                    return
                }
                if (to.meta.requiresWritableBoard && !canWriteBoardPost(board, authStore.isAuthenticated, authStore.user?.role)) {
                    useToastStore().addToast('You do not have permission to write on this board.', 'error')
                    next({ name: 'board-detail', params: { boardUrl } })
                    return
                }
            } catch (error) {
                logger.error('Failed to verify board access:', error)
                next({ name: 'error', query: { status: '500' } })
                return
            }
        }

        next()
    }
})

router.afterEach(() => {
    if (sessionStorage.getItem(CHUNK_RELOAD_KEY)) {
        sessionStorage.removeItem(CHUNK_RELOAD_KEY)
    }
})

router.onError((error) => {
    if (error.message.includes('Failed to fetch dynamically imported module') || error.message.includes('Importing a module script failed')) {
        const alreadyRetried = sessionStorage.getItem(CHUNK_RELOAD_KEY) === '1'
        if (!alreadyRetried) {
            sessionStorage.setItem(CHUNK_RELOAD_KEY, '1')
            window.location.reload()
            return
        }
        sessionStorage.removeItem(CHUNK_RELOAD_KEY)
        router.push({
            name: 'error',
            query: {
                status: '500',
                message: 'Chunk load failed after retry'
            }
        })
    } else {
        logger.error('Router Error:', error)
        router.push({
            name: 'error',
            query: {
                status: '500',
                message: error.message || 'Navigation Error'
            }
        })
    }
})

export default router

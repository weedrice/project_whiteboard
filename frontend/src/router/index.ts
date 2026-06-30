import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { createAppNavigationGuard } from '@/router/guards'
import logger from '@/utils/logger'
import { reloadPage } from '@/utils/pageReload'
import { SessionStorage } from '@/utils/storage'

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
            meta: { requiresAuth: true, requiresPostAuthor: true }
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

router.beforeEach(createAppNavigationGuard())

router.afterEach(() => {
    if (SessionStorage.getString(CHUNK_RELOAD_KEY)) {
        SessionStorage.remove(CHUNK_RELOAD_KEY)
    }
})

router.onError((error) => {
    if (error.message.includes('Failed to fetch dynamically imported module') || error.message.includes('Importing a module script failed')) {
        const alreadyRetried = SessionStorage.getString(CHUNK_RELOAD_KEY) === '1'
        if (!alreadyRetried) {
            SessionStorage.setString(CHUNK_RELOAD_KEY, '1')
            reloadPage()
            return
        }
        SessionStorage.remove(CHUNK_RELOAD_KEY)
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

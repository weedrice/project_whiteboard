import { createRouter, createWebHistory, type RouteLocationNormalized, type NavigationGuardNext } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import AdminDashboard from '@/views/admin/AdminDashboard.vue'

// Extend RouteMeta interface
declare module 'vue-router' {
    interface RouteMeta {
        requiresAuth?: boolean
        guestOnly?: boolean
        roles?: string[]
        layout?: string
    }
}

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
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
        {
            path: '/boards',
            name: 'all-boards',
            component: () => import('@/views/board/AllBoardsPage.vue')
        },
        {
            path: '/board/:boardUrl/edit',
            name: 'board-edit',
            component: () => import('@/views/board/BoardEdit.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/board/:boardUrl/write',
            name: 'post-write',
            component: () => import('@/views/board/PostWrite.vue'),
            meta: { requiresAuth: true }
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
            component: () => import('@/views/board/PostEdit.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/admin',
            meta: { requiresAuth: true, roles: ['SUPER_ADMIN'], layout: 'AdminLayout' },
            children: [
                {
                    path: 'dashboard',
                    name: 'AdminDashboard',
                    component: AdminDashboard
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
                    path: '',
                    redirect: '/admin/dashboard'
                }
            ]
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
            redirect: '/'
        }
    ],
})

router.beforeEach(async (to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
    const authStore = useAuthStore()

    // Initialize auth state from local storage if needed
    if (!authStore.user && authStore.accessToken) {
        await authStore.fetchUser()
    }

    // Sanction check
    if (authStore.user?.status === 'SANCTIONED') {
        await authStore.logout()
        next({ name: 'login' })
        return
    }

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        next({ name: 'login', query: { redirect: to.fullPath } })
    } else if (to.meta.roles && !to.meta.roles.includes(authStore.user?.role || '')) {
        // Role check (e.g. SUPER_ADMIN)
        next({ name: 'home' })
    } else if (to.meta.guestOnly && authStore.isAuthenticated) {
        next({ name: 'home' })
    } else {
        next()
    }
})

export default router

import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'home',
            component: () => import('@/views/board/BoardList.vue')
        },
        {
            path: '/test-components',
            name: 'test-components',
            component: () => import('@/views/ComponentTest.vue')
        },
        {
            path: '/login',
            name: 'login',
            component: () => import('@/views/auth/LoginPage.vue'),
            meta: { guestOnly: true }
        },
        {
            path: '/signup',
            name: 'signup',
            component: () => import('@/views/auth/SignupPage.vue'),
            meta: { guestOnly: true }
        },
        {
            path: '/mypage',
            name: 'mypage',
            component: () => import('@/views/user/MyPage.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/mypage/settings',
            name: 'user-settings',
            component: () => import('@/views/user/UserSettings.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/mypage/points',
            name: 'point-history',
            component: () => import('@/views/user/PointHistory.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/mypage/scraps',
            name: 'scrap-list',
            component: () => import('@/views/user/ScrapList.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/mypage/reports',
            name: 'my-reports',
            component: () => import('@/views/user/MyReports.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/search/recent',
            name: 'recent-viewed',
            component: () => import('@/views/search/RecentViewed.vue'),
            meta: { requiresAuth: true }
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
            path: '/board/:boardId/edit',
            name: 'board-edit',
            component: () => import('@/views/board/BoardEdit.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/board/:boardId/write',
            name: 'post-write',
            component: () => import('@/views/board/PostWrite.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/board/:boardId',
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
            path: '/board/:boardId/post/:postId/edit',
            name: 'post-edit',
            component: () => import('@/views/board/PostEdit.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/admin',
            component: () => import('@/components/layout/AdminLayout.vue'),
            meta: { requiresAuth: true, requiresAdmin: true },
            children: [
                {
                    path: 'dashboard',
                    name: 'admin-dashboard',
                    component: () => import('@/views/admin/DashboardPage.vue')
                },
                {
                    path: 'users',
                    name: 'admin-users',
                    component: () => import('@/views/admin/UserListPage.vue')
                },
                {
                    path: 'reports',
                    name: 'admin-reports',
                    component: () => import('@/views/admin/ReportList.vue')
                },
                {
                    path: 'ip-blocks',
                    name: 'admin-ip-blocks',
                    component: () => import('@/views/admin/IpBlockList.vue')
                },
                {
                    path: 'settings',
                    name: 'admin-settings',
                    component: () => import('@/views/admin/SettingsPage.vue')
                }
            ]
        },
        {
            path: '/error',
            name: 'error',
            component: () => import('@/views/ErrorPage.vue'),
            props: route => ({ title: route.query.title, message: route.query.message })
        }
    ],
})

router.beforeEach(async (to, from, next) => {
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
    } else if (to.meta.requiresAdmin && !authStore.isAdmin) {
        next({ name: 'home' }) // Redirect non-admins to home
    } else if (to.meta.guestOnly && authStore.isAuthenticated) {
        next({ name: 'home' })
    } else {
        next()
    }
})

export default router

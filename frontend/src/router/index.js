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
            path: '/board/create',
            name: 'board-create',
            component: () => import('@/views/board/BoardCreate.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/board/:boardId',
            name: 'board-detail',
            component: () => import('@/views/board/BoardDetail.vue')
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
            path: '/board/:boardId/post/:postId',
            name: 'post-detail',
            component: () => import('@/views/board/PostDetail.vue')
        },
        {
            path: '/board/:boardId/post/:postId/edit',
            name: 'post-edit',
            component: () => import('@/views/board/PostEdit.vue'),
            meta: { requiresAuth: true }
        }
    ],
})

router.beforeEach(async (to, from, next) => {
    const authStore = useAuthStore()

    // Initialize auth state from local storage if needed
    if (!authStore.user && authStore.accessToken) {
        await authStore.fetchUser()
    }

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        next({ name: 'login', query: { redirect: to.fullPath } })
    } else if (to.meta.guestOnly && authStore.isAuthenticated) {
        next({ name: 'home' })
    } else {
        next()
    }
})

export default router

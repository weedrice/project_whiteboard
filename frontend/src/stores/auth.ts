import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { useThemeStore } from '@/stores/theme'
import router from '@/router'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import type { User, LoginCredentials } from '@/types'



export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
    const isAuthenticated = computed(() => !!accessToken.value)
    const themeStore = useThemeStore()
    const toastStore = useToastStore()

    async function login(credentials: LoginCredentials) {
        try {
            const { data } = await authApi.login(credentials)
            if (data.success) {
                const { accessToken: token, refreshToken, user: userData } = data.data

                accessToken.value = token
                user.value = userData

                localStorage.setItem('accessToken', token)
                localStorage.setItem('refreshToken', refreshToken)

                // Set theme from user settings
                if (userData.theme) {
                    themeStore.setTheme(userData.theme)
                }

                return true
            }
        } catch (error) {
            logger.error('Login failed:', error)
            throw error
        }
    }

    async function logout() {
        try {
            const refreshToken = localStorage.getItem('refreshToken')
            if (refreshToken) {
                await authApi.logout(refreshToken)
            }
        } catch (error) {
            logger.error('Logout failed:', error)
        } finally {
            accessToken.value = null
            user.value = null
            localStorage.removeItem('accessToken')
            localStorage.removeItem('refreshToken')
            themeStore.setTheme('LIGHT') // Reset to default on logout
            router.push('/login')
        }
    }

    async function fetchUser(config?: any) {
        // Double check token existence
        const token = localStorage.getItem('accessToken')
        if (!token) {
            accessToken.value = null
            user.value = null
            return
        }

        if (!accessToken.value) accessToken.value = token

        try {
            const { data } = await authApi.getMe(config)
            if (data.success) {
                user.value = data.data

                // Check for sanctions
                if (user.value?.status === 'SANCTIONED') {
                    toastStore.addToast('Your account has been sanctioned. You will be logged out.', 'error')
                    await logout()
                    return
                }

                // Sync theme from server
                if (user.value?.theme) {
                    themeStore.setTheme(user.value.theme)
                }
            }
        } catch (error) {
            logger.error('Fetch user failed:', error)
            // 401 에러는 axios 인터셉터에서 refresh token으로 처리함
            // 여기서는 로그만 남기고, 인터셉터가 refresh 실패 시 로그아웃 처리
            // 네트워크 에러나 서버 에러(500 등)는 로그아웃하지 않음
        }
    }

    return {
        user,
        accessToken,
        isAuthenticated,
        isAdmin: computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN'),
        login,
        logout,
        fetchUser
    }
})

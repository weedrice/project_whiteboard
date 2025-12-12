import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { useThemeStore } from '@/stores/theme'
import router from '@/router'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

export interface User {
    id: number;
    username: string;
    email: string;
    role: 'USER' | 'ADMIN' | 'SUPER_ADMIN';
    status: 'ACTIVE' | 'INACTIVE' | 'SANCTIONED';
    theme?: 'LIGHT' | 'DARK';
    [key: string]: any;
}

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
    const isAuthenticated = computed(() => !!accessToken.value)
    const themeStore = useThemeStore()
    const toastStore = useToastStore()

    async function login(credentials: any) {
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

    async function fetchUser() {
        if (!accessToken.value) return

        try {
            const { data } = await authApi.getMe()
            if (data.success) {
                user.value = data.data

                // Check for sanctions
                if (user.value?.status === 'SANCTIONED') {
                    toastStore.addToast('Your account has been sanctioned. You will be logged out.', 'error')
                    await logout()
                    return
                }
            }
        } catch (error) {
            logger.error('Fetch user failed:', error)
            // If fetch user fails (e.g. invalid token), logout
            logout()
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

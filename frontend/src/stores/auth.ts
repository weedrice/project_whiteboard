import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { useThemeStore } from '@/stores/theme'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import i18n from '@/i18n'
import { Storage } from '@/utils/storage'
import type { User, LoginCredentials } from '@/types'
import type { AxiosRequestConfig } from 'axios'



export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const accessToken = ref<string | null>(Storage.getString('accessToken'))
    const isAuthenticated = computed(() => !!accessToken.value)
    const themeStore = useThemeStore()
    const toastStore = useToastStore()

    async function login(credentials: LoginCredentials): Promise<boolean> {
        try {
            const { data } = await authApi.login(credentials)
            if (data.success) {
                const { accessToken: token, user: userData } = data.data

                accessToken.value = token
                user.value = userData

                Storage.setString('accessToken', token)
                Storage.remove('refreshToken')

                // Set theme from user settings
                if (userData.theme) {
                    themeStore.setTheme(userData.theme)
                }

                return true
            }
            return false
        } catch (error: unknown) {
            logger.error('Login failed:', error)
            throw error
        }
    }

    async function logout() {
        try {
            await authApi.logout()
        } catch (error: unknown) {
            logger.error('Logout failed:', error)
        } finally {
            accessToken.value = null
            user.value = null
            Storage.remove('accessToken')
            Storage.remove('refreshToken')
            // themeStore.setTheme('LIGHT') // Reset to default on logout -> Removed to persist theme
        }
    }

    async function handleSanctionedSession() {
        toastStore.addToast(i18n.global.t('user.sanctioned'), 'error')
        await logout()
    }

    async function fetchUser(config?: AxiosRequestConfig): Promise<boolean> {
        // Double check token existence
        const token = Storage.getString('accessToken')
        if (!token) {
            accessToken.value = null
            user.value = null
            return false
        }

        if (!accessToken.value) accessToken.value = token

        try {
            const { data } = await authApi.getMe(config)
            if (data.success) {
                user.value = data.data

                // Check for sanctions
                if (user.value?.status === 'SANCTIONED') {
                    await handleSanctionedSession()
                    return false
                }

                // Sync theme from server
                if (user.value?.theme) {
                    themeStore.setTheme(user.value.theme)
                }

                return true
            }
            return false
        } catch (error: unknown) {
            logger.error('Fetch user failed:', error)
            // 401 에러는 axios 인터셉터에서 refresh token으로 처리함
            // 여기서는 로그만 남기고, 인터셉터가 refresh 실패 시 로그아웃 처리
            // 네트워크 에러나 서버 에러(500 등)는 로그아웃하지 않음
            return false
        }
    }

    function setTokens(token: string) {
        accessToken.value = token
        Storage.setString('accessToken', token)
        Storage.remove('refreshToken')
    }

    return {
        user,
        accessToken,
        isAuthenticated,
        isAdmin: computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN'),
        login,
        logout,
        handleSanctionedSession,
        fetchUser,
        setTokens
    }
})

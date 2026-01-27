<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { t } = useI18n()

const LOGIN_REDIRECT_KEY = 'loginRedirect'

function isSafeRedirect(path: unknown): path is string {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')
}

onMounted(async () => {
  const rawAccess = route.query.accessToken
  const rawRefresh = route.query.refreshToken
  const accessToken = Array.isArray(rawAccess) ? rawAccess[0] : rawAccess
  const refreshToken = Array.isArray(rawRefresh) ? rawRefresh[0] : rawRefresh

  if (accessToken && refreshToken) {
    try {
      // Store tokens
      authStore.setTokens(accessToken, refreshToken)
      
      // Fetch user info
      await authStore.fetchUser()
      
      toastStore.addToast(t('auth.loginSuccess'), 'success')
      const redirect = sessionStorage.getItem(LOGIN_REDIRECT_KEY)
      sessionStorage.removeItem(LOGIN_REDIRECT_KEY)
      router.push(isSafeRedirect(redirect) ? redirect : '/')
    } catch (error) {
      logger.error('OAuth login failed:', error)
      toastStore.addToast(t('auth.loginFailed'), 'error')
      router.push('/login')
    }
  } else {
    toastStore.addToast(t('auth.loginFailed'), 'error')
    router.push('/login')
  }
})
</script>

<template>
  <div class="flex justify-center items-center h-screen">
    <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
  </div>
</template>

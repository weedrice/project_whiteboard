<script setup>
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

onMounted(async () => {
  const accessToken = route.query.accessToken
  const refreshToken = route.query.refreshToken

  if (accessToken && refreshToken) {
    try {
      // Store tokens
      authStore.setTokens(accessToken, refreshToken)
      
      // Fetch user info
      await authStore.fetchUser()
      
      toastStore.addToast(t('auth.loginSuccess'), 'success')
      router.push('/')
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

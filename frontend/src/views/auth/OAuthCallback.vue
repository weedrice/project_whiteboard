<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import logger from '@/utils/logger'
import { clearLoginRedirect, getStoredLoginRedirect } from '@/utils/authRedirect'
import { clearSensitiveTokensFromUrl, getHashToken } from '@/utils/oauthCallbackTokens'
import { getSingleQueryValue } from '@/utils/routeQueryValue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { t } = useI18n()

onMounted(async () => {
  const accessToken = getSingleQueryValue(route.query.accessToken) ?? getHashToken('accessToken')

  // Remove sensitive values from address bar immediately.
  clearSensitiveTokensFromUrl()

  if (accessToken) {
    try {
      // Store tokens
      authStore.setTokens(accessToken)
      
      // Fetch user info
      const didFetchUser = await authStore.fetchUser()
      if (!didFetchUser) {
        throw new Error('OAuth user hydration failed')
      }
      
      toastStore.addToast(t('auth.loginSuccess'), 'success')
      const redirect = getStoredLoginRedirect()
      clearLoginRedirect()
      router.push(redirect ?? '/')
    } catch (error) {
      logger.error('OAuth login failed:', error)
      await authStore.logout()
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
    <BaseSpinner size="lg" />
  </div>
</template>

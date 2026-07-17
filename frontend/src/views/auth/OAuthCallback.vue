<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { unwrapApiData } from '@/api/response'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import logger from '@/utils/logger'
import { clearLoginRedirect, getStoredLoginRedirect } from '@/utils/authRedirect'
import { clearSensitiveTokensFromUrl } from '@/utils/oauthCallbackTokens'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { t } = useI18n()

onMounted(async () => {
  clearSensitiveTokensFromUrl()
  const generation = authStore.sessionGeneration
  const previousToken = authStore.accessToken

  try {
    const { data } = await authApi.refreshToken({
      skipAuthRefresh: true,
      skipGlobalErrorHandler: true,
    })
    const { accessToken } = unwrapApiData(data)
    if (!accessToken) {
      throw new Error('OAuth refresh returned an invalid access token')
    }

    if (!authStore.applyNewSessionIfCurrent(generation, previousToken, accessToken)) {
      return
    }

    const didFetchUser = await authStore.fetchUser({ skipAuthRefresh: true })
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
})
</script>

<template>
  <div class="flex min-h-dvh justify-center items-center">
    <BaseSpinner size="lg" />
  </div>
</template>

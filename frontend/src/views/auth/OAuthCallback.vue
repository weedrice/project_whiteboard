<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { unwrapApiData } from '@/api/response'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import logger from '@/utils/logger'
import { consumeLoginRedirect } from '@/utils/authRedirect'
import { clearSensitiveTokensFromUrl } from '@/utils/oauthCallbackTokens'
import { coordinateAuthRefresh } from '@/api/authRefreshCoordinator'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { t } = useI18n()
const callbackController = new AbortController()

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

onBeforeUnmount(() => callbackController.abort())

onMounted(async () => {
  clearSensitiveTokensFromUrl()
  const generation = authStore.sessionGeneration
  const previousToken = authStore.accessToken
  let ownedGeneration: number | null = null
  let ownedToken: string | null = null

  try {
    const accessToken = await coordinateAuthRefresh(async (signal) => {
      if (authStore.sessionGeneration !== generation || authStore.accessToken !== previousToken) {
        throw new DOMException('Authentication session changed', 'AbortError')
      }
      const { data } = await authApi.refreshToken({
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
        signal,
      })
      return unwrapApiData(data).accessToken
    }, { previousToken, signal: callbackController.signal })
    if (!accessToken) {
      throw new Error('OAuth refresh returned an invalid access token')
    }
    if (callbackController.signal.aborted
      || authStore.sessionGeneration !== generation
      || authStore.accessToken !== previousToken) return

    if (!authStore.applyNewSessionIfCurrent(generation, previousToken, accessToken)) {
      return
    }
    ownedGeneration = authStore.sessionGeneration
    ownedToken = accessToken

    const hydration = await authStore.hydrateUser({ skipAuthRefresh: true })
    if (callbackController.signal.aborted || hydration === 'stale') return
    if (hydration === 'transient-failure') {
      if (callbackController.signal.aborted
        || authStore.sessionGeneration !== ownedGeneration
        || authStore.accessToken !== ownedToken) return
      await router.replace({ name: 'error', query: { status: '503', retry: '/' } })
      return
    }
    if (hydration === 'terminal-failure') {
      // hydrateUser clears its own failed session before returning terminal-failure.
      if (authStore.sessionGeneration !== ownedGeneration + 1
        || authStore.accessToken !== null) return
      const logoutPromise = authStore.logout()
      const loggedOutGeneration = authStore.sessionGeneration
      await logoutPromise
      if (!callbackController.signal.aborted
        && authStore.sessionGeneration === loggedOutGeneration
        && authStore.accessToken === null) {
        toastStore.addToast(t('auth.loginFailed'), 'error')
        await router.replace('/login')
      }
      return
    }
    if (callbackController.signal.aborted
      || authStore.sessionGeneration !== ownedGeneration
      || authStore.accessToken !== ownedToken) return

    toastStore.addToast(t('auth.loginSuccess'), 'success')
    const redirect = consumeLoginRedirect()
    router.push(redirect ?? '/')
  } catch (error) {
    if (isAbortError(error) || callbackController.signal.aborted) return
    const ownsCurrentSession = ownedGeneration === null
      ? authStore.sessionGeneration === generation && authStore.accessToken === previousToken
      : authStore.sessionGeneration === ownedGeneration && authStore.accessToken === ownedToken
    if (!ownsCurrentSession) return
    logger.error('OAuth login failed:', error)
    const logoutPromise = authStore.logout()
    const loggedOutGeneration = authStore.sessionGeneration
    await logoutPromise
    if (callbackController.signal.aborted
      || authStore.sessionGeneration !== loggedOutGeneration
      || authStore.accessToken !== null) return
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

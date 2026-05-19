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

function getSingleValue(value: unknown): string | null {
  if (typeof value === 'string' && value.length > 0) return value
  if (Array.isArray(value) && typeof value[0] === 'string' && value[0].length > 0) return value[0]
  return null
}

function getHashToken(key: string): string | null {
  const rawHash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : window.location.hash
  if (!rawHash) return null
  return new URLSearchParams(rawHash).get(key)
}

function clearSensitiveTokensFromUrl() {
  const current = new URL(window.location.href)
  const hadQueryToken = current.searchParams.has('accessToken') || current.searchParams.has('refreshToken')
  current.searchParams.delete('accessToken')
  current.searchParams.delete('refreshToken')

  let hadHashToken = false
  if (current.hash) {
    const rawHash = current.hash.startsWith('#') ? current.hash.slice(1) : current.hash
    const hashParams = new URLSearchParams(rawHash)
    hadHashToken = hashParams.has('accessToken') || hashParams.has('refreshToken')
    hashParams.delete('accessToken')
    hashParams.delete('refreshToken')
    const cleanedHash = hashParams.toString()
    current.hash = cleanedHash ? `#${cleanedHash}` : ''
  }

  if (hadQueryToken || hadHashToken) {
    const cleanUrl = `${current.pathname}${current.search}${current.hash}`
    window.history.replaceState(window.history.state, document.title, cleanUrl)
  }
}

onMounted(async () => {
  const accessToken = getSingleValue(route.query.accessToken) ?? getHashToken('accessToken')

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
      const redirect = sessionStorage.getItem(LOGIN_REDIRECT_KEY)
      sessionStorage.removeItem(LOGIN_REDIRECT_KEY)
      router.push(isSafeRedirect(redirect) ? redirect : '/')
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
    <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
  </div>
</template>

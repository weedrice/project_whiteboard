<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Lock, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import AuthFormShell from '@/components/auth/AuthFormShell.vue'
import OAuthLoginButtons from '@/components/auth/OAuthLoginButtons.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { clearLoginRedirect, resolveLoginRedirect, saveLoginRedirect } from '@/utils/authRedirect'

const { t } = useI18n()
const toastStore = useToastStore()

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

onMounted(() => {
  saveLoginRedirect(route.query.redirect)
})

const loginId = ref('')
const password = ref('')
const isLoading = ref(false)

watch(loginId, (newValue) => {
  const filtered = newValue.replace(/[^a-zA-Z0-9_]/g, '')
  if (newValue !== filtered) {
    loginId.value = filtered
  }
})

async function handleLogin() {
  if (!loginId.value) {
    toastStore.addToast(t('auth.placeholders.loginId'), 'error')
    return
  }
  if (!password.value) {
    toastStore.addToast(t('auth.placeholders.password'), 'error')
    return
  }

  isLoading.value = true

  try {
    const didLogin = await authStore.login({
      loginId: loginId.value,
      password: password.value,
    })
    if (!didLogin) {
      toastStore.addToast(t('auth.loginFailed'), 'error', 3000, 'top-center')
      return
    }
    const redirect = resolveLoginRedirect(route.query.redirect)
    clearLoginRedirect()
    router.push(redirect)
  } catch (err: unknown) {
    const msg = err && typeof err === 'object' && 'response' in err
      ? (err as { response?: { data?: { error?: { message?: string } } } }).response?.data?.error?.message
      : undefined
    toastStore.addToast(msg || t('auth.loginFailed'), 'error', 3000, 'top-center')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <AuthFormShell>
    <template #header>
      <h1 class="text-3xl font-extrabold nv-title">
        {{ $t('common.login') }}
      </h1>
      <p class="mt-2 text-xs sm:text-sm nv-text-muted">
        {{ $t('common.or') }}
        <router-link
          to="/signup"
          class="nv-focus-ring inline-flex min-h-11 items-center rounded-md px-1 font-medium nv-accent-text hover:brightness-95"
        >
          {{ $t('auth.createAccount') }}
        </router-link>
      </p>
    </template>

    <form class="space-y-6" @submit.prevent="handleLogin">
      <div class="space-y-4">
        <BaseInput
          id="login-id"
          v-model="loginId"
          name="loginId"
          type="text"
          required
          autocomplete="username"
          :placeholder="$t('auth.placeholders.loginId')"
          :label="$t('common.loginId')"
          hideLabel
        >
          <template #prefix>
            <User class="h-5 w-5 nv-text-subtle" />
          </template>
        </BaseInput>

        <BaseInput
          id="password"
          v-model="password"
          name="password"
          type="password"
          required
          autocomplete="current-password"
          :placeholder="$t('auth.placeholders.password')"
          :label="$t('common.password')"
          hideLabel
        >
          <template #prefix>
            <Lock class="h-5 w-5 nv-text-subtle" />
          </template>
        </BaseInput>
      </div>

      <div class="flex justify-end">
        <router-link
          to="/auth/forgot-password"
          class="nv-focus-ring inline-flex min-h-11 items-center rounded-md px-1 text-xs nv-text-muted hover:text-[var(--nv-accent)] sm:text-sm"
        >
          {{ $t('auth.findIdPassword') }}
        </router-link>
      </div>

      <BaseButton type="submit" :loading="isLoading" class="w-full" variant="primary">
        {{ $t('common.login') }}
      </BaseButton>

      <OAuthLoginButtons />
    </form>
  </AuthFormShell>
</template>

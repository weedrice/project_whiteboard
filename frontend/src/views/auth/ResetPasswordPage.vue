<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useToastStore } from '@/stores/toast'
import { ChevronLeft, Lock } from 'lucide-vue-next'
import { isValidPassword } from '@/utils/validation'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const toastStore = useToastStore()

const token = computed(() => {
  const t = route.query.token
  return typeof t === 'string' ? t : ''
})

const newPassword = ref('')
const confirmPassword = ref('')
const isLoading = ref(false)

async function handleResetPassword() {
  if (!token.value) {
    toastStore.addToast(t('auth.invalidResetLink'), 'error')
    return
  }
  if (!newPassword.value) {
    toastStore.addToast(t('auth.placeholders.password'), 'error')
    return
  }
  if (!isValidPassword(newPassword.value)) {
    toastStore.addToast(t('auth.validation.passwordStrength'), 'error')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toastStore.addToast(t('auth.passwordMismatch'), 'error')
    return
  }

  isLoading.value = true
  try {
    const { data } = await authApi.resetPasswordWithToken(token.value, newPassword.value)
    if (data.success) {
      toastStore.addToast(t('auth.passwordResetSuccess'), 'success')
      router.push('/login')
    }
  } catch {
    // Error handled by global interceptor
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="p-8 relative h-full flex flex-col justify-center">
    <div class="absolute top-4 left-4">
      <router-link
        to="/login"
        class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
      >
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>

    <div v-if="!token" class="w-[80%] mx-auto text-center">
      <p class="text-gray-600 dark:text-gray-300">{{ t('auth.invalidResetLink') }}</p>
      <BaseButton variant="primary" class="mt-6 w-full" @click="router.push('/login')">
        {{ t('auth.login') }}
      </BaseButton>
    </div>

    <div v-else class="w-[80%] mx-auto space-y-6">
      <div class="text-center mb-8">
        <h2 class="text-2xl font-bold text-gray-900 dark:text-white">
          {{ t('auth.resetPasswordTitle') }}
        </h2>
      </div>

      <BaseInput
        id="new-password"
        v-model="newPassword"
        type="password"
        :placeholder="t('auth.newPassword')"
        :label="t('auth.newPassword')"
        hideLabel
      >
        <template #prefix>
          <Lock class="h-5 w-5 text-gray-400" />
        </template>
      </BaseInput>

      <BaseInput
        id="confirm-password"
        v-model="confirmPassword"
        type="password"
        :placeholder="t('auth.newPasswordConfirm')"
        :label="t('auth.newPasswordConfirm')"
        hideLabel
      >
        <template #prefix>
          <Lock class="h-5 w-5 text-gray-400" />
        </template>
      </BaseInput>

      <BaseButton
        type="button"
        variant="primary"
        class="w-full"
        :loading="isLoading"
        :disabled="isLoading"
        @click="handleResetPassword"
      >
        {{ t('auth.resetPassword') }}
      </BaseButton>
    </div>
  </div>
</template>

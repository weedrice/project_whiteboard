import { ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useAuthPasswordValidation } from '@/composables/useAuthPasswordValidation'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'

interface UsePasswordResetByTokenFlowOptions {
  token: Ref<string>
  newPassword: Ref<string>
  confirmPassword: Ref<string>
}

export function usePasswordResetByTokenFlow(options: UsePasswordResetByTokenFlowOptions) {
  const { t } = useI18n()
  const router = useRouter()
  const toastStore = useToastStore()
  const { validatePasswordPair } = useAuthPasswordValidation()
  const isLoading = ref(false)

  const resetPassword = async () => {
    if (!options.token.value) {
      toastStore.addToast(t('auth.invalidResetLink'), 'error')
      return
    }

    const passwordError = validatePasswordPair(options.newPassword.value, options.confirmPassword.value, {
      requirePassword: true,
      messages: {
        required: t('auth.placeholders.password'),
        invalid: t('auth.validation.passwordStrength'),
        mismatch: t('auth.passwordMismatch'),
      },
    })
    if (passwordError) {
      toastStore.addToast(passwordError, 'error')
      return
    }

    isLoading.value = true
    try {
      const { data } = await authApi.resetPasswordWithToken(options.token.value, options.newPassword.value)
      if (data.success) {
        toastStore.addToast(t('auth.passwordResetSuccess'), 'success')
        router.push('/login')
      }
    } catch (error: unknown) {
      const message = extractErrorMessage(error) || t('auth.verificationFailed')
      toastStore.addToast(message, 'error')
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    resetPassword,
  }
}

import { getCurrentScope, onScopeDispose, ref, type Ref } from 'vue'
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
  let requestRevision = 0
  let requestController: AbortController | null = null

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

    requestController?.abort()
    const controller = new AbortController()
    requestController = controller
    const revision = ++requestRevision
    const routeIdentity = router.currentRoute?.value.fullPath ?? window.location.href
    const isCurrent = () => requestRevision === revision
      && requestController === controller
      && !controller.signal.aborted
      && (router.currentRoute?.value.fullPath ?? window.location.href) === routeIdentity
    isLoading.value = true
    try {
      const { data } = await authApi.resetPasswordWithToken(
        options.token.value,
        options.newPassword.value,
        { signal: controller.signal },
      )
      if (isCurrent() && data.success) {
        toastStore.addToast(t('auth.passwordResetSuccess'), 'success')
        router.push('/login')
      }
    } catch (error: unknown) {
      if (!isCurrent()) return
      const message = extractErrorMessage(error) || t('auth.verificationFailed')
      toastStore.addToast(message, 'error')
    } finally {
      if (isCurrent()) isLoading.value = false
    }
  }

  if (getCurrentScope()) {
    onScopeDispose(() => {
      requestRevision += 1
      requestController?.abort()
      requestController = null
      isLoading.value = false
    })
  }

  return {
    isLoading,
    resetPassword,
  }
}

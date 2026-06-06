import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { useEmailVerificationState } from '@/composables/useEmailVerificationState'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { isValidEmail } from '@/utils/validation'
import type { VerificationPurpose, VerifyCodeResponse } from '@/api/auth'

export interface EmailVerificationFlowOptions {
  getEmail: () => string
  refreshProfile?: () => Promise<void>
  purpose?: VerificationPurpose | (() => VerificationPurpose)
  getCode?: () => string
  beforeSend?: (email: string) => Promise<void> | void
  afterSend?: (email: string) => Promise<void> | void
  afterVerify?: (context: {
    email: string
    verificationTicket: string
    response: VerifyCodeResponse
  }) => Promise<void> | void
  onSendError?: (error: unknown) => boolean | void
  onVerifyError?: (error: unknown) => boolean | void
  validateEmailFormat?: boolean
  useTimer?: boolean
  closeOnVerifySuccess?: boolean
  showVerifySuccessToast?: boolean
  emailRequiredMessage?: string
  onLoadingChange?: (loading: boolean) => void
}

export function useEmailVerificationFlow(options: EmailVerificationFlowOptions) {
  const { t } = useI18n()
  const authStore = useAuthStore()
  const toastStore = useToastStore()

  const isVerifyModalOpen = ref(false)
  const {
    verification: emailVerification,
    startTimer: startVerifyTimer,
    stopTimer: stopVerifyTimer,
    startResendCooldown: startVerifyResendCooldown,
    stopResendCooldown: stopVerifyResendCooldown,
    resetVerification,
    formatTime: formatVerifyTime
  } = useEmailVerificationState()

  const resolvePurpose = (): VerificationPurpose => {
    if (typeof options.purpose === 'function') {
      return options.purpose()
    }
    return options.purpose ?? 'CHANGE_EMAIL'
  }

  const shouldValidateEmailFormat = () => options.validateEmailFormat ?? resolvePurpose() === 'CHANGE_EMAIL'
  const shouldUseTimer = () => options.useTimer ?? true
  const shouldShowVerifySuccessToast = () => options.showVerifySuccessToast ?? true
  const shouldCloseOnVerifySuccess = () => options.closeOnVerifySuccess ?? resolvePurpose() === 'CHANGE_EMAIL'

  const setLoading = (loading: boolean) => {
    emailVerification.loading = loading
    options.onLoadingChange?.(loading)
  }

  function openVerifyModal() {
    resetVerification(options.getEmail())
    isVerifyModalOpen.value = true
  }

  function closeVerifyModal() {
    stopVerifyTimer()
    stopVerifyResendCooldown()
    isVerifyModalOpen.value = false
  }

  async function sendVerifyCode() {
    emailVerification.email = options.getEmail()
    const trimmed = emailVerification.email.trim()
    if (!trimmed) {
      toastStore.addToast(options.emailRequiredMessage ?? t('auth.emailRequired'), 'error')
      return
    }
    if (shouldValidateEmailFormat() && !isValidEmail(trimmed)) {
      toastStore.addToast(t('auth.validation.emailFormat'), 'error')
      return
    }

    setLoading(true)
    try {
      await options.beforeSend?.(trimmed)
      const { data } = await authApi.sendVerificationCode(trimmed, resolvePurpose())
      if (data.success) {
        emailVerification.code = ''
        emailVerification.verificationTicket = ''
        emailVerification.isVerified = false
        emailVerification.isCodeSent = true
        if (shouldUseTimer()) {
          startVerifyTimer()
          startVerifyResendCooldown()
        }
        await options.afterSend?.(trimmed)
        toastStore.addToast(t('auth.codeSent'), 'success')
      }
    } catch (err: unknown) {
      const handled = options.onSendError?.(err)
      if (!handled) {
        const message = extractErrorMessage(err) || t('auth.sendCodeFailed')
        toastStore.addToast(message, 'error')
      }
      emailVerification.resendCooldown = 0
      stopVerifyResendCooldown()
    } finally {
      setLoading(false)
    }
  }

  async function verifyEmailCode() {
    emailVerification.email = options.getEmail()
    const trimmed = emailVerification.email.trim()
    const code = options.getCode ? options.getCode().trim() : emailVerification.code.trim()
    if (!code || !trimmed) {
      toastStore.addToast(t('auth.codeInvalid'), 'error')
      return
    }

    if (shouldUseTimer() && emailVerification.timeLeft <= 0) {
      toastStore.addToast(t('auth.codeExpired'), 'error')
      return
    }

    setLoading(true)
    try {
      const verifyResponse = await authApi.verifyCode(trimmed, code, resolvePurpose())
      const response = verifyResponse.data.data
      if (!verifyResponse.data.success || !response?.verificationTicket) {
        throw new Error(t('auth.verificationFailed'))
      }

      emailVerification.verificationTicket = response.verificationTicket
      if (resolvePurpose() === 'CHANGE_EMAIL') {
        const { data } = await userApi.verifyEmail({
          email: trimmed,
          verificationTicket: emailVerification.verificationTicket
        })
        if (!data.success) {
          throw new Error(t('auth.verificationFailed'))
        }
        await Promise.all([
          options.refreshProfile?.() ?? Promise.resolve(),
          authStore.fetchUser()
        ])
      }

      await options.afterVerify?.({
        email: trimmed,
        verificationTicket: emailVerification.verificationTicket,
        response
      })

      emailVerification.isVerified = true
      if (shouldUseTimer()) {
        stopVerifyTimer()
      }
      if (shouldShowVerifySuccessToast()) {
        toastStore.addToast(t('auth.codeVerified'), 'success')
      }
      if (shouldCloseOnVerifySuccess()) {
        closeVerifyModal()
      }
    } catch (err: unknown) {
      const handled = options.onVerifyError?.(err)
      if (!handled) {
        const message = extractErrorMessage(err) || t('auth.verificationFailed')
        toastStore.addToast(message, 'error')
      }
    } finally {
      setLoading(false)
    }
  }

  return {
    isVerifyModalOpen,
    emailVerification,
    formatVerifyTime,
    isValidEmail,
    openVerifyModal,
    closeVerifyModal,
    sendVerifyCode,
    verifyEmailCode
  }
}

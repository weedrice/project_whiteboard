import { getCurrentScope, onScopeDispose, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { unwrapAxiosApiData } from '@/api/response'
import { userApi } from '@/api/user'
import { useEmailVerificationState } from '@/composables/useEmailVerificationState'
import { useLatestRequestGate } from '@/composables/useLatestAsyncTask'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { isValidEmail } from '@/utils/validation'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
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
    purpose: VerificationPurpose
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

  const shouldValidateEmailFormat = (purpose: VerificationPurpose) => options.validateEmailFormat ?? purpose === 'CHANGE_EMAIL'
  const shouldUseTimer = () => options.useTimer ?? true
  const shouldShowVerifySuccessToast = () => options.showVerifySuccessToast ?? true
  const shouldCloseOnVerifySuccess = (purpose: VerificationPurpose) => options.closeOnVerifySuccess ?? purpose === 'CHANGE_EMAIL'

  const setLoading = (loading: boolean) => {
    emailVerification.loading = loading
    options.onLoadingChange?.(loading)
  }

  const requestGate = useLatestRequestGate({
    captureContext: () => captureAuthSessionIntent(authStore),
    isContextCurrent: (intent) => isAuthSessionIntentCurrent(authStore, intent),
    onActiveChange: setLoading,
  })

  function openVerifyModal() {
    resetVerification(options.getEmail())
    isVerifyModalOpen.value = true
  }

  function closeVerifyModal() {
    cancelPendingRequests()
    resetVerification()
    isVerifyModalOpen.value = false
  }

  function cancelPendingRequests() {
    requestGate.cancel()
  }

  if (getCurrentScope()) {
    const stopSessionBoundary = subscribeAuthSessionBoundary(closeVerifyModal)
    onScopeDispose(() => {
      stopSessionBoundary()
      closeVerifyModal()
    })
  }

  async function sendVerifyCode() {
    const purpose = resolvePurpose()
    emailVerification.email = options.getEmail()
    const trimmed = emailVerification.email.trim()
    if (!trimmed) {
      toastStore.addToast(options.emailRequiredMessage ?? t('auth.emailRequired'), 'error')
      return
    }
    if (shouldValidateEmailFormat(purpose) && !isValidEmail(trimmed)) {
      toastStore.addToast(t('auth.validation.emailFormat'), 'error')
      return
    }

    const request = requestGate.start()
    try {
      await options.beforeSend?.(trimmed)
      if (!request.isCurrent()) return
      const { data } = await authApi.sendVerificationCode(trimmed, purpose, {
        signal: request.signal,
      })
      if (!request.isCurrent()) return
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
        if (!request.isCurrent()) return
        toastStore.addToast(t('auth.codeSent'), 'success')
      }
    } catch (err: unknown) {
      if (!request.isCurrent()) return
      const handled = options.onSendError?.(err)
      if (!handled) {
        const message = extractErrorMessage(err) || t('auth.sendCodeFailed')
        toastStore.addToast(message, 'error')
      }
      emailVerification.resendCooldown = 0
      stopVerifyResendCooldown()
    } finally {
      request.finish()
    }
  }

  async function verifyEmailCode() {
    const purpose = resolvePurpose()
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

    const request = requestGate.start()
    try {
      const verifyResponse = await authApi.verifyCode(trimmed, code, purpose, {
        signal: request.signal,
      })
      if (!request.isCurrent()) return
      const response = unwrapAxiosApiData(verifyResponse)
      if (!verifyResponse.data.success || !response?.verificationTicket) {
        throw new Error(t('auth.verificationFailed'))
      }

      emailVerification.verificationTicket = response.verificationTicket
      if (purpose === 'CHANGE_EMAIL') {
        const { data } = await userApi.verifyEmail({
          email: trimmed,
          verificationTicket: emailVerification.verificationTicket
        }, {
          signal: request.signal,
          skipGlobalErrorHandler: true,
        })
        if (!data.success) {
          throw new Error(t('auth.verificationFailed'))
        }
        if (!request.isCurrent()) return
        if (authStore.user) {
          authStore.user.isEmailVerified = true
        }
        await authStore.fetchUser()
        if (!request.isCurrent()) return
        await options.refreshProfile?.()
        if (!request.isCurrent()) return
      }

      await options.afterVerify?.({
        email: trimmed,
        purpose,
        verificationTicket: emailVerification.verificationTicket,
        response
      })
      if (!request.isCurrent()) return

      emailVerification.isVerified = true
      if (shouldUseTimer()) {
        stopVerifyTimer()
      }
      if (shouldShowVerifySuccessToast()) {
        toastStore.addToast(t('auth.codeVerified'), 'success')
      }
      if (shouldCloseOnVerifySuccess(purpose)) {
        closeVerifyModal()
      }
    } catch (err: unknown) {
      if (!request.isCurrent()) return
      const handled = options.onVerifyError?.(err)
      if (!handled) {
        const message = extractErrorMessage(err) || t('auth.verificationFailed')
        toastStore.addToast(message, 'error')
      }
    } finally {
      request.finish()
    }
  }

  return {
    isVerifyModalOpen,
    emailVerification,
    formatVerifyTime,
    isValidEmail,
    openVerifyModal,
    closeVerifyModal,
    cancelPendingRequests,
    sendVerifyCode,
    verifyEmailCode
  }
}

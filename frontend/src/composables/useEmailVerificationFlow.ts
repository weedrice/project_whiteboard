import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { useEmailVerificationState } from '@/composables/useEmailVerificationState'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { isValidEmail } from '@/utils/validation'

interface EmailVerificationFlowOptions {
  getEmail: () => string
  refreshProfile: () => Promise<void>
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
    const trimmed = emailVerification.email.trim()
    if (!trimmed) {
      toastStore.addToast(t('auth.emailRequired'), 'error')
      return
    }
    if (!isValidEmail(trimmed)) {
      toastStore.addToast(t('auth.validation.emailFormat'), 'error')
      return
    }

    emailVerification.loading = true
    try {
      const { data } = await authApi.sendVerificationCode(trimmed, 'CHANGE_EMAIL')
      if (data.success) {
        emailVerification.code = ''
        emailVerification.verificationTicket = ''
        emailVerification.isVerified = false
        emailVerification.isCodeSent = true
        startVerifyTimer()
        startVerifyResendCooldown()
        toastStore.addToast(t('auth.codeSent'), 'success')
      }
    } catch (err: unknown) {
      const message = extractErrorMessage(err) || t('auth.sendCodeFailed')
      toastStore.addToast(message, 'error')
      emailVerification.resendCooldown = 0
      stopVerifyResendCooldown()
    } finally {
      emailVerification.loading = false
    }
  }

  async function verifyEmailCode() {
    const trimmed = emailVerification.email.trim()
    if (!emailVerification.code || !trimmed) return

    if (emailVerification.timeLeft <= 0) {
      toastStore.addToast(t('auth.codeExpired'), 'error')
      return
    }

    emailVerification.loading = true
    try {
      const verifyResponse = await authApi.verifyCode(trimmed, emailVerification.code, 'CHANGE_EMAIL')
      if (!verifyResponse.data.success || !verifyResponse.data.data?.verificationTicket) {
        throw new Error(t('auth.verificationFailed'))
      }

      emailVerification.verificationTicket = verifyResponse.data.data.verificationTicket

      const { data } = await userApi.verifyEmail({
        email: trimmed,
        verificationTicket: emailVerification.verificationTicket
      })
      if (data.success) {
        emailVerification.isVerified = true
        stopVerifyTimer()
        toastStore.addToast(t('auth.codeVerified'), 'success')
        await Promise.all([
          options.refreshProfile(),
          authStore.fetchUser()
        ])
        closeVerifyModal()
      }
    } catch (err: unknown) {
      const message = extractErrorMessage(err) || t('auth.verificationFailed')
      toastStore.addToast(message, 'error')
    } finally {
      emailVerification.loading = false
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

import { onScopeDispose, reactive } from 'vue'

export interface EmailVerificationState {
  email: string
  code: string
  verificationTicket: string
  isCodeSent: boolean
  isVerified: boolean
  loading: boolean
  timeLeft: number
  resendCooldown: number
}

export interface EmailVerificationOptions {
  timerSeconds?: number
  resendCooldownSeconds?: number
}

export function useEmailVerificationState(options: EmailVerificationOptions = {}) {
  const timerSeconds = options.timerSeconds ?? 300
  const resendCooldownSeconds = options.resendCooldownSeconds ?? 60

  const verification = reactive<EmailVerificationState>({
    email: '',
    code: '',
    verificationTicket: '',
    isCodeSent: false,
    isVerified: false,
    loading: false,
    timeLeft: 0,
    resendCooldown: 0,
  })

  let timerInterval: ReturnType<typeof setInterval> | null = null
  let resendInterval: ReturnType<typeof setInterval> | null = null

  const stopTimer = () => {
    if (timerInterval) {
      clearInterval(timerInterval)
      timerInterval = null
    }
  }

  const startTimer = () => {
    stopTimer()
    verification.timeLeft = timerSeconds
    timerInterval = setInterval(() => {
      if (verification.timeLeft > 0) {
        verification.timeLeft--
      } else {
        stopTimer()
      }
    }, 1000)
  }

  const stopResendCooldown = () => {
    if (resendInterval) {
      clearInterval(resendInterval)
      resendInterval = null
    }
  }

  const startResendCooldown = () => {
    stopResendCooldown()
    verification.resendCooldown = resendCooldownSeconds
    resendInterval = setInterval(() => {
      if (verification.resendCooldown > 0) {
        verification.resendCooldown--
      } else {
        stopResendCooldown()
      }
    }, 1000)
  }

  const resetVerification = (email = '') => {
    verification.email = email
    verification.code = ''
    verification.verificationTicket = ''
    verification.isCodeSent = false
    verification.isVerified = false
    verification.loading = false
    verification.timeLeft = 0
    verification.resendCooldown = 0
    stopTimer()
    stopResendCooldown()
  }

  const formatTime = (seconds: number): string => {
    const minutes = Math.floor(seconds / 60)
    const remainingSeconds = seconds % 60
    return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
  }

  onScopeDispose(() => {
    stopTimer()
    stopResendCooldown()
  })

  return {
    verification,
    startTimer,
    stopTimer,
    startResendCooldown,
    stopResendCooldown,
    resetVerification,
    formatTime,
  }
}

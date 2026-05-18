import { reactive, onScopeDispose } from 'vue'

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

export function useEmailVerificationState(initialEmail = '') {
    const verification = reactive<EmailVerificationState>({
        email: initialEmail,
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

    function stopTimer() {
        if (timerInterval) {
            clearInterval(timerInterval)
            timerInterval = null
        }
    }

    function startTimer(seconds = 300) {
        stopTimer()
        verification.timeLeft = seconds
        timerInterval = setInterval(() => {
            if (verification.timeLeft > 0) {
                verification.timeLeft--
            } else {
                stopTimer()
            }
        }, 1000)
    }

    function stopResendCooldown() {
        if (resendInterval) {
            clearInterval(resendInterval)
            resendInterval = null
        }
    }

    function startResendCooldown(seconds = 60) {
        stopResendCooldown()
        verification.resendCooldown = seconds
        resendInterval = setInterval(() => {
            if (verification.resendCooldown > 0) {
                verification.resendCooldown--
            } else {
                stopResendCooldown()
            }
        }, 1000)
    }

    function reset(nextEmail = '') {
        verification.email = nextEmail
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

    function formatTime(seconds: number) {
        const m = Math.floor(seconds / 60)
        const s = seconds % 60
        return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
    }

    onScopeDispose(() => {
        stopTimer()
        stopResendCooldown()
    }, true)

    return {
        verification,
        startTimer,
        stopTimer,
        startResendCooldown,
        stopResendCooldown,
        reset,
        formatTime,
    }
}

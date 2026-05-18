import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useEmailVerificationState } from '../useEmailVerificationState'

describe('useEmailVerificationState', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('counts down verification time and stops at zero', () => {
        const { verification, startTimer } = useEmailVerificationState()

        startTimer(2)

        expect(verification.timeLeft).toBe(2)

        vi.advanceTimersByTime(1000)
        expect(verification.timeLeft).toBe(1)

        vi.advanceTimersByTime(1000)
        expect(verification.timeLeft).toBe(0)

        vi.advanceTimersByTime(1000)
        expect(verification.timeLeft).toBe(0)
    })

    it('counts down resend cooldown and stops at zero', () => {
        const { verification, startResendCooldown } = useEmailVerificationState()

        startResendCooldown(2)

        expect(verification.resendCooldown).toBe(2)

        vi.advanceTimersByTime(1000)
        expect(verification.resendCooldown).toBe(1)

        vi.advanceTimersByTime(1000)
        expect(verification.resendCooldown).toBe(0)

        vi.advanceTimersByTime(1000)
        expect(verification.resendCooldown).toBe(0)
    })

    it('resets email verification fields and active timers', () => {
        const {
            verification,
            startTimer,
            startResendCooldown,
            reset,
        } = useEmailVerificationState('old@example.com')

        verification.code = '123456'
        verification.verificationTicket = 'ticket'
        verification.isCodeSent = true
        verification.isVerified = true
        verification.loading = true
        startTimer(10)
        startResendCooldown(10)

        reset('next@example.com')

        expect(verification).toMatchObject({
            email: 'next@example.com',
            code: '',
            verificationTicket: '',
            isCodeSent: false,
            isVerified: false,
            loading: false,
            timeLeft: 0,
            resendCooldown: 0,
        })

        vi.advanceTimersByTime(1000)
        expect(verification.timeLeft).toBe(0)
        expect(verification.resendCooldown).toBe(0)
    })

    it('cleans up active intervals when the owner scope is disposed', () => {
        const scope = effectScope()
        const state = scope.run(() => {
            const emailVerification = useEmailVerificationState()
            emailVerification.startTimer(10)
            emailVerification.startResendCooldown(10)
            return emailVerification.verification
        })

        expect(state?.timeLeft).toBe(10)
        expect(state?.resendCooldown).toBe(10)

        scope.stop()
        vi.advanceTimersByTime(1000)

        expect(state?.timeLeft).toBe(10)
        expect(state?.resendCooldown).toBe(10)
    })

    it('formats time as mm:ss', () => {
        const { formatTime } = useEmailVerificationState()

        expect(formatTime(65)).toBe('01:05')
    })
})

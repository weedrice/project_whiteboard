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

  it('runs and stops the verification timer with configured seconds', () => {
    const scope = effectScope()
    let composable!: ReturnType<typeof useEmailVerificationState>

    scope.run(() => {
      composable = useEmailVerificationState({ timerSeconds: 3 })
    })

    composable.startTimer()
    expect(composable.verification.timeLeft).toBe(3)

    vi.advanceTimersByTime(1000)
    expect(composable.verification.timeLeft).toBe(2)

    composable.stopTimer()
    vi.advanceTimersByTime(1000)
    expect(composable.verification.timeLeft).toBe(2)

    scope.stop()
  })

  it('allows per-call timer seconds overrides and stops at zero', () => {
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

  it('runs and resets the resend cooldown', () => {
    const scope = effectScope()
    let composable!: ReturnType<typeof useEmailVerificationState>

    scope.run(() => {
      composable = useEmailVerificationState({ resendCooldownSeconds: 2 })
    })

    composable.startResendCooldown()
    expect(composable.verification.resendCooldown).toBe(2)

    vi.advanceTimersByTime(1000)
    expect(composable.verification.resendCooldown).toBe(1)

    composable.resetVerification('user@example.com')
    expect(composable.verification).toMatchObject({
      email: 'user@example.com',
      code: '',
      verificationTicket: '',
      isCodeSent: false,
      isVerified: false,
      loading: false,
      timeLeft: 0,
      resendCooldown: 0,
    })

    vi.advanceTimersByTime(1000)
    expect(composable.verification.resendCooldown).toBe(0)

    scope.stop()
  })

  it('supports the reset alias and string initial email compatibility', () => {
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

  it('formats seconds as mm:ss', () => {
    const { formatTime } = useEmailVerificationState()

    expect(formatTime(65)).toBe('01:05')
    expect(formatTime(0)).toBe('00:00')
  })
})

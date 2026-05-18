import { describe, expect, it, vi, afterEach } from 'vitest'
import { effectScope } from 'vue'
import { useEmailVerificationState } from '../useEmailVerificationState'

describe('useEmailVerificationState', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('runs and stops the verification timer', () => {
    vi.useFakeTimers()
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

  it('runs and resets the resend cooldown', () => {
    vi.useFakeTimers()
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

  it('formats seconds as mm:ss', () => {
    const scope = effectScope()
    let composable!: ReturnType<typeof useEmailVerificationState>

    scope.run(() => {
      composable = useEmailVerificationState()
    })

    expect(composable.formatTime(65)).toBe('01:05')
    expect(composable.formatTime(0)).toBe('00:00')

    scope.stop()
  })
})

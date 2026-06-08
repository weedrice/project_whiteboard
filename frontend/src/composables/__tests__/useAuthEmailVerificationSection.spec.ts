import { describe, expect, it, vi } from 'vitest'
import { useAuthEmailVerificationSection } from '../useAuthEmailVerificationSection'

const flowMocks = vi.hoisted(() => ({
  sendVerifyCode: vi.fn(),
  verifyEmailCode: vi.fn(),
  capturedOptions: null as Record<string, unknown> | null,
}))

vi.mock('../useEmailVerificationFlow', () => ({
  useEmailVerificationFlow: (options: Record<string, unknown>) => {
    flowMocks.capturedOptions = options
    return {
      sendVerifyCode: flowMocks.sendVerifyCode,
      verifyEmailCode: flowMocks.verifyEmailCode,
    }
  },
}))

describe('useAuthEmailVerificationSection', () => {
  it('adapts email verification flow options into section props and handlers', () => {
    const { sectionProps, sendVerifyCode, verifyEmailCode } = useAuthEmailVerificationSection({
      t: (key: string) => `t:${key}`,
      idPrefix: 'forgot-password',
      verifyLabelKey: 'auth.sendResetLink',
      codeSent: () => true,
      loading: () => false,
      emailDisabled: () => true,
      getEmail: () => 'user@example.com',
      getCode: () => '123456',
      purpose: 'PASSWORD_RESET',
    })

    expect(sectionProps.value).toMatchObject({
      idPrefix: 'forgot-password',
      layout: 'stacked',
      codeSent: true,
      loading: false,
      emailDisabled: true,
      emailLabel: 't:auth.email',
      verifyLabel: 't:auth.sendResetLink',
    })
    expect(flowMocks.capturedOptions?.purpose).toBe('PASSWORD_RESET')
    expect(sendVerifyCode).toBe(flowMocks.sendVerifyCode)
    expect(verifyEmailCode).toBe(flowMocks.verifyEmailCode)
  })
})

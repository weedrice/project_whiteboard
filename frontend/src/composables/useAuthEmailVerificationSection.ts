import { computed, type ComputedRef } from 'vue'
import {
  useEmailVerificationFlow,
  type EmailVerificationFlowOptions,
} from '@/composables/useEmailVerificationFlow'

type Translate = (key: string) => string

export type AuthEmailVerificationSectionOptions = EmailVerificationFlowOptions & {
  t: Translate
  idPrefix: string
  codeSent: () => boolean
  loading: () => boolean
  verifyLabelKey: string
  layout?: 'stacked' | 'inline'
  emailDisabled?: () => boolean
}

export function useAuthEmailVerificationSection(options: AuthEmailVerificationSectionOptions) {
  const { sendVerifyCode, verifyEmailCode, cancelPendingRequests } = useEmailVerificationFlow(options)

  const sectionProps: ComputedRef<{
    idPrefix: string
    layout: 'stacked' | 'inline'
    loading: boolean
    codeSent: boolean
    emailDisabled: boolean
    emailLabel: string
    emailPlaceholder: string
    codeLabel: string
    sendLabel: string
    resendLabel: string
    verifyLabel: string
  }> = computed(() => ({
    idPrefix: options.idPrefix,
    layout: options.layout ?? 'stacked',
    loading: options.loading(),
    codeSent: options.codeSent(),
    emailDisabled: options.emailDisabled?.() ?? false,
    emailLabel: options.t('auth.email'),
    emailPlaceholder: options.t('auth.placeholders.email'),
    codeLabel: options.t('auth.codePlaceholder'),
    sendLabel: options.t('auth.sendCode'),
    resendLabel: options.t('auth.resendCode'),
    verifyLabel: options.t(options.verifyLabelKey),
  }))

  return {
    sectionProps,
    sendVerifyCode,
    verifyEmailCode,
    cancelPendingRequests,
  }
}

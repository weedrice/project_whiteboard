import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useAuthPasswordValidation } from '@/composables/useAuthPasswordValidation'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { handleDeletedAccountRedirect } from '@/utils/authRedirect'

interface UsePasswordResetByVerificationFlowOptions {
    getEmail: () => string
    getVerificationTicket: () => string
    getNewPassword: () => string
    getConfirmPassword: () => string
    onLoadingChange?: (loading: boolean) => void
    onVerified?: (verificationTicket: string) => void
}

export function usePasswordResetByVerificationFlow(options: UsePasswordResetByVerificationFlowOptions) {
    const { t } = useI18n()
    const router = useRouter()
    const toastStore = useToastStore()
    const { validatePasswordPair } = useAuthPasswordValidation()

    const completeVerification = (verificationTicket: string) => {
        options.onVerified?.(verificationTicket)
        toastStore.addToast(t('auth.codeVerified'), 'success')
    }

    const resetPassword = async () => {
        const passwordError = validatePasswordPair(options.getNewPassword(), options.getConfirmPassword(), {
            messages: {
                invalid: t('auth.validation.passwordStrength'),
                mismatch: t('auth.passwordMismatch')
            }
        })
        if (passwordError) {
            toastStore.addToast(passwordError, 'error')
            return
        }

        const email = options.getEmail().trim()
        options.onLoadingChange?.(true)
        try {
            const { data } = await authApi.resetPassword({
                email,
                verificationTicket: options.getVerificationTicket(),
                newPassword: options.getNewPassword()
            })
            if (data.success) {
                toastStore.addToast(t('auth.passwordResetSuccess'), 'success')
                router.push('/login')
            }
        } catch (error: unknown) {
            if (handleDeletedAccountRedirect(error, {
                email,
                t,
                addToast: (message, type) => toastStore.addToast(message, type),
                push: (to) => router.push(to),
            })) {
                return
            } else {
                const message = extractErrorMessage(error) || t('auth.verificationFailed')
                toastStore.addToast(message, 'error')
            }
        } finally {
            options.onLoadingChange?.(false)
        }
    }

    return {
        completeVerification,
        resetPassword
    }
}

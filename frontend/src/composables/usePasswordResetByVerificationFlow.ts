import { getCurrentScope, onScopeDispose } from 'vue'
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
    let requestRevision = 0
    let requestController: AbortController | null = null

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
        requestController?.abort()
        const controller = new AbortController()
        requestController = controller
        const revision = ++requestRevision
        const routeIdentity = router.currentRoute?.value.fullPath ?? window.location.href
        const isCurrent = () => requestRevision === revision
            && requestController === controller
            && !controller.signal.aborted
            && (router.currentRoute?.value.fullPath ?? window.location.href) === routeIdentity
        options.onLoadingChange?.(true)
        try {
            const { data } = await authApi.resetPassword({
                email,
                verificationTicket: options.getVerificationTicket(),
                newPassword: options.getNewPassword()
            }, { signal: controller.signal })
            if (isCurrent() && data.success) {
                toastStore.addToast(t('auth.passwordResetSuccess'), 'success')
                router.push('/login')
            }
        } catch (error: unknown) {
            if (!isCurrent()) return
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
            if (isCurrent()) options.onLoadingChange?.(false)
        }
    }

    if (getCurrentScope()) {
        onScopeDispose(() => {
            requestRevision += 1
            requestController?.abort()
            requestController = null
            options.onLoadingChange?.(false)
        })
    }

    return {
        completeVerification,
        resetPassword
    }
}

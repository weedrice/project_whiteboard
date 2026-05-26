import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'

interface UseFindIdFlowOptions {
    getEmail: () => string
    onLoadingChange?: (loading: boolean) => void
    onSuccess: (context: {
        loginId: string
        verificationTicket: string
    }) => void
}

export function useFindIdFlow(options: UseFindIdFlowOptions) {
    const { t } = useI18n()
    const router = useRouter()
    const toastStore = useToastStore()

    const redirectDeletedUserToSignup = (email: string) => {
        toastStore.addToast(t('auth.userDeleted'), 'info')
        router.push(`/signup?email=${encodeURIComponent(email)}`)
    }

    const findId = async (verificationTicket: string) => {
        const email = options.getEmail().trim()
        options.onLoadingChange?.(true)
        try {
            const { data } = await authApi.findId(email, verificationTicket)
            if (data.success) {
                options.onSuccess({
                    loginId: data.data.loginId,
                    verificationTicket
                })
                toastStore.addToast(t('auth.codeVerified'), 'success')
            }
        } catch (error: unknown) {
            if (axios.isAxiosError(error) && error.response?.data?.error?.code === 'A009') {
                redirectDeletedUserToSignup(email)
            } else {
                const message = extractErrorMessage(error) || t('auth.verificationFailed')
                toastStore.addToast(message, 'error')
            }
        } finally {
            options.onLoadingChange?.(false)
        }
    }

    return {
        findId
    }
}

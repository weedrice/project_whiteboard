import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { unwrapApiData } from '@/api/response'
import { useLatestRequestGate } from '@/composables/useLatestAsyncTask'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { handleDeletedAccountRedirect } from '@/utils/authRedirect'

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
    const requestGate = useLatestRequestGate({
        onActiveChange: options.onLoadingChange,
    })
    const cancelPendingRequests = requestGate.cancel

    const findId = async (verificationTicket: string) => {
        const email = options.getEmail().trim()
        const request = requestGate.start()
        try {
            const { data } = await authApi.findId(email, verificationTicket, {
                signal: request.signal,
            })
            if (request.isCurrent() && data.success) {
                const result = unwrapApiData(data)
                options.onSuccess({
                    loginId: result.loginId,
                    verificationTicket
                })
                toastStore.addToast(t('auth.codeVerified'), 'success')
            }
        } catch (error: unknown) {
            if (!request.isCurrent()) return
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
            request.finish()
        }
    }

    return {
        findId,
        cancelPendingRequests,
    }
}

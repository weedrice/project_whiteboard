import { getCurrentScope, onScopeDispose } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { unwrapApiData } from '@/api/response'
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
    let requestRevision = 0
    let requestController: AbortController | null = null

    const cancelPendingRequests = () => {
        requestRevision++
        requestController?.abort()
        requestController = null
        options.onLoadingChange?.(false)
    }

    const findId = async (verificationTicket: string) => {
        const email = options.getEmail().trim()
        requestController?.abort()
        const controller = new AbortController()
        requestController = controller
        const revision = ++requestRevision
        const isCurrent = () => requestRevision === revision
            && requestController === controller
            && !controller.signal.aborted
        options.onLoadingChange?.(true)
        try {
            const { data } = await authApi.findId(email, verificationTicket, {
                signal: controller.signal,
            })
            if (isCurrent() && data.success) {
                const result = unwrapApiData(data)
                options.onSuccess({
                    loginId: result.loginId,
                    verificationTicket
                })
                toastStore.addToast(t('auth.codeVerified'), 'success')
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
            if (isCurrent()) {
                requestController = null
                options.onLoadingChange?.(false)
            }
        }
    }

    if (getCurrentScope()) {
        onScopeDispose(cancelPendingRequests)
    }

    return {
        findId,
        cancelPendingRequests,
    }
}

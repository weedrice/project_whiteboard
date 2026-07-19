import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/features/admin/useAdmin'
import { useToastStore } from '@/stores/toast'
import type { ErrorLogDetail, ErrorLogListItem } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'

export function useErrorLogDetailModal() {
    const { t } = useI18n()
    const toastStore = useToastStore()
    const authStore = useAuthStore()
    const { useErrorLog, useResolveErrorLog } = useAdmin()
    const { mutateAsync: fetchErrorLogDetail } = useErrorLog()
    const { mutateAsync: resolveErrorLog } = useResolveErrorLog()

    const isDetailModalOpen = ref(false)
    const selectedLog = ref<ErrorLogDetail | null>(null)
    const isResolveModalOpen = ref(false)
    const resolveTargetLog = ref<ErrorLogListItem | ErrorLogDetail | null>(null)
    const resolveMemo = ref('')
    const isResolving = ref(false)
    let detailRevision = 0
    let resolveRevision = 0

    async function openDetailModal(log: ErrorLogListItem) {
        const revision = ++detailRevision
        const intent = captureAuthSessionIntent(authStore)
        const errorLogId = log.errorLogId
        selectedLog.value = null
        isDetailModalOpen.value = false
        try {
            const detail = await fetchErrorLogDetail(errorLogId)
            if (revision !== detailRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
            selectedLog.value = detail
            isDetailModalOpen.value = true
        } catch {
            if (revision !== detailRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
            toastStore.addToast(t('common.messages.error'), 'error')
        }
    }

    function closeDetailModal() {
        detailRevision += 1
        isDetailModalOpen.value = false
        selectedLog.value = null
    }

    function openResolveModal(log: ErrorLogListItem | ErrorLogDetail) {
        resolveRevision += 1
        resolveTargetLog.value = log
        resolveMemo.value = ''
        isResolving.value = false
        isResolveModalOpen.value = true
    }

    function closeResolveModal() {
        resolveRevision += 1
        isResolveModalOpen.value = false
        resolveTargetLog.value = null
        resolveMemo.value = ''
        isResolving.value = false
    }

    async function handleResolve() {
        if (!resolveTargetLog.value || isResolving.value) return

        const revision = resolveRevision
        const intent = captureAuthSessionIntent(authStore)
        const errorLogId = resolveTargetLog.value.errorLogId
        const memo = resolveMemo.value.trim()
        isResolving.value = true

        try {
            await resolveErrorLog({
                errorLogId,
                data: memo ? { memo } : undefined
            })
            if (revision !== resolveRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
            toastStore.addToast(t('admin.errorLogs.messages.resolved'), 'success')
            closeResolveModal()
        } catch {
            // Error handled globally
        } finally {
            if (revision === resolveRevision && isAuthSessionIntentCurrent(authStore, intent)) {
                isResolving.value = false
            }
        }
    }

    async function copyStackTrace() {
        const stackTrace = selectedLog.value?.stackTrace
        if (!stackTrace) return
        if (typeof navigator === 'undefined' || typeof navigator.clipboard?.writeText !== 'function') {
            toastStore.addToast(t('admin.errorLogs.messages.stackTraceCopyFailed'), 'error')
            return
        }

        try {
            await navigator.clipboard.writeText(stackTrace)
            toastStore.addToast(t('admin.errorLogs.messages.stackTraceCopied'), 'success')
        } catch {
            toastStore.addToast(t('admin.errorLogs.messages.stackTraceCopyFailed'), 'error')
        }
    }

    return {
        closeDetailModal,
        closeResolveModal,
        copyStackTrace,
        handleResolve,
        isDetailModalOpen,
        isResolving,
        isResolveModalOpen,
        openDetailModal,
        openResolveModal,
        resolveMemo,
        resolveTargetLog,
        selectedLog
    }
}

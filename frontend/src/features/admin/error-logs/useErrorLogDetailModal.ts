import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/features/admin/useAdmin'
import { useToastStore } from '@/stores/toast'
import type { ErrorLogDetail, ErrorLogListItem } from '@/types'
import { useAuthStore } from '@/stores/auth'

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

    async function openDetailModal(log: ErrorLogListItem) {
        const sessionGeneration = authStore.sessionGeneration
        try {
            const detail = await fetchErrorLogDetail(log.errorLogId)
            if (sessionGeneration !== authStore.sessionGeneration) return
            selectedLog.value = detail
            isDetailModalOpen.value = true
        } catch {
            toastStore.addToast(t('common.messages.error'), 'error')
        }
    }

    function closeDetailModal() {
        isDetailModalOpen.value = false
        selectedLog.value = null
    }

    function openResolveModal(log: ErrorLogListItem | ErrorLogDetail) {
        resolveTargetLog.value = log
        resolveMemo.value = ''
        isResolveModalOpen.value = true
    }

    function closeResolveModal() {
        isResolveModalOpen.value = false
        resolveTargetLog.value = null
        resolveMemo.value = ''
    }

    async function handleResolve() {
        if (!resolveTargetLog.value) return

        const memo = resolveMemo.value.trim()

        try {
            await resolveErrorLog({
                errorLogId: resolveTargetLog.value.errorLogId,
                data: memo ? { memo } : undefined
            })
            toastStore.addToast(t('admin.errorLogs.messages.resolved'), 'success')
            closeResolveModal()
        } catch {
            // Error handled globally
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
        isResolveModalOpen,
        openDetailModal,
        openResolveModal,
        resolveMemo,
        resolveTargetLog,
        selectedLog
    }
}

import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { useErrorLogDetailModal } from '../useErrorLogDetailModal'
import type { ErrorLogDetail } from '@/types'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    fetchErrorLogDetail: vi.fn(),
    resolveErrorLog: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast,
    }),
}))

vi.mock('@/features/admin/useAdmin', () => ({
    useAdmin: () => ({
        useErrorLog: () => ({ mutateAsync: mocks.fetchErrorLogDetail }),
        useResolveErrorLog: () => ({ mutateAsync: mocks.resolveErrorLog }),
    }),
}))

function mountErrorLogDetailModal() {
    let modal!: ReturnType<typeof useErrorLogDetailModal>

    mount(defineComponent({
        setup() {
            modal = useErrorLogDetailModal()
            return () => h('div')
        },
    }))

    return modal
}

describe('useErrorLogDetailModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it('copies stack trace when clipboard API is available', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        vi.stubGlobal('navigator', {
            clipboard: { writeText },
        })
        const modal = mountErrorLogDetailModal()
        modal.selectedLog.value = { errorLogId: 1, stackTrace: 'stack' } as ErrorLogDetail

        await modal.copyStackTrace()

        expect(writeText).toHaveBeenCalledWith('stack')
        expect(mocks.addToast).toHaveBeenCalledWith('admin.errorLogs.messages.stackTraceCopied', 'success')
    })

    it('shows failure toast when clipboard API is unavailable', async () => {
        vi.stubGlobal('navigator', {})
        const modal = mountErrorLogDetailModal()
        modal.selectedLog.value = { errorLogId: 1, stackTrace: 'stack' } as ErrorLogDetail

        await modal.copyStackTrace()

        expect(mocks.addToast).toHaveBeenCalledWith('admin.errorLogs.messages.stackTraceCopyFailed', 'error')
    })

    it('resolves a log with trimmed memo', async () => {
        const modal = mountErrorLogDetailModal()
        modal.openResolveModal({ errorLogId: 7 } as ErrorLogDetail)
        modal.resolveMemo.value = '  checked and fixed  '

        await modal.handleResolve()

        expect(mocks.resolveErrorLog).toHaveBeenCalledWith({
            errorLogId: 7,
            data: { memo: 'checked and fixed' },
        })
        expect(mocks.addToast).toHaveBeenCalledWith('admin.errorLogs.messages.resolved', 'success')
        expect(modal.isResolveModalOpen.value).toBe(false)
    })

    it('omits blank resolve memo after trimming', async () => {
        const modal = mountErrorLogDetailModal()
        modal.openResolveModal({ errorLogId: 7 } as ErrorLogDetail)
        modal.resolveMemo.value = '   '

        await modal.handleResolve()

        expect(mocks.resolveErrorLog).toHaveBeenCalledWith({
            errorLogId: 7,
            data: undefined,
        })
    })

    it('skips resolve when there is no target log', async () => {
        const modal = mountErrorLogDetailModal()

        await modal.handleResolve()

        expect(mocks.resolveErrorLog).not.toHaveBeenCalled()
    })
})

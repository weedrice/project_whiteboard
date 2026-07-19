import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { useErrorLogDetailModal } from '../useErrorLogDetailModal'
import type { ErrorLogDetail } from '@/types'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    fetchErrorLogDetail: vi.fn(),
    resolveErrorLog: vi.fn(),
    authStore: { sessionGeneration: 0 },
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

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => mocks.authStore,
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
        mocks.authStore.sessionGeneration = 0
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

    it('ignores a detail response after the authentication generation changes', async () => {
        const deferred = createDeferred<ErrorLogDetail>()
        mocks.fetchErrorLogDetail.mockReturnValueOnce(deferred.promise)
        const modal = mountErrorLogDetailModal()

        const opening = modal.openDetailModal({ errorLogId: 7 } as ErrorLogDetail)
        mocks.authStore.sessionGeneration += 1
        deferred.resolve({ errorLogId: 7, stackTrace: 'old session' } as ErrorLogDetail)
        await opening

        expect(modal.selectedLog.value).toBeNull()
        expect(modal.isDetailModalOpen.value).toBe(false)
    })

    it('keeps the latest selected log when detail responses arrive out of order', async () => {
        const first = createDeferred<ErrorLogDetail>()
        const second = createDeferred<ErrorLogDetail>()
        mocks.fetchErrorLogDetail
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise)
        const modal = mountErrorLogDetailModal()

        const openingFirst = modal.openDetailModal({ errorLogId: 1 } as ErrorLogDetail)
        const openingSecond = modal.openDetailModal({ errorLogId: 2 } as ErrorLogDetail)
        second.resolve({ errorLogId: 2, stackTrace: 'latest' } as ErrorLogDetail)
        await openingSecond
        first.resolve({ errorLogId: 1, stackTrace: 'stale' } as ErrorLogDetail)
        await openingFirst

        expect(modal.selectedLog.value?.errorLogId).toBe(2)
        expect(modal.isDetailModalOpen.value).toBe(true)
    })

    it('prevents duplicate resolve submissions while one is pending', async () => {
        const deferred = createDeferred<void>()
        mocks.resolveErrorLog.mockReturnValueOnce(deferred.promise)
        const modal = mountErrorLogDetailModal()
        modal.openResolveModal({ errorLogId: 7 } as ErrorLogDetail)

        const firstResolve = modal.handleResolve()
        const duplicateResolve = modal.handleResolve()

        expect(modal.isResolving.value).toBe(true)
        expect(mocks.resolveErrorLog).toHaveBeenCalledOnce()
        deferred.resolve()
        await Promise.all([firstResolve, duplicateResolve])
        expect(modal.isResolving.value).toBe(false)
    })
})

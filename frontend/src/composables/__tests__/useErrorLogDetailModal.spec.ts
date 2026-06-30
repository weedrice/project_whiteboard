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

vi.mock('@/composables/useAdmin', () => ({
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
})

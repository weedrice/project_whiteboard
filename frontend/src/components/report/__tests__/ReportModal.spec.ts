import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import ReportModal from '../ReportModal.vue'
import { BaseButtonStub, BaseModalStub, flushAll, getButtonByText, identityT } from '@/test/vue-test-helpers'

const mocks = vi.hoisted(() => ({
    reportUser: vi.fn(),
    addToast: vi.fn(),
    loggerError: vi.fn(),
}))

vi.mock('@/api/report', () => ({
    reportApi: {
        reportUser: mocks.reportUser,
    },
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast,
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: mocks.loggerError,
    },
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: identityT,
    }),
}))

const BaseTextareaStub = defineComponent({
    props: {
        modelValue: String,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('textarea', {
            value: props.modelValue,
            onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
        })
    },
})

const mountModal = () => mount(ReportModal, {
    props: {
        isOpen: true,
        userId: 9,
        displayName: '신고 대상',
    },
    global: {
        mocks: {
            $t: identityT,
        },
        stubs: {
            BaseModal: BaseModalStub,
            BaseButton: BaseButtonStub,
            BaseTextarea: BaseTextareaStub,
        },
    },
})

describe('ReportModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('shows a warning and skips the request when reason is blank', async () => {
        const wrapper = mountModal()

        await wrapper.get('textarea').setValue('   ')
        await getButtonByText(wrapper, 'common.report').trigger('click')

        expect(mocks.addToast).toHaveBeenCalledWith('report.inputReason', 'warning')
        expect(mocks.reportUser).not.toHaveBeenCalled()
    })

    it('ignores repeated reports while a report request is pending', async () => {
        let resolveReport: (value: unknown) => void = () => undefined
        mocks.reportUser.mockReturnValue(new Promise((resolve) => {
            resolveReport = resolve
        }))

        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('신고 사유')

        const reportButton = getButtonByText(wrapper, 'common.report')
        await reportButton.trigger('click')
        await reportButton.trigger('click')

        expect(mocks.reportUser).toHaveBeenCalledTimes(1)
        expect(mocks.reportUser).toHaveBeenCalledWith(9, '신고 사유', '', { skipGlobalErrorHandler: true })

        resolveReport({ data: { success: true } })
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
        expect(mocks.addToast).toHaveBeenCalledWith('report.reportSuccess', 'success')
    })

    it('shows an error toast and keeps the modal open when the request fails', async () => {
        mocks.reportUser.mockRejectedValue(new Error('failed'))

        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('신고 사유')
        await getButtonByText(wrapper, 'common.report').trigger('click')
        await flushAll()

        expect(wrapper.emitted('close')).toBeUndefined()
        expect(mocks.loggerError).toHaveBeenCalled()
        expect(mocks.addToast).toHaveBeenCalledWith('report.reportFailed', 'error')
    })
})

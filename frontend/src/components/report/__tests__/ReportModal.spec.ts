import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import ReportModal from '../ReportModal.vue'
import { BaseButtonStub, BaseModalStub, flushAll, getButtonByText, identityT } from '@/test/vue-test-helpers'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast,
    }),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: identityT,
    }),
}))

const BaseTextareaStub = defineComponent({
    props: {
        modelValue: String,
        maxlength: [String, Number],
        disabled: Boolean,
        error: String,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('textarea', {
            value: props.modelValue,
            maxlength: props.maxlength,
            disabled: props.disabled,
            'data-error': props.error,
            onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
        })
    },
})

const mountModal = (submit = vi.fn(async () => true)) => mount(ReportModal, {
    props: {
        isOpen: true,
        targetIdentity: 'user:3',
        targetText: '신고 대상',
        submit,
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

    it('shows a warning and skips submit when reason is blank', async () => {
        const submit = vi.fn(async () => true)
        const wrapper = mountModal(submit)

        await wrapper.get('textarea').setValue('   ')
        await getButtonByText(wrapper, 'common.report').trigger('click')

        expect(mocks.addToast).toHaveBeenCalledWith('report.inputReason', 'warning')
        expect(submit).not.toHaveBeenCalled()
    })

    it('ignores repeated submits while a request is pending', async () => {
        let resolveSubmit: (value: boolean) => void = () => undefined
        const submit = vi.fn(() => new Promise<boolean>((resolve) => {
            resolveSubmit = resolve
        }))

        const wrapper = mountModal(submit)
        await wrapper.get('textarea').setValue('신고 사유')

        const reportButton = getButtonByText(wrapper, 'common.report')
        await reportButton.trigger('click')
        await reportButton.trigger('click')

        expect(submit).toHaveBeenCalledTimes(1)
        expect(submit).toHaveBeenCalledWith('신고 사유')

        resolveSubmit(true)
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('keeps the modal open when submit returns false', async () => {
        const submit = vi.fn(async () => false)
        const wrapper = mountModal(submit)

        await wrapper.get('textarea').setValue('신고 사유')
        await getButtonByText(wrapper, 'common.report').trigger('click')
        await flushAll()

        expect(submit).toHaveBeenCalledWith('신고 사유')
        expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('enforces the 1,000 character report reason limit', async () => {
        const submit = vi.fn(async () => true)
        const wrapper = mountModal(submit)

        expect(wrapper.get('textarea').attributes('maxlength')).toBe('1000')
        await wrapper.get('textarea').setValue('x'.repeat(1001))
        await getButtonByText(wrapper, 'common.report').trigger('click')

        expect(submit).not.toHaveBeenCalled()
        expect(mocks.addToast).toHaveBeenCalledWith('report.reasonTooLong', 'warning')
        expect(wrapper.get('textarea').attributes('data-error')).toBe('report.reasonTooLong')
    })

    it('resets the draft and ignores a delayed result after target identity changes', async () => {
        let resolveSubmit: (value: boolean) => void = () => undefined
        const submit = vi.fn(() => new Promise<boolean>((resolve) => {
            resolveSubmit = resolve
        }))
        const wrapper = mountModal(submit)
        await wrapper.get('textarea').setValue('이전 대상 신고')
        await getButtonByText(wrapper, 'common.report').trigger('click')

        await wrapper.setProps({ targetIdentity: 'user:4', targetText: '새 신고 대상' })
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')

        resolveSubmit(true)
        await flushAll()
        expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('does not let a closed report complete a reopened modal', async () => {
        let resolveSubmit: (value: boolean) => void = () => undefined
        const submit = vi.fn(() => new Promise<boolean>((resolve) => {
            resolveSubmit = resolve
        }))
        const wrapper = mountModal(submit)
        await wrapper.get('textarea').setValue('닫기 전 신고')
        await getButtonByText(wrapper, 'common.report').trigger('click')

        await getButtonByText(wrapper, 'common.cancel').trigger('click')
        await wrapper.setProps({ isOpen: false })
        await wrapper.setProps({ isOpen: true })
        await wrapper.get('textarea').setValue('새 신고')
        resolveSubmit(true)
        await flushAll()

        expect(wrapper.emitted('close')).toHaveLength(1)
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('새 신고')
    })
})

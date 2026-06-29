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
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('textarea', {
            value: props.modelValue,
            onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
        })
    },
})

const mountModal = (submit = vi.fn(async () => true)) => mount(ReportModal, {
    props: {
        isOpen: true,
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
})

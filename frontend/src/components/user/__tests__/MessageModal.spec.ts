import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import MessageModal from '../MessageModal.vue'
import { BaseButtonStub, BaseModalStub, flushAll, getButtonByText, identityT } from '@/test/vue-test-helpers'

const mocks = vi.hoisted(() => ({
    sendMessage: vi.fn(),
    addToast: vi.fn(),
    loggerError: vi.fn(),
}))

vi.mock('@/api/message', () => ({
    BLOCKED_BY_USER_CODE: 'BLOCKED_BY_USER',
    messageApi: {
        sendMessage: mocks.sendMessage,
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

vi.mock('@/utils/errorHandler', () => ({
    extractErrorResponse: (error: { response?: { data?: unknown } }) => error.response?.data ?? null,
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
        error: String,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('textarea', {
            value: props.modelValue,
            maxlength: props.maxlength,
            'data-error': props.error,
            onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
        })
    },
})

const mountModal = () => mount(MessageModal, {
    props: {
        isOpen: true,
        userId: 3,
        displayName: '받는 사람',
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

describe('MessageModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('shows a warning and skips the request when content is blank', async () => {
        const wrapper = mountModal()

        await wrapper.get('textarea').setValue('   ')
        await getButtonByText(wrapper, 'common.send').trigger('click')

        expect(mocks.addToast).toHaveBeenCalledWith('user.message.inputContent', 'warning')
        expect(mocks.sendMessage).not.toHaveBeenCalled()
    })

    it('applies the 5,000 character input limit', () => {
        const wrapper = mountModal()

        expect(wrapper.get('textarea').attributes('maxlength')).toBe('5000')
        expect(wrapper.text()).toContain('user.message.contentLength')
    })

    it('ignores repeated sends while a send request is pending', async () => {
        let resolveSend: (value: unknown) => void = () => undefined
        mocks.sendMessage.mockReturnValue(new Promise((resolve) => {
            resolveSend = resolve
        }))

        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('안녕하세요')

        const sendButton = getButtonByText(wrapper, 'common.send')
        await sendButton.trigger('click')
        await sendButton.trigger('click')

        expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
        expect(mocks.sendMessage).toHaveBeenCalledWith(3, '안녕하세요', {
            skipGlobalErrorHandler: true,
            signal: expect.any(AbortSignal),
        })

        resolveSend({ data: { success: true } })
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('shows the blocked-user message when the API returns the block error code', async () => {
        mocks.sendMessage.mockRejectedValue({
            response: {
                data: {
                    code: 'BLOCKED_BY_USER',
                },
            },
        })

        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('안녕하세요')
        await getButtonByText(wrapper, 'common.send').trigger('click')
        await flushAll()

        expect(wrapper.emitted('close')).toBeUndefined()
        expect(mocks.loggerError).toHaveBeenCalled()
        expect(mocks.addToast).toHaveBeenCalledWith('user.message.blockedByUser', 'error')
    })

    it('cancels and resets a pending send when the receiver changes', async () => {
        let resolveSend: (value: unknown) => void = () => undefined
        mocks.sendMessage.mockReturnValueOnce(new Promise((resolve) => {
            resolveSend = resolve
        }))
        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('이전 대상 메시지')
        await getButtonByText(wrapper, 'common.send').trigger('click')
        const signal = mocks.sendMessage.mock.calls[0][2].signal as AbortSignal

        await wrapper.setProps({ userId: 4, displayName: '새 대상' })

        expect(signal.aborted).toBe(true)
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
        resolveSend({ data: { success: true } })
        await flushAll()
        expect(wrapper.emitted('close')).toBeUndefined()
        expect(mocks.addToast).not.toHaveBeenCalledWith('user.message.sendSuccess', 'success')
    })

    it('does not let a closed send complete a reopened modal', async () => {
        let resolveSend: (value: unknown) => void = () => undefined
        mocks.sendMessage.mockReturnValueOnce(new Promise((resolve) => {
            resolveSend = resolve
        }))
        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('닫기 전 메시지')
        await getButtonByText(wrapper, 'common.send').trigger('click')

        await getButtonByText(wrapper, 'common.cancel').trigger('click')
        await wrapper.setProps({ isOpen: false })
        await wrapper.setProps({ isOpen: true })
        await wrapper.get('textarea').setValue('새 메시지')
        resolveSend({ data: { success: true } })
        await flushAll()

        expect(wrapper.emitted('close')).toHaveLength(1)
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('새 메시지')
    })
})

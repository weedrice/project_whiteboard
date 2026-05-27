import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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
    extractErrorResponse: () => null,
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
    it('shows a warning and skips the request when content is blank', async () => {
        const wrapper = mountModal()

        await wrapper.get('textarea').setValue('   ')
        await getButtonByText(wrapper, 'common.send').trigger('click')

        expect(mocks.addToast).toHaveBeenCalledWith('user.message.inputContent', 'warning')
        expect(mocks.sendMessage).not.toHaveBeenCalled()
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
        expect(mocks.sendMessage).toHaveBeenCalledWith(3, '안녕하세요', { skipGlobalErrorHandler: true })

        resolveSend({ data: { success: true } })
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
    })
})

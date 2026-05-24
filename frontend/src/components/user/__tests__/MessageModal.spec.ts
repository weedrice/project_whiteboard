import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import MessageModal from '../MessageModal.vue'

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
        t: (key: string) => key,
    }),
}))

const BaseModalStub = defineComponent({
    props: {
        isOpen: Boolean,
        title: String,
    },
    template: '<section v-if="isOpen"><slot /></section>',
})

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
            $t: (key: string) => key,
        },
        stubs: {
            BaseModal: BaseModalStub,
            BaseTextarea: BaseTextareaStub,
        },
    },
})

const flushPromises = async () => {
    await Promise.resolve()
    await Promise.resolve()
}

describe('MessageModal', () => {
    it('ignores repeated sends while a send request is pending', async () => {
        let resolveSend: (value: unknown) => void = () => undefined
        mocks.sendMessage.mockReturnValue(new Promise((resolve) => {
            resolveSend = resolve
        }))

        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('안녕하세요')

        const sendButton = wrapper.findAll('button').at(1)
        await sendButton?.trigger('click')
        await sendButton?.trigger('click')

        expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
        expect(mocks.sendMessage).toHaveBeenCalledWith(3, '안녕하세요', { skipGlobalErrorHandler: true })

        resolveSend({ data: { success: true } })
        await flushPromises()
        expect(wrapper.emitted('close')).toHaveLength(1)
    })
})

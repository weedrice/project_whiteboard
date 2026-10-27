import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useConfirmStore } from '@/stores/confirm'
import { createDeferred } from '@/test/async'
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
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    it('renders the receiver as readonly content and final actions in the modal footer', () => {
        const wrapper = mountModal()
        const footer = wrapper.get('[data-test="modal-footer"]')

        expect(wrapper.get('[data-test="modal-body"] .nv-dialog-readonly').text()).toContain('받는 사람')
        expect(wrapper.find('[data-test="modal-body"] input[disabled]').exists()).toBe(false)
        expect(footer.findAll('button').map(button => button.text())).toEqual(['common.cancel', 'common.send'])
    })

    it('closes an empty draft without a discard confirmation', async () => {
        const wrapper = mountModal()
        await getButtonByText(wrapper, 'common.cancel').trigger('click')
        expect(useConfirmStore().isOpen).toBe(false)
        expect(wrapper.emitted('close')).toHaveLength(1)
        wrapper.unmount()
    })

    it('preserves a draft when discard is cancelled and clears it after confirmation', async () => {
        const wrapper = mountModal()
        const confirmation = useConfirmStore()
        await wrapper.get('textarea').setValue('Unsent draft')
        wrapper.getComponent(BaseModalStub).vm.$emit('close')
        await flushAll()
        expect(confirmation.isOpen).toBe(true)
        expect(confirmation.message).toBe('user.message.discardConfirm')
        expect(wrapper.emitted('close')).toBeUndefined()
        confirmation.cancel()
        await flushAll()
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('Unsent draft')
        await getButtonByText(wrapper, 'common.cancel').trigger('click')
        confirmation.confirm()
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
        wrapper.unmount()
    })

    it('keeps one confirmation for repeated close requests', async () => {
        const wrapper = mountModal()
        const confirmation = useConfirmStore()
        const open = vi.spyOn(confirmation, 'open')
        await wrapper.get('textarea').setValue('Unsent draft')
        wrapper.getComponent(BaseModalStub).vm.$emit('close')
        wrapper.getComponent(BaseModalStub).vm.$emit('close')
        await flushAll()
        expect(open).toHaveBeenCalledTimes(1)
        confirmation.confirm()
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
        wrapper.unmount()
    })

    it.each(['recipient', 'session', 'reopen', 'edit'] as const)(
        'does not discard a newer draft after a pending confirmation and %s change',
        async (change) => {
            const wrapper = mountModal()
            const confirmation = useConfirmStore()
            await wrapper.get('textarea').setValue('Old draft')
            await getButtonByText(wrapper, 'common.cancel').trigger('click')
            if (change === 'recipient') await wrapper.setProps({ userId: 4 })
            if (change === 'session') {
                useAuthStore().sessionGeneration += 1
                await wrapper.vm.$nextTick()
            }
            if (change === 'reopen') await wrapper.setProps({ isOpen: false })
            expect(confirmation.isOpen).toBe(change === 'edit')
            if (change === 'reopen') await wrapper.setProps({ isOpen: true })
            await wrapper.get('textarea').setValue('New draft')
            expect(confirmation.isOpen).toBe(false)
            confirmation.confirm()
            await flushAll()
            expect(wrapper.emitted('close')).toBeUndefined()
            expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('New draft')
            wrapper.unmount()
        },
    )

    it('dismisses its discard confirmation after unmount', async () => {
        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('Unsent draft')
        await getButtonByText(wrapper, 'common.cancel').trigger('click')
        expect(useConfirmStore().isOpen).toBe(true)
        wrapper.unmount()
        await flushAll()
        expect(useConfirmStore().isOpen).toBe(false)
        expect(wrapper.emitted('close')).toBeUndefined()
    })

    it.each(['unmount', 'recipient', 'close'] as const)(
        'preserves an unrelated newer confirmation on %s', async (change) => {
            const wrapper = mountModal()
            const confirmation = useConfirmStore()
            await wrapper.get('textarea').setValue('Unsent draft')
            await getButtonByText(wrapper, 'common.cancel').trigger('click')
            const unrelated = confirmation.open('Unrelated action')
            if (change === 'unmount') wrapper.unmount()
            if (change === 'recipient') await wrapper.setProps({ userId: 4 })
            if (change === 'close') await wrapper.setProps({ isOpen: false })
            await flushAll()
            expect(confirmation.isOpen).toBe(true)
            expect(confirmation.message).toBe('Unrelated action')
            confirmation.confirm()
            await expect(unrelated).resolves.toBe(true)
            expect(wrapper.emitted('close')).toBeUndefined()
            if (change !== 'unmount') wrapper.unmount()
        },
    )

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
            expectedSession: { sessionGeneration: 0, userId: null },
            signal: expect.any(AbortSignal),
        })

        resolveSend({ data: { success: true } })
        await flushAll()
        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('clears private content on a session change and ignores the old send completion', async () => {
        const pending = createDeferred<unknown>()
        mocks.sendMessage.mockReturnValueOnce(pending.promise)
        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('old private message')
        await getButtonByText(wrapper, 'common.send').trigger('click')
        const config = mocks.sendMessage.mock.calls[0]?.[2]
        useAuthStore().sessionGeneration += 1
        await wrapper.vm.$nextTick()
        expect(config.signal.aborted).toBe(true)
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
        await wrapper.get('textarea').setValue('new private message')
        pending.resolve({ data: { success: true } })
        await flushAll()
        expect(wrapper.emitted('close')).toBeUndefined()
        expect(mocks.addToast).not.toHaveBeenCalled()
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('new private message')
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

    it('keeps the modal open while a send may already be committed by the server', async () => {
        let resolveSend: (value: unknown) => void = () => undefined
        mocks.sendMessage.mockReturnValueOnce(new Promise((resolve) => {
            resolveSend = resolve
        }))
        const wrapper = mountModal()
        await wrapper.get('textarea').setValue('닫기 전 메시지')
        await getButtonByText(wrapper, 'common.send').trigger('click')

        const cancelButton = getButtonByText(wrapper, 'common.cancel')
        expect(cancelButton.attributes('disabled')).toBeDefined()
        await cancelButton.trigger('click')

        expect(wrapper.emitted('close')).toBeUndefined()
        expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('닫기 전 메시지')
        resolveSend({ data: { success: true } })
        await flushAll()

        expect(wrapper.emitted('close')).toHaveLength(1)
    })
})

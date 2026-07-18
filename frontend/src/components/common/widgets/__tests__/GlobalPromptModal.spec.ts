import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GlobalPromptModal from '../GlobalPromptModal.vue'
import { usePromptStore } from '@/stores/prompt'

function mountPromptModal(pinia: ReturnType<typeof createPinia>) {
    return mount(GlobalPromptModal, {
        global: {
            plugins: [pinia],
            stubs: {
                BaseModal: {
                    props: ['isOpen', 'title'],
                    template: '<section v-if="isOpen"><slot /><slot name="footer" /></section>',
                },
            },
        },
    })
}

describe('GlobalPromptModal', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it('keeps a hidden input label when prompt placeholder is provided', () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const promptStore = usePromptStore()
        promptStore.open('삭제할 이름을 입력하세요.', '삭제 확인', '게시글 제목')

        const wrapper = mountPromptModal(pinia)

        const label = wrapper.get('label')

        expect(label.classes()).toContain('sr-only')
        expect(label.text()).toBe('게시글 제목')
        expect(wrapper.get('input').attributes('placeholder')).toBe('게시글 제목')
    })

    it('falls back to prompt title for the hidden input label', () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const promptStore = usePromptStore()
        promptStore.open('값을 입력하세요.', '값 입력')

        const wrapper = mountPromptModal(pinia)

        expect(wrapper.get('label').text()).toBe('값 입력')
    })

    it('ignores composing enter before confirming prompt input', async () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const promptStore = usePromptStore()
        promptStore.open('message', 'title')
        const confirmSpy = vi.spyOn(promptStore, 'confirm')

        const wrapper = mountPromptModal(pinia)
        const input = wrapper.get('input')

        await input.trigger('keyup.enter', { isComposing: true })
        expect(confirmSpy).not.toHaveBeenCalled()

        await input.trigger('keyup.enter')
        expect(confirmSpy).toHaveBeenCalledTimes(1)
    })

    it('applies the caller-specific maximum input length', () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const promptStore = usePromptStore()
        promptStore.open('message', 'title', '', 'confirm', 'cancel', { maxLength: 255 })

        const wrapper = mountPromptModal(pinia)

        expect(wrapper.get('input').attributes('maxlength')).toBe('255')
    })
})

import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import SanctionModal from '../SanctionModal.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key
    })
}))

vi.mock('@/composables/useAdmin', () => ({
    useAdmin: () => ({
        useSanctionUser: () => ({
            mutateAsync: vi.fn(),
            isPending: ref(false)
        })
    })
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: vi.fn()
    })
}))

const BaseModalStub = defineComponent({
    props: {
        isOpen: Boolean,
        title: String
    },
    setup(props, { slots }) {
        return () => props.isOpen ? h('div', { 'data-test': 'modal' }, [
            h('h1', props.title),
            slots.default?.()
        ]) : null
    }
})

const PassThroughStub = defineComponent({
    setup(_, { slots }) {
        return () => h('div', slots.default?.())
    }
})

function mountModal(user: { id: number; name?: string; displayName?: string; nickname?: string; email?: string }) {
    return mount(SanctionModal, {
        props: {
            isOpen: true,
            user
        },
        global: {
            stubs: {
                BaseModal: BaseModalStub,
                BaseButton: PassThroughStub,
                BaseInput: PassThroughStub,
                BaseSelect: PassThroughStub,
                BaseTextarea: PassThroughStub
            }
        }
    })
}

describe('SanctionModal', () => {
    it('shows report target names when only name is provided', () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        expect(wrapper.text()).toContain('Reported User')
        expect(wrapper.text()).not.toContain('()')
    })

    it('shows email only when it exists', () => {
        const wrapper = mountModal({ id: 7, displayName: 'Target', email: 'target@test.com' })

        expect(wrapper.text()).toContain('Target (target@test.com)')
    })
})

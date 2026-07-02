import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SanctionModal from '../SanctionModal.vue'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    sanctionUser: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key
    })
}))

vi.mock('@/composables/useAdmin', () => ({
    useAdmin: () => ({
        useSanctionUser: () => ({
            mutateAsync: mocks.sanctionUser,
            isPending: ref(false)
        })
    })
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast
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

const BaseButtonStub = defineComponent({
    props: {
        type: { type: String, default: 'button' },
        disabled: Boolean,
    },
    emits: ['click'],
    setup(props, { emit, slots }) {
        return () => h('button', {
            type: props.type,
            disabled: props.disabled,
            onClick: () => emit('click'),
        }, slots.default?.())
    },
})

const BaseSelectStub = defineComponent({
    props: {
        id: String,
        modelValue: String,
        label: String,
    },
    emits: ['update:modelValue'],
    setup(props, { emit, slots }) {
        return () => h('label', [
            props.label,
            h('select', {
                id: props.id,
                value: props.modelValue,
                onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
            }, slots.default?.()),
        ])
    },
})

const BaseTextareaStub = defineComponent({
    props: {
        id: String,
        modelValue: String,
        label: String,
        rows: String,
        placeholder: String,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('label', [
            props.label,
            h('textarea', {
                id: props.id,
                value: props.modelValue,
                rows: props.rows,
                placeholder: props.placeholder,
                onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
            }),
        ])
    },
})

const BaseInputStub = defineComponent({
    props: {
        id: String,
        modelValue: [String, Number],
        label: String,
        type: String,
        min: String,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('label', [
            props.label,
            h('input', {
                id: props.id,
                type: props.type,
                min: props.min,
                value: props.modelValue,
                onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
            }),
        ])
    },
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
                AdminModalActions: PassThroughStub,
                BaseButton: BaseButtonStub,
                BaseInput: BaseInputStub,
                BaseSelect: BaseSelectStub,
                BaseTextarea: BaseTextareaStub
            }
        }
    })
}

describe('SanctionModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.sanctionUser.mockResolvedValue(undefined)
    })

    it('shows report target names when only name is provided', () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        expect(wrapper.text()).toContain('Reported User')
        expect(wrapper.text()).not.toContain('()')
    })

    it('shows email only when it exists', () => {
        const wrapper = mountModal({ id: 7, displayName: 'Target', email: 'target@test.com' })

        expect(wrapper.text()).toContain('Target (target@test.com)')
    })

    it('falls back to the reason when description is blank after trimming', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('select#reason').setValue('ABUSIVE_LANGUAGE')
        await wrapper.get('textarea#description').setValue('   ')
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            remark: 'ABUSIVE_LANGUAGE',
        }))
    })

    it('trims a custom sanction description before submitting', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('textarea#description').setValue('  repeated spam  ')
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            remark: 'repeated spam',
        }))
    })
})

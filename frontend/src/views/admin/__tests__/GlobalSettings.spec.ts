import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createPinia } from 'pinia'

const mocks = vi.hoisted(() => ({
    configsData: {
        __v_isRef: true,
        value: [
            {
                key: 'site.name',
                value: 'Noviis',
                description: 'Service name',
            },
        ] as Array<{ key: string; value: string; description?: string }>,
    },
    isLoading: { __v_isRef: true, value: false },
    updateConfig: vi.fn(),
    createConfig: vi.fn(),
    deleteConfig: vi.fn(),
    confirm: vi.fn(),
    addToast: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => ({
            'common.description': 'Description',
            'common.value': 'Value',
            'common.save': 'Save',
            'common.delete': 'Delete',
            'common.key': 'Key',
            'common.noData': 'No data',
            'common.add': 'Add',
            'common.cancel': 'Cancel',
            'common.deleted': 'Deleted',
            'common.confirmDelete': 'Confirm delete',
            'admin.settings.title': 'Global Settings',
            'admin.settings.description': 'Manage settings.',
            'admin.settings.addConfig': 'Add Config',
            'admin.settings.messages.saved': 'Saved',
        }[key] ?? key),
    }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast,
    }),
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: mocks.confirm,
    }),
}))

vi.mock('@/features/admin/useAdmin', () => ({
    useAdmin: () => ({
        useConfigs: () => ({
            data: mocks.configsData,
            isLoading: mocks.isLoading,
        }),
        useUpdateConfig: () => ({
            mutateAsync: mocks.updateConfig,
        }),
        useCreateConfig: () => ({
            mutateAsync: mocks.createConfig,
        }),
        useDeleteConfig: () => ({
            mutateAsync: mocks.deleteConfig,
        }),
    }),
}))

vi.mock('@/features/emoticon/form/useEmoticonImagePolicy', () => ({
    EMOTICON_IMAGE_MAX_COUNT_KEY: 'EMOTICON_IMAGE_MAX_COUNT',
    useEmoticonImagePolicy: () => ({ refresh: vi.fn() }),
}))

import GlobalSettings from '../GlobalSettings.vue'

const BaseInputStub = defineComponent({
    props: {
        modelValue: [String, Number],
        label: String,
        hideLabel: Boolean,
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () => h('div', [
            props.label ? h('label', { class: props.hideLabel ? 'sr-only' : '' }, props.label) : null,
            h('input', {
                value: props.modelValue,
                onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
            }),
        ])
    },
})

const BaseButtonStub = defineComponent({
    props: {
        type: { type: String, default: 'button' },
    },
    emits: ['click'],
    setup(props, { attrs, emit, slots }) {
        return () => h('button', { ...attrs, type: props.type, onClick: () => emit('click') }, slots.default?.())
    },
})

const mountGlobalSettings = () => mount(GlobalSettings, {
    global: {
        plugins: [createPinia()],
        stubs: {
            BaseInput: BaseInputStub,
            BaseButton: BaseButtonStub,
            BaseModal: true,
            Save: true,
            Trash2: true,
        },
    },
})

describe('GlobalSettings', () => {
    beforeEach(() => {
        mocks.configsData.value = [
            {
                key: 'site.name',
                value: 'Noviis',
                description: 'Service name',
            },
        ]
        mocks.updateConfig.mockReset()
        mocks.createConfig.mockReset()
        mocks.deleteConfig.mockReset()
        mocks.confirm.mockReset()
        mocks.addToast.mockReset()
    })

    it('keeps hidden labels for editable config fields and names icon actions', () => {
        const wrapper = mountGlobalSettings()
        const labels = wrapper.findAll('label.sr-only').map((label) => label.text())

        expect(labels).toContain('site.name Description')
        expect(labels).toContain('site.name Value')
        expect(wrapper.find('button[aria-label="Save"]').exists()).toBe(true)
        expect(wrapper.find('button[aria-label="Delete"]').exists()).toBe(true)
    })

    it('saves the edited draft instead of mutating the query item directly', async () => {
        const wrapper = mountGlobalSettings()
        const inputs = wrapper.findAll('input')

        await inputs[0].setValue('Updated description')
        await inputs[1].setValue('Updated name')
        await wrapper.find('button[aria-label="Save"]').trigger('click')

        expect(mocks.configsData.value[0]).toEqual({
            key: 'site.name',
            value: 'Noviis',
            description: 'Service name',
        })
        expect(mocks.updateConfig).toHaveBeenCalledWith({
            key: 'site.name',
            value: 'Updated name',
            description: 'Updated description',
        })
    })

    it('preserves an omitted description until the user edits it', async () => {
        mocks.configsData.value = [
            {
                key: 'site.name',
                value: 'Noviis',
            },
        ]
        const wrapper = mountGlobalSettings()
        const inputs = wrapper.findAll('input')

        await inputs[1].setValue('Updated name')
        await wrapper.find('button[aria-label="Save"]').trigger('click')

        expect(mocks.updateConfig).toHaveBeenCalledWith({
            key: 'site.name',
            value: 'Updated name',
            description: undefined,
        })
    })
})

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import GlobalSettings from '../GlobalSettings.vue'

const configsData = ref([
    {
        key: 'site.name',
        value: 'Noviis',
        description: '서비스 이름',
    },
])

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => ({
            'common.description': '설명',
            'common.value': '값',
            'common.save': '저장',
            'common.delete': '삭제',
            'common.key': '키',
            'common.noData': '데이터가 없습니다',
            'admin.settings.title': '전역 설정',
            'admin.settings.description': '설정을 관리합니다.',
        }[key] ?? key),
    }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: vi.fn(),
    }),
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: vi.fn().mockResolvedValue(true),
    }),
}))

vi.mock('@/composables/useAdmin', () => ({
    useAdmin: () => ({
        useConfigs: () => ({
            data: configsData,
            isLoading: ref(false),
        }),
        useUpdateConfig: () => ({
            mutateAsync: vi.fn(),
        }),
        useCreateConfig: () => ({
            mutateAsync: vi.fn(),
        }),
        useDeleteConfig: () => ({
            mutateAsync: vi.fn(),
        }),
    }),
}))

const BaseInputStub = defineComponent({
    props: {
        modelValue: String,
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
    it('keeps hidden labels for editable config fields and names icon actions', () => {
        const wrapper = mountGlobalSettings()
        const labels = wrapper.findAll('label.sr-only').map((label) => label.text())

        expect(labels).toContain('site.name 설명')
        expect(labels).toContain('site.name 값')
        expect(wrapper.find('button[aria-label="저장"]').exists()).toBe(true)
        expect(wrapper.find('button[aria-label="삭제"]').exists()).toBe(true)
    })
})

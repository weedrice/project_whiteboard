import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import BaseModal from '../ui/BaseModal.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: (key: string) => key }),
}))

const BaseButtonStub = defineComponent({
    name: 'BaseButton',
    emits: ['click'],
    setup(_props, { emit, slots }) {
        return () => h('button', { type: 'button', onClick: () => emit('click') }, slots.default?.())
    },
})

const mountModal = (isOpen = true) => mount(BaseModal, {
    props: {
        isOpen,
        title: 'Modal',
    },
    slots: {
        default: '<button type="button">First</button>',
    },
    global: {
        mocks: {
            $t: (key: string) => key,
        },
        stubs: {
            BaseButton: BaseButtonStub,
            Teleport: true,
        },
    },
})

const mountConfiguredModal = () => mount(BaseModal, {
    props: {
        isOpen: true,
        title: 'Configured',
        description: 'A concise modal description.',
        titleTag: 'h3' as const,
        headerClass: 'custom-header',
        bodyClass: 'custom-body',
        footerClass: 'custom-footer',
        footerAlign: 'between',
        closeAriaLabel: 'Close configured modal',
        closeButtonClass: 'custom-close',
        closeOnBackdrop: false,
        closeOnEscape: false,
    },
    slots: {
        default: '<button type="button">Body button</button>',
        footer: '<button type="button">Footer button</button>',
    },
    global: {
        mocks: {
            $t: (key: string) => key,
        },
        stubs: {
            BaseButton: BaseButtonStub,
            Teleport: true,
        },
    },
})

const mountedWrappers: VueWrapper[] = []

function track(wrapper: VueWrapper) {
    mountedWrappers.push(wrapper)
    return wrapper
}

afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => {
        wrapper.unmount()
    })
    document.body.style.overflow = ''
})

describe('BaseModal', () => {
    it('locks body scroll while open and restores the previous overflow after close', async () => {
        document.body.style.overflow = 'scroll'
        const wrapper = track(mountModal(false))

        await wrapper.setProps({ isOpen: true })
        await nextTick()
        await nextTick()

        expect(document.body.style.overflow).toBe('hidden')

        await wrapper.setProps({ isOpen: false })
        await nextTick()

        expect(document.body.style.overflow).toBe('scroll')
    })

    it('keeps body scroll locked until every open modal closes', async () => {
        const first = track(mountModal(true))
        const second = track(mountModal(true))
        await nextTick()
        await nextTick()

        expect(document.body.style.overflow).toBe('hidden')

        await first.setProps({ isOpen: false })
        await nextTick()

        expect(document.body.style.overflow).toBe('hidden')

        await second.setProps({ isOpen: false })
        await nextTick()

        expect(document.body.style.overflow).toBe('')
    })

    it('releases its scroll lock when unmounted while open', async () => {
        const wrapper = track(mountModal(true))
        await nextTick()
        await nextTick()

        expect(document.body.style.overflow).toBe('hidden')

        wrapper.unmount()
        mountedWrappers.pop()

        expect(document.body.style.overflow).toBe('')
    })

    it('uses tokenized modal text and shell classes', async () => {
        const wrapper = track(mountModal(true))
        await nextTick()

        expect(wrapper.find('.modal-content').exists()).toBe(true)
        expect(wrapper.get('.modal-header .nv-title').text()).toBe('Modal')
    })

    it('labels the dialog without treating the entire body as its description', async () => {
        const wrapper = track(mountModal(true))
        await nextTick()

        const dialog = wrapper.get('[role="dialog"]')
        const titleId = dialog.attributes('aria-labelledby')

        expect(titleId).toBeTruthy()
        expect(dialog.attributes('aria-describedby')).toBeUndefined()
        expect(wrapper.findAll('[id]').find((node) => node.attributes('id') === titleId)?.text()).toBe('Modal')
        expect(wrapper.get('h2').text()).toBe('Modal')
        expect(wrapper.get('.modal-body').attributes('id')).toBeUndefined()
    })

    it('links an optional concise description and supports a configured title level', async () => {
        const wrapper = track(mountConfiguredModal())
        await nextTick()

        const dialog = wrapper.get('[role="dialog"]')
        const descriptionId = dialog.attributes('aria-describedby')

        expect(descriptionId).toBeTruthy()
        expect(wrapper.get(`#${descriptionId}`).text()).toBe('A concise modal description.')
        expect(wrapper.get(`#${descriptionId}`).classes()).toContain('sr-only')
        expect(wrapper.get('h3').text()).toBe('Configured')
    })

    it('applies shell configuration without forcing backdrop or escape close', async () => {
        const wrapper = track(mountConfiguredModal())
        await nextTick()

        expect(wrapper.get('.modal-header').classes()).toContain('custom-header')
        expect(wrapper.get('.modal-body').classes()).toContain('custom-body')
        expect(wrapper.get('.modal-footer').classes()).toContain('custom-footer')
        expect(wrapper.get('.modal-footer').classes()).toContain('justify-between')
        expect(wrapper.get('.custom-close').attributes('aria-label')).toBe('Close configured modal')

        await wrapper.get('.modal-overlay').trigger('click')
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

        expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('closes only the topmost modal when Escape is pressed', async () => {
        const first = track(mountModal(true))
        const second = track(mountModal(true))
        await nextTick()
        await nextTick()

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

        expect(first.emitted('close')).toBeUndefined()
        expect(second.emitted('close')).toHaveLength(1)
    })
})

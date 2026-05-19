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
})

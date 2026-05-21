import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const routerPush = vi.hoisted(() => vi.fn())
const pointData = vi.hoisted(() => ({ value: null as { currentPoint: number } | null }))
const useMyPoint = vi.hoisted(() => vi.fn(() => ({ data: pointData })))
const authState = vi.hoisted(() => ({
    isAuthenticated: true,
    user: {
        userId: 10,
        loginId: 'noviis',
        displayName: 'Novi User',
        email: 'user@example.com',
        role: 'USER',
    },
    logout: vi.fn(),
}))
const keyboardStore = vi.hoisted(() => ({
    setOpenDropdown: vi.fn(),
    closeDropdown: vi.fn(),
}))

vi.mock('vue-router', () => ({
    useRouter: () => ({ push: routerPush }),
    RouterLink: defineComponent({
        props: { to: { type: [String, Object], required: false } },
        setup(_props, { slots }) {
            return () => h('a', slots.default?.())
        },
    }),
}))

vi.mock('lucide-vue-next', () => {
    const Icon = defineComponent({ setup: () => () => h('i') })
    return {
        User: Icon,
        LogOut: Icon,
        CreditCard: Icon,
        FileText: Icon,
        Clock: Icon,
        AlertTriangle: Icon,
        PlusSquare: Icon,
        ChevronDown: Icon,
        Bell: Icon,
        LayoutDashboard: Icon,
        Mail: Icon,
        Star: Icon,
        Slash: Icon,
        Smile: Icon,
    }
})

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => authState,
}))

vi.mock('@/stores/keyboard', () => ({
    useKeyboardStore: () => keyboardStore,
}))

vi.mock('@/composables/useUser', () => ({
    useUser: () => ({ useMyPoint }),
}))

vi.mock('@/components/common/ui/BaseButton.vue', () => ({
    default: defineComponent({
        setup(_props, { slots }) {
            return () => h('button', slots.default?.())
        },
    }),
}))

import UserDropdown from '../UserDropdown.vue'

const RouterLinkStub = defineComponent({
    props: { to: { type: [String, Object], required: false } },
    setup(_props, { slots }) {
        return () => h('a', slots.default?.())
    },
})

describe('UserDropdown', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        authState.isAuthenticated = true
        pointData.value = { currentPoint: 12345 }
    })

    it('uses cached user point query when dropdown is open', () => {
        const wrapper = mount(UserDropdown, {
            props: { isOpen: true },
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    'router-link': RouterLinkStub,
                },
            },
        })

        expect(wrapper.text()).toContain('12,345 P')
        expect(useMyPoint).toHaveBeenCalled()
        expect(keyboardStore.setOpenDropdown).toHaveBeenCalledWith('user', expect.any(Array))
    })

    it('keeps point query disabled while closed', () => {
        mount(UserDropdown, {
            props: { isOpen: false },
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    'router-link': RouterLinkStub,
                },
            },
        })

        const [enabled] = useMyPoint.mock.calls[0] as unknown as [{ value: boolean }]
        expect(enabled.value).toBe(false)
        expect(keyboardStore.setOpenDropdown).not.toHaveBeenCalled()
    })
})

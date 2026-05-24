import { mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import UserDropdown from '../UserDropdown.vue'

const mocks = vi.hoisted(() => ({
    routerPush: vi.fn(),
    logout: vi.fn(),
    setOpenDropdown: vi.fn(),
    closeDropdown: vi.fn(),
    getMyPoint: vi.fn(),
}))

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: mocks.routerPush,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({
        user: {
            loginId: 'tester',
            displayName: '테스터',
            email: 'tester@example.com',
            role: 'USER',
        },
        logout: mocks.logout,
    }),
}))

vi.mock('@/stores/keyboard', () => ({
    useKeyboardStore: () => ({
        setOpenDropdown: mocks.setOpenDropdown,
        closeDropdown: mocks.closeDropdown,
    }),
}))

vi.mock('@/api/user', () => ({
    userApi: {
        getMyPoint: mocks.getMyPoint,
    },
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
    },
}))

const iconStubs = [
    'User',
    'LogOut',
    'CreditCard',
    'FileText',
    'Clock',
    'AlertTriangle',
    'PlusSquare',
    'ChevronDown',
    'Bell',
    'LayoutDashboard',
    'Mail',
    'Star',
    'Slash',
    'Smile',
]

const mountDropdown = (isOpen = false) => mount(UserDropdown, {
    props: { isOpen },
    global: {
        mocks: {
            $t: (key: string) => key,
        },
        stubs: {
            'router-link': RouterLinkStub,
            BaseButton: true,
            ...Object.fromEntries(iconStubs.map((icon) => [icon, true])),
        },
    },
})

describe('UserDropdown', () => {
    it('exposes menu semantics on the profile trigger', () => {
        const closedWrapper = mountDropdown(false)
        const closedButton = closedWrapper.get('button[aria-label="사용자 메뉴"]')

        expect(closedButton.attributes()).toMatchObject({
            type: 'button',
            'aria-haspopup': 'menu',
            'aria-expanded': 'false',
        })
        expect(closedButton.attributes('aria-controls')).toBeUndefined()

        const openWrapper = mountDropdown(true)
        const openButton = openWrapper.get('button[aria-label="사용자 메뉴"]')

        expect(openButton.attributes('aria-expanded')).toBe('true')
        expect(openButton.attributes('aria-controls')).toBe('user-dropdown-menu')
        expect(openWrapper.get('#user-dropdown-menu').attributes('role')).toBe('menu')
    })
})

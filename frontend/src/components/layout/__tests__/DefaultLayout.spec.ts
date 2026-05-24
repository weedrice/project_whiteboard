import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'

const routeState = ref({ name: 'home' as string | null })
const routerPush = vi.fn()
const logout = vi.fn()
const toggleThemePreference = vi.hoisted(() => vi.fn())
const authState = vi.hoisted(() => ({
    isAuthenticated: false,
    user: null as null | Record<string, unknown>,
}))

vi.mock('vue-router', () => ({
    createRouter: vi.fn(() => ({
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
        onError: vi.fn(),
    })),
    createWebHistory: vi.fn(() => ({})),
    useRoute: () => routeState.value,
    useRouter: () => ({ push: routerPush }),
    RouterLink: defineComponent({
        props: { to: { type: [String, Object], required: false } },
        setup(_props, { slots }) {
            return () => h('a', slots.default?.())
        },
    }),
    RouterView: defineComponent({
        setup() {
            return () => h('div', 'router-view')
        },
    }),
}))

vi.mock('lucide-vue-next', () => ({
    Bell: defineComponent({ setup: () => () => h('i') }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({
        isAuthenticated: authState.isAuthenticated,
        user: authState.user,
        logout,
    }),
}))

vi.mock('@/stores/theme', () => ({
    useThemeStore: () => ({
        isDark: false,
    }),
}))

vi.mock('@/composables/useThemePreference', () => ({
    useThemePreference: () => ({
        toggleTheme: toggleThemePreference,
    }),
}))

vi.mock('@/stores/keyboard', () => ({
    useKeyboardStore: () => ({
        isShortcutsModalOpen: false,
        closeDropdown: vi.fn(),
        setOpenDropdown: vi.fn(),
        closeShortcutsModal: vi.fn(),
        toggleShortcutsModal: vi.fn(),
    }),
}))

vi.mock('@/composables/useNotification', () => ({
    useNotification: () => ({
        useUnreadCount: () => ({ data: ref(0) }),
        connectToSse: vi.fn(),
        closeSse: vi.fn(),
    }),
}))

vi.mock('@/utils/keyboard', () => ({
    isInputFocused: () => false,
}))

vi.mock('@/assets/noviis_logo.webp', () => ({ default: '/logo-light.webp' }))
vi.mock('@/assets/noviis_logo_dark.webp', () => ({ default: '/logo-dark.webp' }))

import DefaultLayout from '../DefaultLayout.vue'

const MobileBottomNavStub = defineComponent({
    name: 'MobileBottomNav',
    props: {
        hidden: { type: Boolean, default: false },
    },
    setup(props) {
        return () => h('div', { 'data-testid': 'mobile-bottom-nav', 'data-hidden': String(props.hidden) })
    },
})

describe('DefaultLayout', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        toggleThemePreference.mockClear()
        routeState.value = { name: 'home' }
        authState.isAuthenticated = false
        authState.user = null
        Object.defineProperty(window, 'matchMedia', {
            writable: true,
            value: vi.fn().mockImplementation(() => ({
                matches: true,
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
            })),
        })
    })

    it('hides and restores the mobile bottom nav in response to editor focus events', async () => {
        const wrapper = mount(DefaultLayout, {
            global: {
                stubs: {
                    'router-link': true,
                    'router-view': true,
                    NotificationDropdown: true,
                    UserDropdown: true,
                    BoardDropdown: true,
                    Footer: true,
                    GlobalSearchBar: true,
                    KeyboardShortcutsModal: true,
                    RecentBoardsBar: true,
                    MobileBottomNav: MobileBottomNavStub,
                },
                mocks: {
                    $t: (key: string) => key,
                },
            },
        })

        expect(wrapper.get('[data-testid="mobile-bottom-nav"]').attributes('data-hidden')).toBe('false')

        window.dispatchEvent(new CustomEvent('noviis:editor-focus-change', { detail: true }))
        await nextTick()
        expect(wrapper.get('[data-testid="mobile-bottom-nav"]').attributes('data-hidden')).toBe('true')

        window.dispatchEvent(new CustomEvent('noviis:editor-focus-change', { detail: false }))
        await nextTick()
        expect(wrapper.get('[data-testid="mobile-bottom-nav"]').attributes('data-hidden')).toBe('false')
    })

    it('announces notification dropdown expanded state and panel relationship', async () => {
        authState.isAuthenticated = true
        authState.user = { displayName: 'Tester' }

        const wrapper = mount(DefaultLayout, {
            global: {
                stubs: {
                    'router-link': true,
                    'router-view': true,
                    NotificationDropdown: true,
                    UserDropdown: true,
                    BoardDropdown: true,
                    Footer: true,
                    GlobalSearchBar: true,
                    KeyboardShortcutsModal: true,
                    RecentBoardsBar: true,
                    MobileBottomNav: MobileBottomNavStub,
                },
                mocks: {
                    $t: (key: string) => key,
                },
            },
        })

        const notificationButton = wrapper.get('button[aria-label="Open notifications"]')
        expect(notificationButton.attributes('aria-expanded')).toBe('false')
        expect(notificationButton.attributes('aria-controls')).toBe('notification-dropdown-panel')

        await notificationButton.trigger('click')

        expect(notificationButton.attributes('aria-expanded')).toBe('true')
        expect(wrapper.find('#notification-dropdown-panel').exists()).toBe(true)
    })
})

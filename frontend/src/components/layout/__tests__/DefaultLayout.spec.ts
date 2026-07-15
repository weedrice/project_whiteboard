import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'

const routeState = ref({ name: 'home' as string | null })
const routerPush = vi.fn()
const toggleThemePreference = vi.hoisted(() => vi.fn())
const authMocks = vi.hoisted(() => {
    const logout = vi.fn()
    return {
        logout,
        authStore: {
            isAuthenticated: false,
            user: null as null | Record<string, unknown>,
            logout,
        },
    }
})
const keyboardStore = vi.hoisted(() => ({
    isShortcutsModalOpen: false,
    closeDropdown: vi.fn(),
    setOpenDropdown: vi.fn(),
    closeShortcutsModal: vi.fn(),
    toggleShortcutsModal: vi.fn(),
}))
const notificationMocks = vi.hoisted(() => ({
    connectToSse: vi.fn(),
    closeSse: vi.fn(),
    unreadCount: { __v_isRef: true as const, value: 0 },
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
    AtSign: defineComponent({ setup: () => () => h('i') }),
    Award: defineComponent({ setup: () => () => h('i') }),
    Bell: defineComponent({ setup: () => () => h('i') }),
    Heart: defineComponent({ setup: () => () => h('i') }),
    Mail: defineComponent({ setup: () => () => h('i') }),
    Megaphone: defineComponent({ setup: () => () => h('i') }),
    MessageCircle: defineComponent({ setup: () => () => h('i') }),
    Reply: defineComponent({ setup: () => () => h('i') }),
    ShieldAlert: defineComponent({ setup: () => () => h('i') }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => authMocks.authStore,
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
    useKeyboardStore: () => keyboardStore,
}))

vi.mock('vue-i18n', async (importOriginal) => {
    const actual = await importOriginal<typeof import('vue-i18n')>()
    return {
        ...actual,
        useI18n: () => ({
            t: (key: string, values?: Record<string, string>) => {
                if (key === 'layout.a11y.openNotifications') return 'Open notifications'
                if (key === 'layout.a11y.goToNotifications') return 'Go to notifications'
                if (key === 'notification.unreadNotifications') return `${values?.count} unread notifications`
                if (key === 'layout.a11y.homeLink') return `${values?.appName ?? 'Noviis'} home`
                if (key === 'common.skipToContent') return 'Skip to content'
                if (key === 'common.appName') return 'Noviis'
                return key
            },
        }),
    }
})

vi.mock('@/features/notifications/queries/useNotification', () => ({
    useNotification: () => ({
        useUnreadCount: () => ({ data: notificationMocks.unreadCount }),
        connectToSse: notificationMocks.connectToSse,
        closeSse: notificationMocks.closeSse,
    }),
}))

vi.mock('@/components/user/BadgeAwardCelebration.vue', () => ({
    default: defineComponent({
        name: 'BadgeAwardCelebration',
        setup: () => () => h('div', { 'data-testid': 'badge-award-celebration' }),
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
        authMocks.authStore.isAuthenticated = false
        authMocks.authStore.user = null
        keyboardStore.isShortcutsModalOpen = false
        notificationMocks.unreadCount.value = 0
        routeState.value = { name: 'home' }
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

        wrapper.unmount()
    })

    it('keeps the desktop logo from shrinking when the header width is constrained', () => {
        const wrapper = mount(DefaultLayout, {
            global: {
                stubs: {
                    RouterLink: RouterLinkStub,
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

        const homeLink = wrapper.get('a[aria-label="Noviis home"]')
        const desktopLogo = wrapper.get('img[alt="common.appName"]')

        expect(homeLink.classes()).toContain('shrink-0')
        expect(desktopLogo.classes()).toContain('shrink-0')
        expect(desktopLogo.classes()).toContain('object-contain')
    })

    it('opens and closes the notification stream with the layout lifecycle', () => {
        authMocks.authStore.isAuthenticated = true

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

        expect(notificationMocks.connectToSse).toHaveBeenCalledTimes(1)

        wrapper.unmount()

        expect(notificationMocks.closeSse).toHaveBeenCalled()
    })

    it('keeps global keyboard shortcuts wired through the extracted handler', async () => {
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

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))
        await nextTick()

        expect(routerPush).toHaveBeenCalledWith('/search')

        wrapper.unmount()
        routerPush.mockClear()

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'h' }))
        await nextTick()

        expect(routerPush).not.toHaveBeenCalled()
    })

    it('announces notification dropdown expanded state and panel relationship', async () => {
        authMocks.authStore.isAuthenticated = true
        authMocks.authStore.user = { displayName: 'Tester' }

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

    it('includes the unread count in notification controls and the live status', () => {
        authMocks.authStore.isAuthenticated = true
        authMocks.authStore.user = { displayName: 'Tester' }
        notificationMocks.unreadCount.value = 3

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
                mocks: { $t: (key: string) => key },
            },
        })

        expect(wrapper.get('button[aria-controls="notification-dropdown-panel"]').attributes('aria-label'))
            .toBe('Open notifications. 3 unread notifications')
        expect(wrapper.get('[role="status"]').text()).toBe('3 unread notifications')
    })
})

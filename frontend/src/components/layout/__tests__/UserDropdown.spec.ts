import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import UserDropdown from '../UserDropdown.vue'

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
    setup(_props, { slots, attrs }) {
      return () => h('a', attrs, slots.default?.())
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
    ShoppingBag: Icon,
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
    setup(_props, { slots, attrs }) {
      return () => h('button', attrs, slots.default?.())
    },
  }),
}))

const RouterLinkStub = defineComponent({
  props: { to: { type: [String, Object], required: false } },
  setup(_props, { slots, attrs }) {
    return () => h('a', attrs, slots.default?.())
  },
})

const t = (key: string) => ({
  'layout.userMenu.ariaLabel': '사용자 메뉴',
}[key] ?? key)

const mountDropdown = (isOpen = false) => mount(UserDropdown, {
  props: { isOpen },
  global: {
    mocks: {
      $t: t,
    },
    stubs: {
      'router-link': RouterLinkStub,
    },
  },
})

describe('UserDropdown', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.isAuthenticated = true
    authState.user.role = 'USER'
    pointData.value = { currentPoint: 12345 }
  })

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
    expect(openWrapper.findAll('[role="menuitem"]').length).toBeGreaterThan(0)
  })

  it('uses cached user point query when dropdown is open', () => {
    const wrapper = mountDropdown(true)

    expect(wrapper.text()).toContain('12,345 P')
    expect(useMyPoint).toHaveBeenCalled()
    expect(keyboardStore.setOpenDropdown).toHaveBeenCalledWith('user', expect.any(Array))
    expect(keyboardStore.setOpenDropdown.mock.calls[0][1]).toHaveLength(9)
    expect(wrapper.text()).toContain('layout.menu.createBoard')
    expect(wrapper.text()).toContain('emoticon.title')
    expect(wrapper.text()).toContain('shop.title')
    expect(wrapper.text()).toContain('shop.purchases.title')
  })

  it('keeps point query disabled while closed', () => {
    mountDropdown(false)

    const [enabled] = useMyPoint.mock.calls[0] as unknown as [{ value: boolean }]
    expect(enabled.value).toBe(false)
    expect(keyboardStore.setOpenDropdown).not.toHaveBeenCalled()
  })

  it('shows the admin menu without adding it to numbered shortcuts', () => {
    authState.user.role = 'SUPER_ADMIN'

    const wrapper = mountDropdown(true)

    expect(wrapper.text()).toContain('layout.menu.admin')
    expect(keyboardStore.setOpenDropdown.mock.calls[0][1]).toHaveLength(9)
  })
})

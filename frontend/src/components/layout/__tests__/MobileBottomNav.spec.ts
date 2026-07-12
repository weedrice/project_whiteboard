import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import MobileBottomNav from '../MobileBottomNav.vue'

const { mocks, refLike } = vi.hoisted(() => ({
  refLike: <T>(value: T) => ({ __v_isRef: true, value }),
  mocks: {
    route: {
      name: 'home' as string | null
    },
    routerPush: vi.fn(),
    openWriteSheet: vi.fn(),
    closeWriteSheet: vi.fn(),
    goToBoardWrite: vi.fn(),
    handleSheetKeydown: vi.fn(),
    showWriteSheet: { __v_isRef: true as const, value: false },
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.routerPush
  })
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true
  })
}))

vi.mock('@/composables/useNotification', () => ({
  useNotification: () => ({
    useUnreadCount: () => ({
      data: refLike(0)
    })
  })
}))

vi.mock('@/composables/useWriteBoardSheet', () => ({
  useWriteBoardSheet: () => ({
    fabButtonRef: refLike(null),
    sheetRef: refLike(null),
    showWriteSheet: mocks.showWriteSheet,
    preferredBoards: refLike([]),
    isSubscribedBoardsLoading: refLike(false),
    isBoardsError: refLike(false),
    isSubscribedBoardsError: refLike(false),
    openWriteSheet: mocks.openWriteSheet,
    closeWriteSheet: mocks.closeWriteSheet,
    goToBoardWrite: mocks.goToBoardWrite,
    handleSheetKeydown: mocks.handleSheetKeydown
  })
}))

function mountNav(routeName: string | null) {
  mocks.route.name = routeName
  return mount(MobileBottomNav, {
    global: {
      mocks: {
        $t: (key: string) => key
      },
      stubs: {
        Teleport: true,
        RouterLink: RouterLinkStub,
      }
    }
  })
}

describe('MobileBottomNav', () => {
  beforeEach(() => {
    mocks.showWriteSheet.value = false
    vi.clearAllMocks()
  })

  it.each([
    ['home', 0],
    ['board-detail', 1],
    ['MyNotifications', 2],
    ['mypage', 3]
  ])('marks only the active route as current for %s', (routeName, activeIndex) => {
    const wrapper = mountNav(routeName)
    const navButtons = wrapper.findAll('.nv-mobile-nav-item')
    const ariaCurrentValues = navButtons.map((button) => button.attributes('aria-current'))
    const expectedValues = navButtons.map((_, index) => (index === activeIndex ? 'page' : undefined))

    expect(ariaCurrentValues).toEqual(expectedValues)
  })

  it('connects the write button with the mobile write sheet dialog', () => {
    const closedWrapper = mountNav('home')
    const writeButton = closedWrapper.get('.nv-mobile-nav-fab')

    expect(writeButton.attributes()).toMatchObject({
      'aria-haspopup': 'dialog',
      'aria-controls': 'mobile-write-sheet',
      'aria-expanded': 'false',
      'aria-label': 'layout.mobileNav.createPost',
    })

    mocks.showWriteSheet.value = true
    const openWrapper = mountNav('home')

    expect(openWrapper.get('.nv-mobile-nav-fab').attributes('aria-expanded')).toBe('true')
    expect(openWrapper.get('#mobile-write-sheet').attributes()).toMatchObject({
      role: 'dialog',
      'aria-modal': 'true',
      'aria-labelledby': 'mobile-write-sheet-title',
    })
  })

  it('uses links for page navigation and keeps the composer as a button', () => {
    const wrapper = mountNav('home')
    const navItems = wrapper.findAll('.nv-mobile-nav-item')

    expect(navItems).toHaveLength(4)
    expect(navItems.every((item) => item.element.tagName === 'A')).toBe(true)
    expect(wrapper.get('.nv-mobile-nav-fab').element.tagName).toBe('BUTTON')
  })
})

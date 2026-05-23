import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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
    handleSheetKeydown: vi.fn()
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
    showWriteSheet: refLike(false),
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
        Teleport: true
      }
    }
  })
}

describe('MobileBottomNav', () => {
  it.each([
    ['home', 0],
    ['board-detail', 1],
    ['MyNotifications', 2],
    ['mypage', 3]
  ])('marks only the active route as current for %s', (routeName, activeIndex) => {
    const wrapper = mountNav(routeName)
    const navButtons = wrapper.findAll('.nv-mobile-nav-item')

    navButtons.forEach((button, index) => {
      if (index === activeIndex) {
        expect(button.attributes('aria-current')).toBe('page')
      } else {
        expect(button.attributes('aria-current')).toBeUndefined()
      }
    })
  })
})

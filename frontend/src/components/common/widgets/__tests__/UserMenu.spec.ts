import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserMenu from '../UserMenu.vue'

const authState = {
  user: { userId: 1 } as { userId: number } | null
}
const invalidateQueriesMock = vi.fn()
const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key === 'user.deletedUser' ? '탈퇴한 사용자' : key
    })
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({
    push: routerPushMock
  })
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/vue-query')>()
  return {
    ...actual,
    useQueryClient: () => ({
      invalidateQueries: invalidateQueriesMock
    })
  }
})

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: vi.fn()
  })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn()
  })
}))

vi.mock('@/composables/useKeyboardNavigation', () => ({
  useKeyboardNavigation: () => ({
    selectedIndex: ref(-1),
    handleKeyDown: vi.fn(),
    setSelectedIndex: vi.fn(),
    reset: vi.fn()
  })
}))

vi.mock('@/composables/useFocusTrap', () => ({
  useFocusTrap: () => ({
    trapFocus: vi.fn(),
    restoreFocus: vi.fn()
  })
}))

vi.mock('@/api/user', () => ({
  userApi: {
    blockUser: vi.fn()
  }
}))

vi.mock('@/api/report', () => ({
  reportApi: {
    reportUser: vi.fn()
  }
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn()
  }
}))

describe('UserMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { userId: 1 }
  })

  it('truncates the visible label using maxLabelLength', () => {
    const wrapper = mount(UserMenu, {
      props: {
        userId: 2,
        displayName: '12345678901',
        maxLabelLength: 10
      },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: true,
          Teleport: true
        }
      }
    })

    const button = wrapper.get('button')

    expect(button.text()).toBe('1234567890...')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('shows the deleted-user fallback label and keeps profile access for guests', () => {
    authState.user = null

    const wrapper = mount(UserMenu, {
      props: {
        userId: 2,
        displayName: '',
        maxLabelLength: 10
      },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: true,
          Teleport: true
        }
      }
    })

    const button = wrapper.get('button')

    expect(button.text()).toBe('탈퇴한 사용자')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('keeps profile access for the current user', () => {
    authState.user = { userId: 7 }

    const wrapper = mount(UserMenu, {
      props: {
        userId: 7,
        displayName: 'Author'
      },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: true,
          Teleport: true
        }
      }
    })

    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
  })
})

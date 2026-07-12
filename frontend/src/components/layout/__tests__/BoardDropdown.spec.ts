import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BoardDropdown from '../BoardDropdown.vue'
import { useKeyboardStore } from '@/stores/keyboard'

const mocks = vi.hoisted(() => {
  return {
    allBoards: undefined as Ref<Array<{ boardUrl: string; boardName: string }>> | undefined,
    subscribedBoards: undefined as Ref<Array<{ boardUrl: string; boardName: string }>> | undefined,
    route: { name: null as string | null },
    routerPush: vi.fn(),
    allBoardsError: undefined as Ref<boolean> | undefined,
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.routerPush
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true
  })
}))

vi.mock('@/composables/useBoard', async () => {
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  mocks.allBoards = ref([])
  mocks.subscribedBoards = ref([])
  mocks.allBoardsError = ref(false)

  return {
    useBoard: () => ({
      useBoards: () => ({
        data: mocks.allBoards,
        isLoading: ref(false),
        isError: mocks.allBoardsError
      }),
      useSubscribedBoards: () => ({
        data: mocks.subscribedBoards,
        isLoading: ref(false),
        isError: ref(false)
      })
    })
  }
})

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  emits: ['click'],
  setup(_, { attrs, emit, slots }) {
    return () => h('button', { ...attrs, onClick: (event: Event) => emit('click', event) }, slots.default?.())
  }
})

describe('BoardDropdown', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mocks.route.name = null
    mocks.allBoards!.value = []
    mocks.subscribedBoards!.value = []
    mocks.allBoardsError!.value = false
  })

  it('updates keyboard items when async boards arrive after opening', async () => {
    mount(BoardDropdown, {
      props: {
        type: 'all',
        isOpen: true
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          BaseButton: BaseButtonStub
        }
      }
    })

    const keyboardStore = useKeyboardStore()
    expect(keyboardStore.dropdownItems).toEqual([])

    mocks.allBoards!.value = [
      { boardUrl: 'free', boardName: 'Free' },
      { boardUrl: 'notice', boardName: 'Notice' }
    ]
    await nextTick()

    expect(keyboardStore.openDropdownType).toBe('all')
    expect(keyboardStore.dropdownItems.map((item) => item.label)).toEqual(['Free', 'Notice'])
  })

  it('closes matching keyboard dropdown state when the menu closes', async () => {
    const wrapper = mount(BoardDropdown, {
      props: {
        type: 'subscription',
        isOpen: true
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          BaseButton: BaseButtonStub
        }
      }
    })

    const keyboardStore = useKeyboardStore()
    mocks.subscribedBoards!.value = [{ boardUrl: 'free', boardName: 'Free' }]
    await nextTick()
    expect(keyboardStore.openDropdownType).toBe('subscription')

    await wrapper.setProps({ isOpen: false })

    expect(keyboardStore.openDropdownType).toBeNull()
    expect(keyboardStore.dropdownItems).toEqual([])
  })

  it('marks the all spaces trigger as pressed on the all boards route', () => {
    mocks.route.name = 'all-boards'

    const wrapper = mount(BoardDropdown, {
      props: {
        type: 'all',
        isOpen: false
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          BaseButton: BaseButtonStub
        }
      }
    })

    const button = wrapper.get('button')
    expect(button.attributes('aria-pressed')).toBe('true')
    expect(button.classes()).toContain('is-active')
  })

  it('renders the localized board load error', () => {
    mocks.allBoardsError!.value = true

    const wrapper = mount(BoardDropdown, {
      props: { type: 'all', isOpen: true },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { BaseButton: BaseButtonStub },
      },
    })

    expect(wrapper.text()).toContain('board.loadFailed')
    expect(wrapper.text()).not.toContain('Unable to load spaces.')
  })
})

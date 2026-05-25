import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const routeParams = vi.hoisted(() => ({ emoticonId: '9' }))
const invalidateQueries = vi.hoisted(() => vi.fn())
const addToast = vi.hoisted(() => vi.fn())
const purchaseEmoticon = vi.hoisted(() => vi.fn())
const mutationOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
  useQuery: vi.fn((options: Record<string, unknown>) => {
    const key = options.queryKey as unknown[]
    if (Array.isArray(key) && key[2] === 'purchased') {
      return {
        data: ref({ purchased: false, price: 100 }),
        isLoading: ref(false),
        error: ref(null),
      }
    }

    return {
      data: ref({
        emoticonId: 9,
        creatorId: 2,
        isActive: true,
        price: 100,
        images: [],
      }),
      isLoading: ref(false),
      error: ref(null),
    }
  }),
  useMutation: vi.fn((options: Record<string, unknown>) => {
    mutationOptions.push(options)
    return {
      mutate: vi.fn(),
      isPending: ref(false),
    }
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getEmoticonData: vi.fn(),
    checkPurchaseStatusData: vi.fn(),
    purchaseEmoticon,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    user: { userId: 1 },
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast }),
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn(),
}))

vi.mock('@/composables/useToggleEmoticonVisibility', () => ({
  useToggleEmoticonVisibility: () => ({
    mutate: vi.fn(),
    isPending: ref(false),
  }),
}))

vi.mock('lucide-vue-next', () => {
  const Icon = { template: '<i />' }
  return {
    ArrowLeft: Icon,
    ShoppingCart: Icon,
    Tag: Icon,
    Calendar: Icon,
    User: Icon,
    TrendingUp: Icon,
    Pencil: Icon,
    EyeOff: Icon,
    Eye: Icon,
  }
})

vi.mock('@/components/common/ui/BaseButton.vue', () => ({
  default: {
    template: '<button type="button"><slot /></button>',
  },
}))

import EmoticonDetail from '../EmoticonDetail.vue'

describe('EmoticonDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mutationOptions.length = 0
  })

  it('invalidates user point cache after purchase success', () => {
    mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    const purchaseMutation = mutationOptions[0]
    expect(purchaseMutation).toBeDefined()

    ;(purchaseMutation.onSuccess as () => void)()

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', expect.any(Object)] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', expect.any(Object), 'purchased'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'points'] })
  })
})

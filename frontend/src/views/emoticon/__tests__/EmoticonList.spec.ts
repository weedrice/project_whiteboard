import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const queryOptions: Array<Record<string, unknown>> = []
  const searchAll = vi.fn()
  const getPopularEmoticons = vi.fn()
  const push = vi.fn()

  return {
    queryOptions,
    searchAll,
    getPopularEmoticons,
    push,
  }
})

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn((options: Record<string, unknown>) => {
    mocks.queryOptions.push(options)
    if (mocks.queryOptions.length === 1) {
      return {
        data: { __v_isRef: true, value: [] },
        isLoading: { __v_isRef: true, value: false },
      }
    }
    return {
      data: {
        __v_isRef: true,
        value: {
          content: [],
          totalPages: 0,
          totalElements: 0,
        },
      },
      isLoading: { __v_isRef: true, value: false },
    }
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.push,
  }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getPopularEmoticons: mocks.getPopularEmoticons,
    searchAll: mocks.searchAll,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: false,
  }),
}))

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn(),
}))

import EmoticonList from '../EmoticonList.vue'

const mountList = () => mount(EmoticonList, {
  global: {
    stubs: {
      RouterLink: true,
      BaseButton: {
        template: '<button type="button"><slot /></button>',
      },
      BaseInput: {
        props: ['modelValue'],
        template: `
          <label>
            <slot name="prefix" />
            <input :value="modelValue" />
            <slot name="suffix" />
          </label>
        `,
      },
    },
  },
})

const getListQuery = () => {
  const listQuery = mocks.queryOptions.find((option) => {
    const queryKey = option.queryKey
    return Array.isArray(queryKey) && queryKey[0] === 'emoticons' && queryKey[1] === 'list'
  })

  expect(listQuery).toBeDefined()
  return listQuery as { queryFn: () => Promise<unknown> }
}

describe('EmoticonList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.queryOptions.length = 0
    mocks.searchAll.mockResolvedValue({
      data: {
        data: {
          content: [],
          totalPages: 0,
          totalElements: 0,
        },
      },
    })
    mocks.getPopularEmoticons.mockResolvedValue({ data: { data: [] } })
  })

  it('renders latest, oldest, and popular sort buttons', () => {
    const wrapper = mountList()

    expect(wrapper.text()).toContain('최신순')
    expect(wrapper.text()).toContain('오래된순')
    expect(wrapper.text()).toContain('판매순')
  })

  it('passes oldest sortBy to the list query after selecting oldest', async () => {
    const wrapper = mountList()
    const listQuery = getListQuery()

    await listQuery.queryFn()
    expect(mocks.searchAll).toHaveBeenLastCalledWith(expect.objectContaining({ sortBy: 'latest' }))

    const oldestButton = wrapper.findAll('button')
      .find((button) => button.text() === '오래된순')
    expect(oldestButton).toBeTruthy()

    await oldestButton!.trigger('click')
    await listQuery.queryFn()

    expect(mocks.searchAll).toHaveBeenLastCalledWith(expect.objectContaining({ sortBy: 'oldest' }))
  })
})

import { mount } from '@vue/test-utils'
import { defineComponent, h, unref, type MaybeRefOrGetter } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { emoticonApiData } from '@/test/emoticonApiFixtures'

const mocks = vi.hoisted(() => {
  type QueryOptionMock = {
    queryKey?: MaybeRefOrGetter<unknown>
    queryFn?: () => Promise<unknown>
  }
  const queryOptions: QueryOptionMock[] = []
  const searchAll = vi.fn()
  const getPopularEmoticons = vi.fn()
  const push = vi.fn()
  const listPage = {
    content: [] as Array<Record<string, unknown>>,
    totalPages: 0,
    totalElements: 0,
  }

  return {
    queryOptions,
    searchAll,
    getPopularEmoticons,
    push,
    listPage,
  }
})

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn((options: { queryKey?: MaybeRefOrGetter<unknown>; queryFn?: () => Promise<unknown> }) => {
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
        value: mocks.listPage,
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

const BaseInputStub = defineComponent({
  props: {
    modelValue: String,
    label: String,
    hideLabel: Boolean,
    placeholder: String,
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots, attrs }) {
    return () => h('label', { ...attrs, class: props.hideLabel ? 'sr-only' : '' }, [
      props.label,
      slots.prefix?.(),
      h('input', {
        value: props.modelValue,
        placeholder: props.placeholder,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
      }),
      slots.suffix?.(),
    ])
  },
})

const mountList = () => mount(EmoticonList, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      RouterLink: true,
      BaseButton: {
        template: '<button type="button"><slot /></button>',
      },
      BaseInput: BaseInputStub,
    },
  },
})

const getListQuery = () => {
  const listQuery = mocks.queryOptions.find((option) => {
    const queryKey = unref(option.queryKey)
    return Array.isArray(queryKey) && queryKey[0] === 'emoticons' && queryKey[1] === 'list'
  })

  expect(listQuery).toBeDefined()
  return listQuery as { queryFn: () => Promise<unknown> }
}

describe('EmoticonList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.queryOptions.length = 0
    mocks.searchAll.mockResolvedValue(emoticonApiData(mocks.listPage))
    mocks.listPage.content = []
    mocks.listPage.totalPages = 0
    mocks.listPage.totalElements = 0
    mocks.getPopularEmoticons.mockResolvedValue(emoticonApiData([]))
  })

  it('renders latest, oldest, and popular sort buttons', () => {
    const wrapper = mountList()

    expect(wrapper.text()).toContain('최신순')
    expect(wrapper.text()).toContain('오래된순')
    expect(wrapper.text()).toContain('판매순')
  })

  it('labels the search type selector for assistive technology', () => {
    const wrapper = mountList()

    expect(wrapper.get('select').attributes('aria-label')).toBe('emoticon.search.typeLabel')
  })

  it('keeps a hidden label on the keyword search field', () => {
    const wrapper = mountList()
    const label = wrapper.get('label.sr-only')

    expect(label.text()).toContain('노비콘 검색')
    expect(wrapper.get('input[placeholder="검색어"]').attributes('placeholder')).toBe('검색어')
  })

  it('marks toggle buttons with aria-pressed state', () => {
    const wrapper = mountList()

    expect(wrapper.findAll('button[aria-pressed="true"]').length).toBeGreaterThan(0)
    expect(wrapper.findAll('button[aria-pressed="false"]').length).toBeGreaterThan(0)
  })

  it('renders emoticon cards as labelled buttons and navigates on click', async () => {
    mocks.listPage.content = [
      {
        emoticonId: 42,
        name: 'Happy Novi',
        creatorName: 'Noviis',
        thumbnailUrl: '',
        purchaseCount: 7,
      },
    ]
    mocks.listPage.totalElements = 1

    const wrapper = mountList()
    const cardButton = wrapper.get('button[aria-label="Happy Novi"]')

    expect(cardButton.text()).toContain('Happy Novi')

    await cardButton.trigger('click')

    expect(mocks.push).toHaveBeenCalledWith({
      name: 'emoticon-detail',
      params: { emoticonId: 42 },
    })
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

  it('renders a bounded pagination range for large page counts', () => {
    mocks.listPage.totalPages = 12

    const wrapper = mountList()
    const buttons = wrapper.findAll('button').map((button) => button.text())

    expect(buttons).toContain('1')
    expect(buttons).toContain('2')
    expect(buttons).toContain('12')
    expect(wrapper.text()).toContain('...')
    expect(buttons).not.toContain('6')
    expect(buttons).not.toContain('11')
  })

  it('ignores composing enter before running keyword search', async () => {
    const wrapper = mountList()
    const listQuery = getListQuery()
    const input = wrapper.get('input')

    await input.setValue('wave')
    await input.trigger('keyup.enter', { isComposing: true })
    await listQuery.queryFn()
    expect(mocks.searchAll.mock.calls.at(-1)?.[0]).not.toHaveProperty('keyword')

    await input.trigger('keyup.enter')
    await listQuery.queryFn()
    expect(mocks.searchAll).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'wave' }))
  })
})

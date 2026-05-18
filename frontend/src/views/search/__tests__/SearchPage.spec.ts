import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import SearchPage from '../SearchPage.vue'

const mocks = vi.hoisted(() => {
  const route: { query: Record<string, unknown> } = {
    query: {},
  }
  const useIntegratedSearch = vi.fn()

  return {
    route,
    useIntegratedSearch,
    t: (key: string, params?: Record<string, string>) => {
      if (key === 'search.noResultsFor') return `No results for ${params?.query}`
      return key
    },
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  createWebHistory: vi.fn(),
  createRouter: vi.fn(() => ({
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
    onError: vi.fn(),
    push: vi.fn(),
    currentRoute: {
      value: {
        fullPath: '/',
        meta: {},
      },
    },
  })),
  RouterLink: {
    props: ['to'],
    template: '<a><slot /></a>',
  },
}))

vi.mock('@/composables/useSearch', () => ({
  useSearch: () => ({
    useIntegratedSearch: mocks.useIntegratedSearch,
  }),
}))

describe('SearchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.route.query = {}
    mocks.useIntegratedSearch.mockReturnValue({
      data: ref({ posts: { content: [] }, boards: [] }),
      isLoading: ref(false),
    })
  })

  const mountPage = () => shallowMount(SearchPage, {
    global: {
      mocks: {
        $t: mocks.t,
      },
      stubs: {
        'router-link': true,
      },
    },
  })

  const integratedSearchParams = () => mocks.useIntegratedSearch.mock.calls[0][0] as Ref<{
    q: string
    page: number
    size: number
    t?: string
  }>

  it('uses q as the canonical search query', () => {
    mocks.route.query = { q: ' vue ', keyword: 'legacy', tag: 'tagged', t: '123' }

    mountPage()

    expect(integratedSearchParams().value).toEqual({
      q: 'vue',
      page: 0,
      size: 20,
      t: '123',
    })
  })

  it('keeps keyword and tag deep links working as q fallbacks', () => {
    mocks.route.query = { q: '   ', keyword: 'pinia' }
    mountPage()
    expect(integratedSearchParams().value.q).toBe('pinia')

    vi.clearAllMocks()
    mocks.useIntegratedSearch.mockReturnValue({
      data: ref({ posts: { content: [] }, boards: [] }),
      isLoading: ref(false),
    })
    mocks.route.query = { tag: ['vue'] }

    mountPage()

    expect(integratedSearchParams().value.q).toBe('vue')
  })
})

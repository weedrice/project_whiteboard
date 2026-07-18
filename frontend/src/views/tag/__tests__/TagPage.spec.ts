import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import TagPage from '../TagPage.vue'
import { useHead } from '@unhead/vue'

const routeState = vi.hoisted(() => ({
  params: { name: 'vue' } as Record<string, unknown>,
  query: {} as Record<string, unknown>,
}))

const routerReplace = vi.hoisted(() => vi.fn())

const tagApi = vi.hoisted(() => ({
  getPostsByTagName: vi.fn(),
  getPopularTags: vi.fn(),
}))

let mountedQueryClient: QueryClient | null = null

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => routeState,
    useRouter: () => ({ replace: routerReplace }),
  }
})

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn(),
}))

vi.mock('@/api/tag', () => ({ tagApi }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ sessionGeneration: 0 }),
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string, params?: Record<string, unknown>) => {
        if (!params) return key
        return `${key}:${Object.values(params).join(':')}`
      },
    }),
  }
})

const mountPage = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
  mountedQueryClient = queryClient

  return mount(TagPage, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      mocks: {
        $t: (key: string, params?: Record<string, unknown>) => {
          if (!params) return key
          return `${key}:${Object.values(params).join(':')}`
        },
      },
      stubs: {
        BaseSkeleton: true,
        ErrorState: true,
        Pagination: true,
        PostList: {
          props: ['posts', 'emptyDescription'],
          template: '<div data-testid="post-list" :data-empty-description="emptyDescription">{{ posts.length }}</div>',
        },
        RouterLink: RouterLinkStub,
        Tag: true,
      },
    },
  })
}

describe('TagPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.params = { name: 'vue' }
    routeState.query = {}
    routerReplace.mockReset()
    tagApi.getPostsByTagName.mockResolvedValue({
      data: {
        success: true,
        data: {
          content: [],
          page: 0,
          size: 20,
          totalElements: 12,
          totalPages: 2,
          hasNext: true,
          hasPrevious: false,
        },
      },
    })
    tagApi.getPopularTags.mockResolvedValue({
      data: {
        success: true,
        data: {
          tags: [
            { tagId: 1, tagName: 'vue', postCount: 12 },
            { tagId: 2, tagName: 'spring', postCount: 8 },
          ],
        },
      },
    })
  })

  it('registers tag posts under a single auth-session prefix', async () => {
    mountPage()
    await flushPromises()

    const keys = mountedQueryClient?.getQueryCache().getAll().map((query) => query.queryKey) ?? []
    expect(keys).toContainEqual(['session', 0, 'tags', 'vue', 'posts', {
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    }])
    expect(keys.some((key) => key.slice(0, 4).join(':') === 'session:0:session:0')).toBe(false)
  })

  it('loads tag posts with a zero-based API page and renders count and related tags', async () => {
    routeState.query = { page: '2' }

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    expect(tagApi.getPostsByTagName).toHaveBeenCalledWith(
      'vue',
      { page: 1, size: 20, sort: 'createdAt,desc' },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(wrapper.text()).toContain('#vue')
    expect(wrapper.text()).toContain('search.tagPostCount:12')
    expect(wrapper.text()).toContain('#spring')
    expect(wrapper.find('[data-testid="post-list"]').attributes('data-empty-description')).toBe('search.tagEmpty')
  })

  it('registers tag SEO with useHead', () => {
    mountPage()

    expect(useHead).toHaveBeenCalledWith(expect.objectContaining({
      title: expect.any(Object),
      meta: expect.any(Array),
    }))
  })

  it('replaces an out-of-range page with the last page returned by the API', async () => {
    routeState.query = { page: '999' }
    tagApi.getPostsByTagName.mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          content: [],
          page: 998,
          size: 20,
          totalElements: 45,
          totalPages: 3,
          hasNext: false,
          hasPrevious: true,
        },
      },
    })

    mountPage()
    await flushPromises()

    expect(routerReplace).toHaveBeenCalledWith({
      path: '/tag/vue',
      query: { page: '3' },
    })
  })

  it('removes an out-of-range page when the tag has no results', async () => {
    routeState.query = { page: '4' }
    tagApi.getPostsByTagName.mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          content: [],
          page: 3,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrevious: false,
        },
      },
    })

    mountPage()
    await flushPromises()

    expect(routerReplace).toHaveBeenCalledWith({ path: '/tag/vue', query: {} })
  })
})

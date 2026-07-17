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

const tagApi = vi.hoisted(() => ({
  getPostsByTagName: vi.fn(),
  getPopularTags: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => routeState,
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
})

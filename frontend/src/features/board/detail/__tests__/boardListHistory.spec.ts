import { computed, defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, RouterView, useRoute, useRouter, type Router } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useBoardListState } from '../useBoardListState'
import { useBoardDetailNavigation } from '../useBoardDetailNavigation'

vi.mock('@/composables/useKeyboardShortcuts', () => ({ useBoardDetailShortcuts: vi.fn() }))

let wrapper: VueWrapper | undefined
afterEach(() => wrapper?.unmount())

async function mountBoardRoute(initialPath: string) {
  let state!: ReturnType<typeof useBoardListState>
  let mounts = 0
  const resetNoticeState = vi.fn()
  const Board = defineComponent({
    setup() {
      mounts += 1
      const route = useRoute()
      const router = useRouter()
      state = useBoardListState(route, router)
      useBoardDetailNavigation({
        route,
        router,
        boardUrl: computed(() => String(route.params.boardUrl)),
        currentPostId: computed(() => route.params.postId as string | undefined),
        page: state.page,
        totalPages: computed(() => 10),
        board: ref(null),
        canWrite: computed(() => false),
        isSubscribePending: ref(false),
        handleSubscribe: async () => {},
        handlePageChange: state.handlePageChange,
        resetNoticeState,
      })
      return () => h('div', JSON.stringify(state.queryParams.value))
    },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/board/:boardUrl/', component: Board }],
  })
  await router.push(initialPath)
  await router.isReady()
  wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router] } })
  await flushPromises()
  return { router, state, resetNoticeState, getMounts: () => mounts }
}

async function traverseHistory(router: Router, direction: 'back' | 'forward') {
  await new Promise<void>((resolve) => {
    const stop = router.afterEach(() => { stop(); resolve() })
    router[direction]()
  })
  await flushPromises()
}

describe('board list router history', () => {
  it.each([
    { query: 'page=3&q=needle&type=TITLE', filters: { q: 'needle', searchType: 'TITLE' } },
    { query: 'page=3&categoryId=7', filters: { categoryId: 7 } },
    { query: 'page=3&concept=1', filters: { minLikes: 5 } },
  ])('restores $query across boards without remounting', async ({ query, filters }) => {
    const originalPath = `/board/alpha/?${query}`
    const { router, state, resetNoticeState, getMounts } = await mountBoardRoute(originalPath)
    const expected = { page: 2, size: 20, sort: 'createdAt,desc', ...filters }
    expect(state.queryParams.value).toEqual(expected)

    await router.push('/board/beta/')
    await flushPromises()
    expect(state.queryParams.value).toEqual({ page: 0, size: 20, sort: 'createdAt,desc' })
    await traverseHistory(router, 'back')

    expect(router.currentRoute.value.fullPath).toBe(originalPath)
    expect(getMounts()).toBe(1)
    expect(state.queryParams.value).toEqual(expected)
    expect(state.searchQuery.value).toBe('q' in filters ? filters.q : '')
    expect(resetNoticeState).toHaveBeenCalledTimes(2)

    await traverseHistory(router, 'forward')
    expect(router.currentRoute.value.fullPath).toBe('/board/beta/')
    expect(state.queryParams.value).toEqual({ page: 0, size: 20, sort: 'createdAt,desc' })
  })

  it('hydrates a destination filter while resetting only the previous board sort', async () => {
    const { router, state } = await mountBoardRoute('/board/alpha/')
    state.handleSortChange('viewCount,desc')
    await nextTick()
    await router.push('/board/beta/?page=2&categoryId=8')
    await flushPromises()

    expect(state.queryParams.value).toEqual({ page: 1, size: 20, sort: 'createdAt,desc', categoryId: 8 })
    state.handlePageChange(2, 10)
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ page: '3', categoryId: '8' })

    state.handleSortChange('likeCount,desc')
    await flushPromises()
    expect(state.queryParams.value).toEqual({ page: 0, size: 20, sort: 'likeCount,desc', categoryId: 8 })
    expect(router.currentRoute.value.query).toEqual({ categoryId: '8' })
  })
})

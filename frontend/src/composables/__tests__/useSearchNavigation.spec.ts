import { describe, expect, it, vi, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'
import { useSearchNavigation } from '../useSearchNavigation'
import type { BoardListItem } from '@/types'

const routerPush = vi.fn()
const invalidateQueries = vi.fn()
const routeState = vi.hoisted(() => ({
  name: 'home' as string,
  query: {} as Record<string, string>,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush,
  }),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({
    invalidateQueries,
  }),
}))

const createBoard = (overrides: Partial<BoardListItem> = {}): BoardListItem => ({
  boardId: 1,
  boardName: 'Vue',
  boardUrl: 'vue',
  description: 'Vue board',
  sortOrder: 1,
  subscriberCount: 3,
  postCount: 10,
  isSubscribed: false,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
  ...overrides,
})

describe('useSearchNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.name = 'home'
    routeState.query = {}
  })

  it('opens dropdown on non-empty query and routes search submit', async () => {
    const searchQuery = ref('')
    const showDropdown = ref(false)
    const collapse = vi.fn()
    const navigation = useSearchNavigation({
      filteredBoards: ref([createBoard()]),
      searchQuery,
      showDropdown,
      listboxId: 'boards',
      isMobile: ref(false),
      collapse,
    })

    searchQuery.value = ' Vue '
    await nextTick()
    navigation.handleSearch()

    expect(showDropdown.value).toBe(false)
    expect(collapse).not.toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: { q: 'Vue' },
    })
  })

  it('selects a board from keyboard navigation and clears the query', () => {
    const searchQuery = ref('vue')
    const showDropdown = ref(true)
    const collapse = vi.fn()
    const navigation = useSearchNavigation({
      filteredBoards: ref([createBoard({ boardUrl: 'vue-board' })]),
      searchQuery,
      showDropdown,
      listboxId: 'boards',
      isMobile: ref(true),
      collapse,
    })

    navigation.setSelectedIndex(0)
    navigation.handleDropdownKeyDown(new KeyboardEvent('keydown', { key: 'Enter' }))

    expect(showDropdown.value).toBe(false)
    expect(searchQuery.value).toBe('')
    expect(collapse).toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith('/board/vue-board')
  })

  it('invalidates integrated search when the same search query is submitted on the search route', () => {
    routeState.name = 'search'
    routeState.query = { q: 'Vue' }
    const navigation = useSearchNavigation({
      filteredBoards: ref([]),
      searchQuery: ref('Vue'),
      showDropdown: ref(true),
      listboxId: 'boards',
      isMobile: ref(false),
      collapse: vi.fn(),
    })

    navigation.handleSearch()

    expect(routerPush).not.toHaveBeenCalled()
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['search', 'integrated'] })
  })
})

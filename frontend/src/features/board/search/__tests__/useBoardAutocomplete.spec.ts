import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import { useBoardAutocomplete } from '../useBoardAutocomplete'
import type { BoardListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  boardsData: undefined as Ref<BoardListItem[]> | undefined,
  isError: undefined as Ref<boolean> | undefined,
  isFetching: undefined as Ref<boolean> | undefined,
  refetch: vi.fn(),
}))

vi.mock('@/composables/useDebounce', () => ({
  useDebounce: <T>(value: Ref<T>) => value,
}))

vi.mock('@/features/board/useBoard', async () => {
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  mocks.boardsData = ref([])
  mocks.isError = ref(false)
  mocks.isFetching = ref(false)

  return {
    useBoard: () => ({
      useBoards: () => ({
        data: mocks.boardsData,
        isError: mocks.isError,
        isFetching: mocks.isFetching,
        refetch: mocks.refetch,
      }),
    }),
  }
})

function board(boardName: string): BoardListItem {
  return {
    boardId: 1,
    boardName,
    boardUrl: boardName.toLowerCase(),
    description: '',
    sortOrder: 0,
    postCount: 0,
    subscriberCount: 0,
    isSubscribed: false,
    isActive: true,
    isPublic: true,
    subscriptionAccessible: true,
  }
}

describe('useBoardAutocomplete', () => {
  beforeEach(() => {
    mocks.boardsData!.value = [
      board('Free Board'),
      board('Notice'),
    ]
  })

  it('matches boards with trimmed case-insensitive queries', () => {
    const query = ref('  free  ')
    const autocomplete = useBoardAutocomplete(query)

    expect(autocomplete.filteredBoards.value.map(item => item.boardName)).toEqual(['Free Board'])
  })

  it('returns no suggestions for blank queries', () => {
    const query = ref('   ')
    const autocomplete = useBoardAutocomplete(query)

    expect(autocomplete.filteredBoards.value).toEqual([])
  })

  it('exposes the query error and retry state instead of disguising a failure as empty data', async () => {
    mocks.isError!.value = true
    mocks.isFetching!.value = true
    const autocomplete = useBoardAutocomplete(ref('free'))

    expect(autocomplete.isError.value).toBe(true)
    expect(autocomplete.isFetching.value).toBe(true)
    await autocomplete.retry()
    expect(mocks.refetch).toHaveBeenCalledOnce()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import { useBoardAutocomplete } from '../useBoardAutocomplete'
import type { BoardListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  boardsData: undefined as Ref<BoardListItem[]> | undefined,
}))

vi.mock('@/composables/useDebounce', () => ({
  useDebounce: <T>(value: Ref<T>) => value,
}))

vi.mock('@/features/board/useBoard', async () => {
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  mocks.boardsData = ref([])

  return {
    useBoard: () => ({
      useBoards: () => ({ data: mocks.boardsData }),
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
})

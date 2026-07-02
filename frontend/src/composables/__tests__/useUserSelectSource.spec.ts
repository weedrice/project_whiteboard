import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref, type Ref } from 'vue'
import { useUserSelectSource } from '../useUserSelectSource'
import type { PageResponse } from '@/types'

const mocks = vi.hoisted(() => ({
  adminUsersData: {
    __v_isRef: true,
    value: {
      content: [],
      number: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    } as PageResponse<never>,
  },
  boardCandidatesData: {
    __v_isRef: true,
    value: {
      content: [],
      number: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    } as PageResponse<never>,
  },
  adminParams: undefined as Ref<{ page: number; size: number; q: string }> | undefined,
  boardParams: undefined as Ref<{ page: number; size: number; q: string }> | undefined,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useUsers: (params: Ref<{ page: number; size: number; q: string }>) => {
      mocks.adminParams = params
      return {
        data: mocks.adminUsersData,
        isLoading: ref(false),
      }
    },
  }),
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardManagerCandidates: (
      _boardUrl: Ref<string>,
      params: Ref<{ page: number; size: number; q: string }>,
    ) => {
      mocks.boardParams = params
      return {
        data: mocks.boardCandidatesData,
        isLoading: ref(false),
      }
    },
  }),
}))

describe('useUserSelectSource', () => {
  beforeEach(() => {
    mocks.adminParams = undefined
    mocks.boardParams = undefined
  })

  it('trims admin user search params', () => {
    const searchQuery = ref('  admin  ')

    useUserSelectSource({
      isOpen: ref(true),
      selectionMode: ref('single'),
      source: ref('admin'),
      boardUrl: ref(''),
      excludeUserIds: ref([]),
      searchQuery,
    })

    expect(mocks.adminParams?.value).toEqual({ page: 0, size: 10, q: 'admin' })

    searchQuery.value = '   '
    expect(mocks.adminParams?.value.q).toBe('')
  })

  it('uses trimmed q for board manager candidate params', () => {
    const searchQuery = ref('  manager  ')

    useUserSelectSource({
      isOpen: ref(true),
      selectionMode: computed(() => 'single'),
      source: computed(() => 'board-manager-candidates'),
      boardUrl: ref('free'),
      excludeUserIds: ref([]),
      searchQuery,
    })

    expect(mocks.boardParams?.value.q).toBe('manager')
  })
})

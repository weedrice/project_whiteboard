import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ref } from 'vue'
import { useErrorLogListState } from '../useErrorLogListState'
import type { ErrorLogListItem, ErrorLogSearchParams, PageResponse } from '@/types'

const mocks = vi.hoisted(() => ({
  errorLogsData: undefined as Ref<PageResponse<ErrorLogListItem> | null> | undefined,
  statsData: undefined as Ref<unknown> | undefined,
  useErrorLogsParams: undefined as Ref<(ErrorLogSearchParams & { page: number, size: number })> | undefined,
}))

vi.mock('@/composables/useAdmin', async () => {
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  mocks.errorLogsData = ref(null)
  mocks.statsData = ref(null)

  return {
    useAdmin: () => ({
      useErrorLogs: (params: Ref<ErrorLogSearchParams & { page: number, size: number }>) => {
        mocks.useErrorLogsParams = params
        return { data: mocks.errorLogsData, isLoading: ref(false) }
      },
      useErrorLogStats: () => ({ data: mocks.statsData }),
    }),
  }
})

describe('useErrorLogListState', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useErrorLogsParams = undefined
  })

  it('normalizes padded filter strings before applying search params', () => {
    const state = useErrorLogListState()
    state.page.value = 3
    state.filterErrorType.value = '  BusinessException  '
    state.filterStartDate.value = '  2026-01-01  '
    state.filterEndDate.value = '  2026-01-31  '

    state.handleSearch()

    expect(state.page.value).toBe(0)
    expect(mocks.useErrorLogsParams?.value).toEqual(expect.objectContaining({
      errorType: 'BusinessException',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
    }))
  })

  it('omits blank string filters after trimming', () => {
    const state = useErrorLogListState()
    state.filterErrorType.value = '   '
    state.filterStartDate.value = '   '
    state.filterEndDate.value = '   '

    state.handleSearch()

    expect(mocks.useErrorLogsParams?.value).toEqual(expect.objectContaining({
      errorType: undefined,
      startDate: undefined,
      endDate: undefined,
    }))
  })
})

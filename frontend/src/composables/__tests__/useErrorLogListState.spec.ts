import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-07T12:00:00.000Z'))
    vi.clearAllMocks()
    mocks.useErrorLogsParams = undefined
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('initializes and resets to the last two weeks date range', () => {
    const state = useErrorLogListState()

    expect(state.filterStartDate.value).toBe('2026-06-23')
    expect(state.filterEndDate.value).toBe('2026-07-07')
    expect(mocks.useErrorLogsParams?.value).toEqual(expect.objectContaining({
      startDate: '2026-06-23',
      endDate: '2026-07-07',
    }))

    state.filterStartDate.value = '2026-01-01'
    state.filterEndDate.value = '2026-01-31'
    state.resetFilters()

    expect(state.filterStartDate.value).toBe('2026-06-23')
    expect(state.filterEndDate.value).toBe('2026-07-07')
    expect(mocks.useErrorLogsParams?.value).toEqual(expect.objectContaining({
      startDate: '2026-06-23',
      endDate: '2026-07-07',
    }))
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

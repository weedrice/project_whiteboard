import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { useReport } from '../useReport'
import { reportApi } from '@/api/report'
import { apiDataResponse } from '@/test/apiResponseFixtures'

const mocks = vi.hoisted(() => ({
  queryOptions: [] as Array<Record<string, unknown>>,
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn((options: Record<string, unknown>) => {
    mocks.queryOptions.push(options)
    return {
      data: ref(null),
      isLoading: ref(false),
      error: ref(null),
      refetch: vi.fn(),
    }
  }),
}))

vi.mock('@/api/report', () => ({
  reportApi: {
    getMyReports: vi.fn(),
  },
}))

describe('useReport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.queryOptions.length = 0
  })

  it('fetches my reports with paginated query state and abort signal', async () => {
    vi.mocked(reportApi.getMyReports).mockResolvedValueOnce(
      apiDataResponse<typeof reportApi.getMyReports>({ content: [{ reportId: 1 }], totalPages: 1 })
    )

    const params = ref({ page: 2, size: 15 })
    const { useMyReports } = useReport()
    useMyReports(params)

    const options = mocks.queryOptions.at(-1)!
    expect((options.queryKey as ReturnType<typeof computed>).value).toEqual(['reports', 'me', { page: 2, size: 15 }])

    const signal = new AbortController().signal
    const result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({ signal })

    expect(result).toEqual({ content: [{ reportId: 1 }], totalPages: 1 })
    expect(reportApi.getMyReports).toHaveBeenCalledWith({ page: 2, size: 15 }, { signal })
  })
})

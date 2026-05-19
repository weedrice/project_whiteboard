import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useCodeService } from '../useCodeService'
import { codeApi } from '@/api/code'
import { QUERY_STALE_TIME } from '@/utils/constants'

const mocks = vi.hoisted(() => {
    const queryOptions: Array<Record<string, unknown>> = []
    return { queryOptions }
})

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.queryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
        }
    }),
}))

vi.mock('@/api/code', () => ({
    codeApi: {
        getCodes: vi.fn(),
    },
}))

describe('useCodeService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
    })

    it('unwraps mapped code response data for components', async () => {
        vi.mocked(codeApi.getCodes).mockResolvedValueOnce({
            data: {
                success: true,
                data: [{ code: 'NOTICE', value: 'NOTICE', name: 'Notice', sortOrder: 1 }],
            },
        } as never)

        const { getCodes } = useCodeService()
        getCodes('BOARD_TYPE')

        const options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['codes', 'BOARD_TYPE'])
        expect(options.staleTime).toBe(QUERY_STALE_TIME.LONG)
        expect(options.gcTime).toBe(QUERY_STALE_TIME.DAY)

        const result = await (options.queryFn as () => Promise<unknown>)()

        expect(codeApi.getCodes).toHaveBeenCalledWith('BOARD_TYPE')
        expect(result).toEqual([{ code: 'NOTICE', value: 'NOTICE', name: 'Notice', sortOrder: 1 }])
    })
})

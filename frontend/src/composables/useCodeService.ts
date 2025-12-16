import { useQuery } from '@tanstack/vue-query'
import { codeApi } from '@/api/code'
import { QUERY_STALE_TIME } from '@/utils/constants'

export function useCodeService() {
    const getCodes = (typeCode: string) => {
        return useQuery({
            queryKey: ['codes', typeCode],
            queryFn: async () => {
                const { data } = await codeApi.getCodes(typeCode)
                return data
            },
            staleTime: QUERY_STALE_TIME.LONG, // 1 hour
            gcTime: QUERY_STALE_TIME.DAY // 24 hours (cacheTime renamed to gcTime in v5)
        })
    }

    return {
        getCodes
    }
}

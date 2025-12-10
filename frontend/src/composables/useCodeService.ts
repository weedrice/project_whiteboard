import { useQuery } from '@tanstack/vue-query'
import { codeApi } from '@/api/code'

export function useCodeService() {
    const getCodes = (typeCode: string) => {
        return useQuery({
            queryKey: ['codes', typeCode],
            queryFn: async () => {
                const { data } = await codeApi.getCodes(typeCode)
                return data
            },
            staleTime: 1000 * 60 * 60, // 1 hour
            gcTime: 1000 * 60 * 60 * 24 // 24 hours (cacheTime renamed to gcTime in v5)
        })
    }

    return {
        getCodes
    }
}

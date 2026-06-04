import { computed, type Ref } from 'vue'
import { useQuery, type QueryFunctionContext } from '@tanstack/vue-query'
import { reportApi } from '@/api/report'
import { withQuerySignal } from '@/utils/querySignal'

interface PaginationParams {
  page?: number
  size?: number
}

export function useReport() {
  const useMyReports = (params?: Ref<PaginationParams>) => {
    return useQuery({
      queryKey: computed(() => ['reports', 'me', params?.value ?? {}]),
      queryFn: async (context: QueryFunctionContext) => {
        const { data } = await reportApi.getMyReports(params?.value ?? {}, withQuerySignal(undefined, context))
        return data.data
      },
    })
  }

  return {
    useMyReports,
  }
}

import { computed, type Ref } from 'vue'
import { useQuery, type QueryFunctionContext } from '@tanstack/vue-query'
import { reportApi } from '@/api/report'
import type { AxiosRequestConfig } from 'axios'

interface PaginationParams {
  page?: number
  size?: number
}

function withQuerySignal(config: AxiosRequestConfig | undefined, queryContext?: QueryFunctionContext): AxiosRequestConfig {
  return {
    ...config,
    signal: config?.signal ?? queryContext?.signal,
  }
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

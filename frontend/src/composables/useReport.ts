import type { Ref } from 'vue'
import { useQuery, type QueryFunctionContext } from '@tanstack/vue-query'
import { reportApi } from '@/api/report'
import { unwrapAxiosApiData } from '@/api/response'
import { withQuerySignal } from '@/utils/querySignal'
import { reportQueryKeys, type ReportQueryPaginationParams } from '@/composables/reportQueryKeys'

type PaginationParams = ReportQueryPaginationParams

export function useReport() {
  const useMyReports = (params?: Ref<PaginationParams>) => {
    return useQuery({
      queryKey: reportQueryKeys.myReports(params),
      queryFn: async (context: QueryFunctionContext) => {
        return unwrapAxiosApiData(await reportApi.getMyReports(params?.value ?? {}, withQuerySignal(undefined, context)))
      },
    })
  }

  return {
    useMyReports,
  }
}

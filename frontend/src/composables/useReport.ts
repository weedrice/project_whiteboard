import type { Ref } from 'vue'
import { reportApi } from '@/api/report'
import { withQuerySignal } from '@/utils/querySignal'
import { useApiQuery } from '@/composables/useApiQuery'
import { reportQueryKeys, type ReportQueryPaginationParams } from '@/composables/reportQueryKeys'

type PaginationParams = ReportQueryPaginationParams

export function useReport() {
  const useMyReports = (params?: Ref<PaginationParams>) => {
    return useApiQuery({
      queryKey: reportQueryKeys.myReports(params),
      request: (context) => reportApi.getMyReports(params?.value ?? {}, withQuerySignal(undefined, context)),
    })
  }

  return {
    useMyReports,
  }
}

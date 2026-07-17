import { computed, type Ref } from 'vue'
import { reportApi } from '@/api/report'
import { withQuerySignal } from '@/utils/querySignal'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { reportQueryKeys, type ReportQueryPaginationParams } from '@/features/user/reports/reportQueryKeys'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'

type PaginationParams = ReportQueryPaginationParams

export function useReport() {
  const useMyReports = (params?: Ref<PaginationParams>) => {
    return useApiPageQuery({
      queryKey: computed(() => reportQueryKeys.myReports(params?.value)),
      request: (context) => reportApi.getMyReports(params?.value ?? {}, withQuerySignal(undefined, context)),
      meta: AUTH_SCOPED_QUERY_META,
    })
  }

  return {
    useMyReports,
  }
}

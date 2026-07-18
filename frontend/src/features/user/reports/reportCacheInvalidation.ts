import type { QueryClient } from '@tanstack/vue-query'
import { sessionQueryKey } from '@/queryAuthScope'
import { reportQueryKeys } from '@/features/user/reports/reportQueryKeys'

export function invalidateMyReportCaches(queryClient: QueryClient, sessionGeneration: number) {
  return queryClient.invalidateQueries({
    queryKey: sessionQueryKey(sessionGeneration, reportQueryKeys.myReportsRoot),
  })
}

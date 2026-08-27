import { commonCodeApi, type CommonCodeDetail } from '@/api/commonCode'
import { useApiQuery } from '@/composables/useApiQuery'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { computed, type ComputedRef, type Ref } from 'vue'

export const COMMON_CODE_TYPES = {
  REPORT_REASON: 'REPORT_REASON',
  POINT_CHANGE_TYPE: 'POINT_CHANGE_TYPE',
  SANCTION_TYPE: 'SANCTION_TYPE',
  REPORT_STATUS: 'REPORT_STATUS',
  TARGET_TYPE: 'TARGET_TYPE',
  NOTIFICATION_TYPE: 'NOTIFICATION_TYPE',
  ITEM_TYPE: 'ITEM_TYPE',
} as const

export const commonCodeDetailQueryKey = (typeCode: string) => [
  'common-codes',
  typeCode,
  'details',
] as const

interface CommonCodeDetailQueryOptions {
  strict?: boolean
  enabled?: boolean | Ref<boolean> | ComputedRef<boolean>
}

export function useCommonCodeDetails(
  typeCode: string,
  options: CommonCodeDetailQueryOptions = {},
) {
  const strict = options.strict === true
  return useApiQuery<CommonCodeDetail[]>({
    queryKey: commonCodeDetailQueryKey(typeCode),
    request: ({ signal }) => commonCodeApi.getDetails(typeCode, {
      signal,
      skipGlobalErrorHandler: true,
    }),
    enabled: options.enabled,
    staleTime: strict ? QUERY_STALE_TIME.SHORT : QUERY_STALE_TIME.LONG,
    refetchInterval: strict ? QUERY_STALE_TIME.SHORT : undefined,
    refetchOnMount: strict ? 'always' : undefined,
    refetchOnWindowFocus: strict ? 'always' : undefined,
    refetchOnReconnect: strict ? 'always' : undefined,
    retry: false,
  })
}

function supportedDetails<T extends string>(
  details: CommonCodeDetail[],
  supportedValues: readonly T[],
) {
  const supportedValueSet = new Set<string>(supportedValues)
  return details
    .filter((detail) => detail.isActive && supportedValueSet.has(detail.codeValue))
    .map((detail) => detail.codeValue as T)
}

export function useSupportedCommonCodeValues<T extends string>(
  typeCode: string,
  supportedValues: readonly T[],
) {
  const { data } = useCommonCodeDetails(typeCode)

  return computed<T[]>(() => {
    if (data.value === undefined) return [...supportedValues]
    return supportedDetails(data.value, supportedValues)
  })
}

export function useStrictSupportedCommonCodeValues<T extends string>(
  typeCode: string,
  supportedValues: readonly T[],
  options: Omit<CommonCodeDetailQueryOptions, 'strict'> = {},
) {
  const query = useCommonCodeDetails(typeCode, { ...options, strict: true })
  const values = computed<T[]>(() => {
    if (query.data.value === undefined) return []
    return supportedDetails(query.data.value, supportedValues)
  })
  const isReady = computed(() => (
    query.data.value !== undefined
    && !query.isFetching.value
    && !query.isError.value
  ))

  return {
    values,
    isReady,
    isLoading: query.isLoading,
    isValidating: query.isFetching,
    isError: query.isError,
    refetch: query.refetch,
  }
}

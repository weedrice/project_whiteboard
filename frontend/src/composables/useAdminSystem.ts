import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { unwrapAxiosApiData } from '@/api/response'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import {
    callAdminApiWithOptionalConfig,
    useAdminDataQuery,
    useAdminNullableDataQuery,
    useAdminPageQuery,
} from '@/composables/adminApiQuery'
import type { ConfigCreateData } from '@/composables/adminComposableTypes'
import type {
    ErrorLogDetail,
    ErrorLogListItem,
    ErrorLogSearchParams,
    ErrorLogStats,
} from '@/types'

export function useAdminSystem(queryClient: QueryClient) {
    const useConfigs = () => {
        return useAdminDataQuery(
            adminQueryKeys.configs,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getConfigs, () => adminApi.getConfigs()),
        )
    }

    const useUpdateConfig = () => {
        return useMutation({
            mutationFn: ({ key, value, description }: { key: string, value: string, description?: string }) => adminApi.updateConfig(key, value, description),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.configs })
        })
    }

    const useCreateConfig = () => {
        return useMutation({
            mutationFn: (data: ConfigCreateData) => adminApi.createConfig(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.configs })
        })
    }

    const useDeleteConfig = () => {
        return useMutation({
            mutationFn: (key: string) => adminApi.deleteConfig(key),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.configs })
        })
    }

    const useDashboardStats = () => {
        return useAdminDataQuery(
            adminQueryKeys.stats,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getDashboardStats, () => adminApi.getDashboardStats()),
        )
    }

    const useErrorLogs = (params: Ref<ErrorLogSearchParams>) => {
        return useAdminPageQuery<ErrorLogListItem>(
            adminQueryKeys.errorLogs(params),
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getErrorLogs(params.value, requestConfig),
                () => adminApi.getErrorLogs(params.value),
            )
        )
    }

    function useErrorLog(errorLogId: Ref<number | null>): ReturnType<typeof useAdminNullableDataQuery<ErrorLogDetail>>
    function useErrorLog(): { mutateAsync: (errorLogId: number) => Promise<ErrorLogDetail> }
    function useErrorLog(errorLogId?: Ref<number | null>) {
        if (errorLogId) {
            return useAdminNullableDataQuery<ErrorLogDetail>(
                adminQueryKeys.errorLogDetail(errorLogId),
                (config) => {
                    if (errorLogId.value === null) {
                        return null
                    }

                    return callAdminApiWithOptionalConfig(
                        config,
                        (requestConfig) => adminApi.getErrorLog(errorLogId.value as number, requestConfig),
                        () => adminApi.getErrorLog(errorLogId.value as number),
                    )
                },
                computed(() => errorLogId.value !== null)
            )
        }

        return {
            mutateAsync: (selectedErrorLogId: number) => queryClient.fetchQuery({
                queryKey: adminQueryKeys.errorLogDetailById(selectedErrorLogId),
                queryFn: async ({ signal }) => {
                    const response = callAdminApiWithOptionalConfig(
                        signal ? { signal } : undefined,
                        (requestConfig) => adminApi.getErrorLog(selectedErrorLogId, requestConfig),
                        () => adminApi.getErrorLog(selectedErrorLogId),
                    )
                    return unwrapAxiosApiData(await response) as ErrorLogDetail
                },
            }),
        }
    }

    const useResolveErrorLog = () => {
        return useMutation({
            mutationFn: ({ errorLogId, data }: { errorLogId: number, data?: { memo?: string } }) => adminApi.resolveErrorLog(errorLogId, data),
            onSuccess: (_data, variables) => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.errorLogsRoot })
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.errorLogStats })
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.errorLogDetailById(variables.errorLogId) })
            }
        })
    }

    const useErrorLogStats = () => {
        return useAdminDataQuery<ErrorLogStats>(
            adminQueryKeys.errorLogStats,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getErrorLogStats, () => adminApi.getErrorLogStats())
        )
    }

    return {
        useConfigs,
        useUpdateConfig,
        useCreateConfig,
        useDeleteConfig,
        useDashboardStats,
        useErrorLogs,
        useErrorLog,
        useResolveErrorLog,
        useErrorLogStats,
    }
}

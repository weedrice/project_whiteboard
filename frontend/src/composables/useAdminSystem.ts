import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { unwrapAxiosApiData } from '@/api/response'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import {
    invalidateAdminConfigCaches,
    invalidateAdminErrorLogCaches,
} from '@/features/admin/queries/adminCacheInvalidation'
import {
    callAdminApiWithOptionalConfig,
    useAdminDataQuery,
    useAdminNullableDataQuery,
    useAdminPageQuery,
} from '@/features/admin/queries/adminApiQuery'
import type { ConfigCreateData } from '@/api/admin'
import type {
    DeepDashboardStats,
    ErrorLogDetail,
    ErrorLogListItem,
    ErrorLogSearchParams,
    ErrorLogStats,
    ModerationAuditLog,
    ModerationAuditSearchParams,
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
            onSuccess: () => invalidateAdminConfigCaches(queryClient)
        })
    }

    const useCreateConfig = () => {
        return useMutation({
            mutationFn: (data: ConfigCreateData) => adminApi.createConfig(data),
            onSuccess: () => invalidateAdminConfigCaches(queryClient)
        })
    }

    const useDeleteConfig = () => {
        return useMutation({
            mutationFn: (key: string) => adminApi.deleteConfig(key),
            onSuccess: () => invalidateAdminConfigCaches(queryClient)
        })
    }

    const useDashboardStats = () => {
        return useAdminDataQuery(
            adminQueryKeys.stats,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getDashboardStats, () => adminApi.getDashboardStats()),
        )
    }

    const useDeepDashboardStats = (days: Ref<30 | 90>) => {
        return useAdminDataQuery<DeepDashboardStats>(
            adminQueryKeys.deepStats(days),
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getDeepDashboardStats(days.value, requestConfig),
                () => adminApi.getDeepDashboardStats(days.value),
            ),
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

    const useModerationAudits = (params: Ref<ModerationAuditSearchParams>) => {
        return useAdminPageQuery<ModerationAuditLog>(
            adminQueryKeys.moderationAudits(params),
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getModerationAudits(params.value, requestConfig),
                () => adminApi.getModerationAudits(params.value),
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
                invalidateAdminErrorLogCaches(queryClient, variables.errorLogId)
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
        useDeepDashboardStats,
        useModerationAudits,
        useErrorLogs,
        useErrorLog,
        useResolveErrorLog,
        useErrorLogStats,
    }
}

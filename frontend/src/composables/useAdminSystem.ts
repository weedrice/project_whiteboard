import { useMutation, type QueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { unwrapAxiosApiData } from '@/api/response'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import {
    useAdminDataQuery,
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
        return useAdminDataQuery(adminQueryKeys.configs, () => adminApi.getConfigs())
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
        return useAdminDataQuery(adminQueryKeys.stats, () => adminApi.getDashboardStats())
    }

    const useErrorLogs = (params: Ref<ErrorLogSearchParams>) => {
        return useAdminPageQuery<ErrorLogListItem>(
            adminQueryKeys.errorLogs(params),
            () => adminApi.getErrorLogs(params.value)
        )
    }

    const useErrorLog = () => {
        return useMutation({
            mutationFn: async (errorLogId: number) => {
                return unwrapAxiosApiData(await adminApi.getErrorLog(errorLogId)) as ErrorLogDetail
            }
        })
    }

    const useResolveErrorLog = () => {
        return useMutation({
            mutationFn: ({ errorLogId, data }: { errorLogId: number, data?: { memo?: string } }) => adminApi.resolveErrorLog(errorLogId, data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.errorLogsRoot })
        })
    }

    const useErrorLogStats = () => {
        return useAdminDataQuery<ErrorLogStats>(adminQueryKeys.errorLogStats, () => adminApi.getErrorLogStats())
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

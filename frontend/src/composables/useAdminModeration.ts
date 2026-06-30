import { useMutation, type QueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import { type AdminApiRequestConfig, useAdminPageQuery } from '@/composables/adminApiQuery'
import type {
    IpBlockData,
    ReportResolveData,
    ReportSearchParams,
} from '@/composables/adminComposableTypes'
import type { IpBlock, Report } from '@/types'

const withConfig = <T>(
    config: AdminApiRequestConfig | undefined,
    requestWithConfig: (config: AdminApiRequestConfig) => T,
    requestWithoutConfig: () => T,
) => (config ? requestWithConfig(config) : requestWithoutConfig())

export function useAdminModeration(queryClient: QueryClient) {
    const useReports = (params: Ref<ReportSearchParams>) => {
        return useAdminPageQuery<Report>(
            adminQueryKeys.reports(params),
            (config) => withConfig(
                config,
                (requestConfig) => adminApi.getReports(params.value, requestConfig),
                () => adminApi.getReports(params.value),
            )
        )
    }

    const useResolveReport = () => {
        return useMutation({
            mutationFn: ({ reportId, data }: { reportId: string | number, data: ReportResolveData }) => adminApi.resolveReport(reportId, data),
            onSettled: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.reportsRoot })
        })
    }

    const useIpBlocks = (params: Ref<{ page?: number, size?: number }>) => {
        return useAdminPageQuery<IpBlock>(
            adminQueryKeys.ipBlocks(params),
            (config) => withConfig(
                config,
                (requestConfig) => adminApi.getIpBlocks(params.value, requestConfig),
                () => adminApi.getIpBlocks(params.value),
            )
        )
    }

    const useBlockIp = () => {
        return useMutation({
            mutationFn: (data: IpBlockData) => adminApi.blockIp(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.ipBlocksRoot })
        })
    }

    const useUnblockIp = () => {
        return useMutation({
            mutationFn: (ipAddress: string) => adminApi.unblockIp(ipAddress),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.ipBlocksRoot })
        })
    }

    return {
        useReports,
        useResolveReport,
        useIpBlocks,
        useBlockIp,
        useUnblockIp,
    }
}

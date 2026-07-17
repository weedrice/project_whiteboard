import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import {
    invalidateAdminIpBlockCaches,
    invalidateAdminReportCaches,
} from '@/features/admin/queries/adminCacheInvalidation'
import { callAdminApiWithOptionalConfig, useAdminPageQuery } from '@/features/admin/queries/adminApiQuery'
import type {
    IpBlockData,
    ReportResolveData,
    ReportSearchParams,
} from '@/api/admin'
import type { IpBlock, Report } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { captureSessionGeneration, isSessionGenerationCurrent } from '@/queryAuthScope'

export function useAdminModeration(queryClient: QueryClient) {
    const authStore = useAuthStore()
    const captureMutationSession = () => ({
        sessionGeneration: captureSessionGeneration(authStore),
    })
    const isCurrentMutation = (
        context?: { sessionGeneration: number },
    ): context is { sessionGeneration: number } =>
        context !== undefined
        && isSessionGenerationCurrent(authStore, context.sessionGeneration)

    const useReports = (params: Ref<ReportSearchParams>) => {
        return useAdminPageQuery<Report>(
            computed(() => adminQueryKeys.reports(params.value)),
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getReports(params.value, requestConfig),
                () => adminApi.getReports(params.value),
            )
        )
    }

    const useResolveReport = () => {
        return useMutation({
            mutationFn: ({ reportId, data }: { reportId: string | number, data: ReportResolveData }) => adminApi.resolveReport(reportId, data),
            onMutate: captureMutationSession,
            onSettled: (_data, _error, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminReportCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useIpBlocks = (params: Ref<{ page?: number, size?: number }>) => {
        return useAdminPageQuery<IpBlock>(
            computed(() => adminQueryKeys.ipBlocks(params.value)),
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getIpBlocks(params.value, requestConfig),
                () => adminApi.getIpBlocks(params.value),
            )
        )
    }

    const useBlockIp = () => {
        return useMutation({
            mutationFn: (data: IpBlockData) => adminApi.blockIp(data),
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminIpBlockCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useUnblockIp = () => {
        return useMutation({
            mutationFn: (ipAddress: string) => adminApi.unblockIp(ipAddress),
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminIpBlockCaches(queryClient, context.sessionGeneration)
            },
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

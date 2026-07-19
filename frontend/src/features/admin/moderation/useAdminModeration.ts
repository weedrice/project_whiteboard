import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import {
    invalidateAdminIpBlockCaches,
} from '@/features/admin/queries/adminCacheInvalidation'
import { callAdminApiWithOptionalConfig, useAdminPageQuery } from '@/features/admin/queries/adminApiQuery'
import type {
    IpBlockData,
    ReportResolveData,
    ReportSearchParams,
} from '@/api/admin'
import type { IpBlock, Report } from '@/types'
import type { PageResponse } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { captureSessionGeneration, isSessionGenerationCurrent, sessionQueryKey } from '@/queryAuthScope'
import { unwrapAxiosApiData } from '@/api/response'

function isReportPage(value: unknown): value is PageResponse<Report> {
    return !!value && typeof value === 'object' && Array.isArray((value as PageResponse<Report>).content)
}

function replaceReport(page: PageResponse<Report> | undefined, updatedReport: Report) {
    if (!isReportPage(page)) return page
    let changed = false
    const content = page.content.map((report) => {
        if (report.reportId !== updatedReport.reportId) return report
        changed = true
        return updatedReport
    })
    return changed ? { ...page, content } : page
}

function removeReport(page: PageResponse<Report> | undefined, reportId: number) {
    if (!isReportPage(page) || !page.content.some((report) => report.reportId === reportId)) return page
    const content = page.content.filter((report) => report.reportId !== reportId)
    const totalElements = Math.max(0, page.totalElements - 1)
    const totalPages = page.size > 0 ? Math.ceil(totalElements / page.size) : 0
    return {
        ...page,
        content,
        totalElements,
        totalPages,
        empty: content.length === 0,
        last: page.number >= Math.max(0, totalPages - 1),
    }
}

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
            onSuccess: (response, _variables, context) => {
                if (!isCurrentMutation(context)) return
                const updatedReport = unwrapAxiosApiData(response)
                const reportRoot = sessionQueryKey(context.sessionGeneration, adminQueryKeys.reportsRoot)
                const hasMismatchedStatusFilter = (query: { queryKey: readonly unknown[] }) => {
                    const queryKey = query.queryKey
                    if (!reportRoot.every((segment, index) => queryKey[index] === segment)) return false
                    const params = queryKey[reportRoot.length]
                    return !!params
                        && typeof params === 'object'
                        && 'status' in params
                        && typeof params.status === 'string'
                        && params.status !== updatedReport.status
                }

                queryClient.setQueriesData<PageResponse<Report>>(
                    { predicate: hasMismatchedStatusFilter },
                    (page) => removeReport(page, updatedReport.reportId),
                )
                queryClient.setQueriesData<PageResponse<Report>>(
                    {
                        predicate: (query) => (
                            reportRoot.every((segment, index) => query.queryKey[index] === segment)
                            && !hasMismatchedStatusFilter(query)
                        ),
                    },
                    (page) => replaceReport(page, updatedReport),
                )
                void queryClient.invalidateQueries({ predicate: hasMismatchedStatusFilter })
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

export interface ReportQueryPaginationParams {
    page?: number
    size?: number
}

export const reportQueryKeys = {
    myReportsRoot: ['reports', 'me'] as const,
    myReports: (params: ReportQueryPaginationParams = {}) =>
        ['reports', 'me', { ...params }] as const,
}

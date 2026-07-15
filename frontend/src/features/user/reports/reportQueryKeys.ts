export interface ReportQueryPaginationParams {
    page?: number
    size?: number
}

export const reportQueryKeys = {
    myReports: (params: ReportQueryPaginationParams = {}) =>
        ['reports', 'me', { ...params }] as const,
}

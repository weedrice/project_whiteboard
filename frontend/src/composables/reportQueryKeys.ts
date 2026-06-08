import { computed, type Ref } from 'vue'

export interface ReportQueryPaginationParams {
    page?: number
    size?: number
}

export const reportQueryKeys = {
    myReports: (params?: Ref<ReportQueryPaginationParams>) =>
        computed(() => ['reports', 'me', params?.value ?? {}] as const),
}

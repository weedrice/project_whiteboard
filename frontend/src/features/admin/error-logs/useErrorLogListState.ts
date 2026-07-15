import { ref } from 'vue'
import { useAdmin } from '@/features/admin/useAdmin'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { optionalTrimmedText } from '@/utils/inputNormalization'
import type { ErrorLogSearchParams } from '@/types'

function toDateString(date: Date): string {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

function getDefaultDateRange() {
    const today = new Date()
    const twoWeeksAgo = new Date(today)
    twoWeeksAgo.setDate(today.getDate() - 14)

    return {
        defaultEndDate: toDateString(today),
        defaultStartDate: toDateString(twoWeeksAgo)
    }
}

export function useErrorLogListState() {
    const { useErrorLogs, useErrorLogStats } = useAdmin()
    const { defaultStartDate, defaultEndDate } = getDefaultDateRange()

    const filterErrorType = ref('')
    const filterHttpStatus = ref<number | undefined>(undefined)
    const filterIsResolved = ref('')
    const filterStartDate = ref(defaultStartDate)
    const filterEndDate = ref(defaultEndDate)

    const filterParams = ref<ErrorLogSearchParams>({
        errorType: undefined,
        httpStatus: undefined,
        isResolved: undefined,
        startDate: defaultStartDate,
        endDate: defaultEndDate
    })

    const {
        page,
        size,
        params,
        resetPage,
    } = usePaginatedQueryState({
        initialSize: 20,
        extraParams: filterParams,
    })

    function handleSearch() {
        filterParams.value = {
            errorType: optionalTrimmedText(filterErrorType.value),
            httpStatus: filterHttpStatus.value || undefined,
            isResolved: filterIsResolved.value || undefined,
            startDate: optionalTrimmedText(filterStartDate.value),
            endDate: optionalTrimmedText(filterEndDate.value)
        }
        resetPage()
    }

    function resetFilters() {
        filterErrorType.value = ''
        filterHttpStatus.value = undefined
        filterIsResolved.value = ''
        filterStartDate.value = defaultStartDate
        filterEndDate.value = defaultEndDate
        handleSearch()
    }

    const { data: errorLogsData, isLoading } = useErrorLogs(params)
    const { data: statsData } = useErrorLogStats()

    const {
        items: errorLogs,
        totalPages,
        totalElements,
    } = usePageResponseState(errorLogsData, page)

    return {
        errorLogs,
        filterEndDate,
        filterErrorType,
        filterHttpStatus,
        filterIsResolved,
        filterStartDate,
        handleSearch,
        isLoading,
        page,
        resetFilters,
        size,
        statsData,
        totalElements,
        totalPages
    }
}

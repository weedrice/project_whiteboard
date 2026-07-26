import { ref } from 'vue'
import { useAdmin } from '@/features/admin/useAdmin'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { optionalTrimmedText } from '@/utils/inputNormalization'
import { toServerDateString } from '@/utils/date'
import type { ErrorLogSearchParams } from '@/types'

const TWO_WEEKS_MS = 14 * 24 * 60 * 60 * 1000

function getDefaultDateRange() {
    // 서버가 KST 날짜로 거르므로 범위도 서버 기준으로 만든다. 기기 기준으로 만들면
    // KST와 날짜가 다른 지역의 관리자에게 서버의 오늘 로그가 통째로 빠진다.
    const today = new Date()

    return {
        defaultEndDate: toServerDateString(today),
        defaultStartDate: toServerDateString(new Date(today.getTime() - TWO_WEEKS_MS))
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

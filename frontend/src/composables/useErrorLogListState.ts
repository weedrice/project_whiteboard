import { computed, ref } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
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

    const page = ref(0)
    const size = ref(20)
    const filterErrorType = ref('')
    const filterHttpStatus = ref<number | undefined>(undefined)
    const filterIsResolved = ref('')
    const filterStartDate = ref(defaultStartDate)
    const filterEndDate = ref(defaultEndDate)

    const searchParams = ref<ErrorLogSearchParams>({
        page: 0,
        size: 20,
        errorType: undefined,
        httpStatus: undefined,
        isResolved: undefined,
        startDate: defaultStartDate,
        endDate: defaultEndDate
    })

    const params = computed(() => ({
        ...searchParams.value,
        page: page.value
    }))

    function handleSearch() {
        searchParams.value = {
            page: 0,
            size: size.value,
            errorType: filterErrorType.value || undefined,
            httpStatus: filterHttpStatus.value || undefined,
            isResolved: filterIsResolved.value || undefined,
            startDate: filterStartDate.value || undefined,
            endDate: filterEndDate.value || undefined
        }
        page.value = 0
    }

    function resetFilters() {
        filterErrorType.value = ''
        filterHttpStatus.value = undefined
        filterIsResolved.value = ''
        filterStartDate.value = defaultStartDate
        filterEndDate.value = defaultEndDate
        page.value = 0
        handleSearch()
    }

    const { data: errorLogsData, isLoading } = useErrorLogs(params)
    const { data: statsData } = useErrorLogStats()

    const errorLogs = computed(() => errorLogsData.value?.content || [])
    const totalPages = computed(() => errorLogsData.value?.totalPages || 0)
    const totalElements = computed(() => errorLogsData.value?.totalElements || 0)

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

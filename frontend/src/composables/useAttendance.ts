import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { attendanceApi } from '@/api/attendance'
import { unwrapAxiosApiData } from '@/api/response'
import { attendanceQueryKeys } from '@/composables/attendanceQueryKeys'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'

export function useAttendance() {
    const queryClient = useQueryClient()

    const useMyAttendance = (enabled: Ref<boolean>, month?: Ref<string | undefined>) => useQuery({
        queryKey: computed(() => attendanceQueryKeys.me(month?.value)),
        queryFn: async (context?: { signal?: AbortSignal }) => unwrapAxiosApiData(
            await attendanceApi.getMyAttendance(month?.value, withQuerySignal(undefined, context))
        ),
        enabled,
    })

    const useCheckIn = () => useMutation({
        mutationFn: async () => unwrapAxiosApiData(await attendanceApi.checkIn()),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: attendanceQueryKeys.meRoot })
            queryClient.invalidateQueries({ queryKey: userQueryKeys.pointsRoot })
        },
    })

    return {
        useMyAttendance,
        useCheckIn,
    }
}

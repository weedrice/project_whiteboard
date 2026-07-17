import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { attendanceApi } from '@/api/attendance'
import { unwrapAxiosApiData } from '@/api/response'
import { attendanceQueryKeys } from '@/features/user/attendance/attendanceQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'
import { useAuthStore } from '@/stores/auth'
import {
    AUTH_SCOPED_QUERY_META,
    currentSessionQueryKey,
    isSessionGenerationCurrent,
    sessionQueryKey,
} from '@/queryAuthScope'

export function useAttendance() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()

    const useMyAttendance = (enabled: Ref<boolean>, month?: Ref<string | undefined>) => useQuery({
        queryKey: computed(() => currentSessionQueryKey(authStore, attendanceQueryKeys.me(month?.value))),
        queryFn: async (context?: { signal?: AbortSignal }) => unwrapAxiosApiData(
            await attendanceApi.getMyAttendance(month?.value, withQuerySignal(undefined, context))
        ),
        enabled,
        meta: AUTH_SCOPED_QUERY_META,
    })

    const useCheckIn = () => useMutation({
        onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
        mutationFn: async () => unwrapAxiosApiData(await attendanceApi.checkIn()),
        onSuccess: (_data, _variables, context) => {
            if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
            queryClient.invalidateQueries({
                queryKey: sessionQueryKey(context.sessionGeneration, attendanceQueryKeys.meRoot),
            })
            queryClient.invalidateQueries({
                queryKey: sessionQueryKey(context.sessionGeneration, userQueryKeys.pointsRoot),
            })
        },
    })

    return {
        useMyAttendance,
        useCheckIn,
    }
}

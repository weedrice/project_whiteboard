import { createReactiveAuthScopedQueryOptions } from '@/utils/authScopedQuery'
import { computed, getCurrentScope, onScopeDispose, type Ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import { attendanceApi } from '@/api/attendance'
import { unwrapAxiosApiData } from '@/api/response'
import { useAttendanceDay } from './useAttendanceDay'
import { attendanceQueryKeys } from '@/features/user/attendance/attendanceQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'
import { useAuthStore } from '@/stores/auth'
import {
    AUTH_SCOPED_QUERY_META,
    currentSessionQueryKey,
    isSessionGenerationCurrent,
    sessionQueryKey,
    subscribeAuthSessionBoundary,
} from '@/queryAuthScope'
import {
    captureAuthSessionIntent,
    throwIfAuthSessionIntentChanged,
} from '@/utils/authSessionIntent'

export function useAttendance() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()

    const useMyAttendance = (enabled: Ref<boolean>, month?: Ref<string | undefined>) => {
        const today = useAttendanceDay(enabled)
        return useQuery({
            ...createReactiveAuthScopedQueryOptions(authStore, computed(() => currentSessionQueryKey(
                authStore, attendanceQueryKeys.me(month?.value, today.value),
            )), async (context) => unwrapAxiosApiData(
                await attendanceApi.getMyAttendance(month?.value, withQuerySignal(undefined, context))
            )),
            enabled,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useCheckIn = () => {
        let activeController: AbortController | null = null
        const cancelActiveCheckIn = () => {
            activeController?.abort()
            activeController = null
        }

        if (getCurrentScope()) {
            const stopSessionBoundary = subscribeAuthSessionBoundary(cancelActiveCheckIn)
            onScopeDispose(() => {
                cancelActiveCheckIn()
                stopSessionBoundary()
            })
        }

        return useAuthScopedMutation({
            onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
            mutationFn: async (_variables: void, mutationContext) => {
                cancelActiveCheckIn()
                const controller = new AbortController()
                const intent = captureAuthSessionIntent(authStore)
                activeController = controller
                try {
                    const result = unwrapAxiosApiData(await attendanceApi.checkIn(mutationContext.withRequestConfig({
                        signal: controller.signal,
                        skipGlobalErrorHandler: true,
                    })))
                    throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
                    return result
                } finally {
                    if (activeController === controller) activeController = null
                }
            },
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
    }

    return {
        useMyAttendance,
        useCheckIn,
    }
}

import { useQueryClient } from '@tanstack/vue-query'
import { useAdminAccountManagement } from '@/composables/useAdminAccountManagement'
import { useAdminBoardManagement } from '@/composables/useAdminBoards'
import { useAdminModeration } from '@/composables/useAdminModeration'
import { useAdminSystem } from '@/composables/useAdminSystem'

export type {
    AdminCreateData,
    BoardCreateData,
    BoardManagerData,
    BoardUpdateData,
    ConfigCreateData,
    IpBlockData,
    ReportResolveData,
    ReportSearchParams,
    UserSearchParams,
} from '@/composables/adminComposableTypes'

export function useAdmin() {
    const queryClient = useQueryClient()

    return {
        ...useAdminAccountManagement(queryClient),
        ...useAdminModeration(queryClient),
        ...useAdminSystem(queryClient),
        ...useAdminBoardManagement(queryClient),
    }
}

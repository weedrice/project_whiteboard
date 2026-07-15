import { useQueryClient } from '@tanstack/vue-query'
import { useAdminAccountManagement } from '@/features/admin/users/useAdminAccountManagement'
import { useAdminBoardManagement } from '@/features/admin/boards/useAdminBoards'
import { useAdminModeration } from '@/features/admin/moderation/useAdminModeration'
import { useAdminSystem } from '@/features/admin/system/useAdminSystem'

export type {
    AdminCreateData,
    BoardManagerUpdateData as BoardManagerData,
    ConfigCreateData,
    IpBlockData,
    ReportResolveData,
    ReportSearchParams,
    UserSearchParams,
} from '@/api/admin'

export type {
    BoardCreateData,
    BoardUpdateData,
} from '@/types'

export function useAdmin() {
    const queryClient = useQueryClient()

    return {
        ...useAdminAccountManagement(queryClient),
        ...useAdminModeration(queryClient),
        ...useAdminSystem(queryClient),
        ...useAdminBoardManagement(queryClient),
    }
}

import { adminAccountApi } from '@/api/adminAccountApi'
import { adminBoardApi } from '@/api/adminBoardApi'
import { adminErrorLogApi } from '@/api/adminErrorLogApi'
import { adminModerationApi } from '@/api/adminModerationApi'
import { adminSystemApi } from '@/api/adminSystemApi'
import { adminUserApi } from '@/api/adminUserApi'

export {
    type AdminCreateData,
    type AdminRole,
    type BoardManagerUpdateData,
    type ConfigCreateData,
    type IpBlockData,
    type PaginationParams,
    type ReportResolveData,
    type ReportSearchParams,
    type SuperAdminData,
    type UserSearchParams,
} from '@/api/adminTypes'

export const adminApi = {
    ...adminAccountApi,
    ...adminUserApi,
    ...adminModerationApi,
    ...adminSystemApi,
    ...adminBoardApi,
    ...adminErrorLogApi,
}

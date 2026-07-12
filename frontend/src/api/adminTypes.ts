import type { AxiosRequestConfig } from 'axios'
import api from '@/api'

export type AdminRole = 'BOARD_ADMIN' | 'MODERATOR'

export interface AdminCreateData {
    loginId: string
    boardId: number
    role: AdminRole
}

export interface SuperAdminData {
    loginId: string
    reason: string
}

export interface IpBlockData {
    ipAddress: string
    reason: string
}

export interface PaginationParams {
    page?: number
    size?: number
    q?: string
}

export interface UserSearchParams extends PaginationParams {
    status?: string
    role?: string
    isEmailVerified?: boolean
    isSuperAdmin?: boolean
    isWithdrawn?: boolean
    createdFrom?: string
    createdTo?: string
    lastLoginFrom?: string
    lastLoginTo?: string
    sort?: string
}

export interface ReportResolveData {
    status: 'RESOLVED' | 'REJECTED'
    remark?: string
}

export interface ReportSearchParams {
    page?: number
    size?: number
}

export interface ConfigCreateData {
    key: string
    value: string
    description?: string
}

export interface BoardManagerUpdateData {
    loginId: string
}

export const getWithOptionalConfig = <T>(url: string, config?: AxiosRequestConfig) => (
    config ? api.get<T>(url, config) : api.get<T>(url)
)

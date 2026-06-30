import type { AdminRole } from '@/api/admin'

export interface AdminCreateData {
    loginId: string
    boardId: number
    role: AdminRole
}

export interface UserSearchParams {
    page?: number
    size?: number
    q?: string
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

export interface ReportSearchParams {
    page?: number
    size?: number
}

export interface ReportResolveData {
    status: 'RESOLVED' | 'REJECTED'
}

export interface IpBlockData {
    ipAddress: string
    reason: string
}

export interface ConfigCreateData {
    key: string
    value: string
    description?: string
}

export interface BoardCreateData {
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    agentUseYn?: boolean
    guidePrompt?: string
}

export interface BoardUpdateData {
    boardName?: string
    boardUrl?: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    isActive?: boolean
    agentUseYn?: boolean
    guidePrompt?: string
}

export interface BoardManagerData {
    loginId: string
}

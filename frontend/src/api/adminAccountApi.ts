import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, BoardAdminInfo, PageResponse, SuperAdminInfo } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import {
    getWithOptionalConfig,
    type AdminCreateData,
    type BoardManagerUpdateData,
    type PaginationParams,
    type SuperAdminData,
} from '@/api/adminTypes'

export const adminAccountApi = {
    getAdmins(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<BoardAdminInfo>>>('/admin/admins', { ...config, params })
    },
    createAdmin(data: AdminCreateData) {
        return api.post<ApiResponse<BoardAdminInfo>>('/admin/admins', data)
    },
    deactivateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${encodePathSegment(adminId)}/deactivate`)
    },
    activateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${encodePathSegment(adminId)}/activate`)
    },
    getBoardManager(boardId: number, config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<BoardAdminInfo | null>>(`/admin/boards/${encodePathSegment(boardId)}/manager`, config)
    },
    updateBoardManager(boardId: number, data: BoardManagerUpdateData) {
        return api.put<ApiResponse<BoardAdminInfo>>(`/admin/boards/${encodePathSegment(boardId)}/manager`, data)
    },
    getSuperAdmin(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<SuperAdminInfo[]>>('/admin/super', config)
    },
    activeSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/active', data)
    },
    deactivateSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/deactive', data)
    },
}

import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, BoardAdminInfo, PageResponse, SuperAdminInfo } from '@/types'
import { mapApiDataResponse } from '@/api/response'
import { normalizePageResponseItems, type PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'
import {
    getWithOptionalConfig,
    type AdminCreateData,
    type BoardManagerUpdateData,
    type PaginationParams,
    type SuperAdminData,
} from '@/api/adminTypes'

export interface BoardAdminInfoWire extends Omit<BoardAdminInfo, 'isActive'> {
    active: boolean
}

export interface SuperAdminInfoWire extends Omit<SuperAdminInfo, 'isSuperAdmin'> {
    superAdmin: boolean
}

export interface SuperAdminStatusResponse {
    loginId: string
    isSuperAdmin: boolean
}

interface SuperAdminStatusResponseWire extends Omit<SuperAdminStatusResponse, 'isSuperAdmin'> {
    superAdmin: boolean
}

export const normalizeBoardAdmin = (admin: BoardAdminInfoWire): BoardAdminInfo => ({
    ...admin,
    isActive: admin.active,
})

export const normalizeSuperAdmin = (admin: SuperAdminInfoWire): SuperAdminInfo => ({
    ...admin,
    isSuperAdmin: admin.superAdmin,
})

const normalizeSuperAdminStatus = (admin: SuperAdminStatusResponseWire): SuperAdminStatusResponse => ({
    ...admin,
    isSuperAdmin: admin.superAdmin,
})

export const adminAccountApi = {
    async getAdmins(params: PaginationParams, config?: AxiosRequestConfig) {
        const response = await api.get<ApiResponse<PageResponseRaw<BoardAdminInfoWire>>>('/admin/admins', { ...config, params })
        return mapApiDataResponse(response, (page): PageResponse<BoardAdminInfo> =>
            normalizePageResponseItems(page, normalizeBoardAdmin))
    },
    async createAdmin(data: AdminCreateData) {
        const response = await api.post<ApiResponse<BoardAdminInfoWire>>('/admin/admins', data)
        return mapApiDataResponse(response, normalizeBoardAdmin)
    },
    deactivateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${encodePathSegment(adminId)}/deactivate`)
    },
    activateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${encodePathSegment(adminId)}/activate`)
    },
    async getBoardManager(boardId: number, config?: AxiosRequestConfig) {
        const response = await getWithOptionalConfig<ApiResponse<BoardAdminInfoWire | null>>(`/admin/boards/${encodePathSegment(boardId)}/manager`, config)
        return mapApiDataResponse(response, (admin) => admin ? normalizeBoardAdmin(admin) : null)
    },
    async updateBoardManager(boardId: number, data: BoardManagerUpdateData) {
        const response = await api.put<ApiResponse<BoardAdminInfoWire>>(`/admin/boards/${encodePathSegment(boardId)}/manager`, data)
        return mapApiDataResponse(response, normalizeBoardAdmin)
    },
    async getSuperAdmin(config?: AxiosRequestConfig) {
        const response = await getWithOptionalConfig<ApiResponse<SuperAdminInfoWire[]>>('/admin/super', config)
        return mapApiDataResponse(response, (admins) => admins.map(normalizeSuperAdmin))
    },
    async activeSuperAdmin(data: SuperAdminData) {
        const response = await api.put<ApiResponse<SuperAdminStatusResponseWire>>('/admin/super/active', data)
        return mapApiDataResponse(response, normalizeSuperAdminStatus)
    },
    async deactivateSuperAdmin(data: SuperAdminData) {
        const response = await api.put<ApiResponse<SuperAdminStatusResponseWire>>('/admin/super/deactive', data)
        return mapApiDataResponse(response, normalizeSuperAdminStatus)
    },
}

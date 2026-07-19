import type { AxiosRequestConfig } from 'axios'
import api from '@/api'
import type { ApiResponse } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export interface CommonCode {
  typeCode: string
  typeName: string
  description?: string | null
  createdAt: string
  modifiedAt: string
}

export interface CommonCodeDetail {
  id: number
  typeCode: string
  codeValue: string
  codeName: string
  sortOrder: number
  isActive: boolean
}

export interface CommonCodePayload {
  typeCode: string
  typeName: string
  description?: string | null
}

export interface CommonCodeDetailPayload {
  codeValue: string
  codeName: string
  sortOrder: number
  isActive: boolean
}

export const commonCodeApi = {
  getAll: (config?: AxiosRequestConfig) =>
    api.get<ApiResponse<CommonCode[]>>('/common-codes', config),
  create: (data: CommonCodePayload) =>
    api.post<ApiResponse<CommonCode>>('/common-codes', data),
  update: (typeCode: string, data: CommonCodePayload) =>
    api.put<ApiResponse<CommonCode>>(`/common-codes/${encodePathSegment(typeCode)}`, data),
  getDetails: (typeCode: string, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<CommonCodeDetail[]>>(`/common-codes/${encodePathSegment(typeCode)}/details`, config),
  createDetail: (typeCode: string, data: CommonCodeDetailPayload) =>
    api.post<ApiResponse<CommonCodeDetail>>(`/common-codes/${encodePathSegment(typeCode)}/details`, data),
  updateDetail: (detailId: number, data: CommonCodeDetailPayload) =>
    api.put<ApiResponse<CommonCodeDetail>>(`/common-codes/details/${encodePathSegment(detailId)}`, data),
  deleteDetail: (detailId: number) =>
    api.delete<ApiResponse<void>>(`/common-codes/details/${encodePathSegment(detailId)}`),
}

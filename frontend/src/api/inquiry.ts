import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, PageResponse } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import type {
  InquiryCreateData,
  InquiryDetail,
  InquiryListParams,
  InquiryMessageCreateData,
  InquirySummary,
} from '@/types/inquiry'
import { encodePathSegment } from '@/utils/urlPath'

const inquiryPath = (inquiryId: string | number) => `/inquiries/${encodePathSegment(inquiryId)}`
const adminInquiryPath = (inquiryId: string | number) =>
  `/admin/support/inquiries/${encodePathSegment(inquiryId)}`

export const inquiryApi = {
  create: (data: InquiryCreateData, config?: AxiosRequestConfig) => api.post<ApiResponse<InquiryDetail>>('/inquiries', data, config),
  getMine: (params: InquiryListParams, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<PageResponse<InquirySummary> | PageResponseRaw<InquirySummary>>>('/inquiries', {
      ...config,
      params,
    }),
  getMineDetail: (inquiryId: string | number, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<InquiryDetail>>(inquiryPath(inquiryId), config),
  addMessage: (inquiryId: string | number, data: InquiryMessageCreateData, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${inquiryPath(inquiryId)}/messages`, data, config),
  withdraw: (inquiryId: string | number, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${inquiryPath(inquiryId)}/withdraw`, undefined, config),
  close: (inquiryId: string | number, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${inquiryPath(inquiryId)}/close`, undefined, config),

  getAdminPage: (params: InquiryListParams, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<PageResponse<InquirySummary> | PageResponseRaw<InquirySummary>>>(
      '/admin/support/inquiries',
      { ...config, params },
    ),
  getAdminDetail: (inquiryId: string | number, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<InquiryDetail>>(adminInquiryPath(inquiryId), config),
  start: (inquiryId: string | number, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${adminInquiryPath(inquiryId)}/start`, undefined, config),
  reply: (inquiryId: string | number, data: InquiryMessageCreateData, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${adminInquiryPath(inquiryId)}/reply`, data, config),
  addNote: (inquiryId: string | number, data: InquiryMessageCreateData, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${adminInquiryPath(inquiryId)}/notes`, data, config),
  adminClose: (inquiryId: string | number, reason: string, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${adminInquiryPath(inquiryId)}/close`, { reason }, config),
  reopen: (inquiryId: string | number, config?: AxiosRequestConfig) =>
    api.post<ApiResponse<InquiryDetail>>(`${adminInquiryPath(inquiryId)}/reopen`, undefined, config),
}

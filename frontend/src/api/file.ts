import api from './index'
import type { ApiResponse } from '@/types'
import type { AxiosRequestConfig } from 'axios'

export interface FileUploadResponse {
    fileId: number
    url: string
    filename: string
    originalFilename: string
    size: number
    contentType: string
}

export const fileApi = {
    uploadFile: (file: File, config?: AxiosRequestConfig) => {
        const formData = new FormData()
        formData.append('file', file)

        return api.post<ApiResponse<FileUploadResponse>>('/files/upload', formData, {
            ...config,
            headers: {
                'Content-Type': 'multipart/form-data',
                ...config?.headers
            }
        })
    }
}

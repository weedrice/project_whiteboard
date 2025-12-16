import api from './index'
import type { ApiResponse } from '@/types'

export interface FileUploadResponse {
    url: string
    filename: string
    originalFilename: string
    size: number
    contentType: string
}

export const fileApi = {
    uploadFile: (file: File) => {
        const formData = new FormData()
        formData.append('file', file)

        return api.post<ApiResponse<FileUploadResponse>>('/files/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        })
    }
}

import api from './index'
import type { ApiResponse } from '@/types'
import type { AxiosRequestConfig } from 'axios'
import type { FileUploadTarget } from '@/api/fileUploadTargets'
import { API } from '@/utils/constants'
import { getStoredAccessToken } from '@/utils/authTokenStorage'

export interface FileUploadResponse {
    fileId: number
    url?: string
    fileUrl?: string
}

export interface FileDiscardResponse {
    discardedCount: number
}


export function resolveFileUploadUrl(uploadedFile: FileUploadResponse): string | null {
    return uploadedFile.url ?? uploadedFile.fileUrl ?? null
}

export function discardUploadsOnPageExit(fileIds: number[]): boolean {
    if (fileIds.length === 0 || typeof fetch === 'undefined') return false
    const accessToken = getStoredAccessToken()
    try {
        void fetch(`${API.BASE_URL.replace(/\/+$/, '')}/files/uploads/discard`, {
            method: 'POST',
            credentials: 'include',
            keepalive: true,
            headers: {
                'Content-Type': 'application/json',
                ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
            },
            body: JSON.stringify({ fileIds }),
        }).catch(() => undefined)
        return true
    } catch {
        return false
    }
}

export const fileApi = {
    uploadFile: (file: File, config?: AxiosRequestConfig, target?: FileUploadTarget) => {
        const formData = new FormData()
        formData.append('file', file)
        // 대상을 함께 보내야 서버에서도 같은 제한이 걸린다. 생략하면 서버는 GENERIC으로 처리한다.
        if (target) formData.append('target', target)

        return api.post<ApiResponse<FileUploadResponse>>('/files/upload', formData, {
            ...config,
            headers: {
                'Content-Type': 'multipart/form-data',
                ...config?.headers
            }
        })
    },
    discardUploads: (fileIds: number[], config?: AxiosRequestConfig) => (
        api.post<ApiResponse<FileDiscardResponse>>('/files/uploads/discard', { fileIds }, config)
    ),
    discardUploadsOnPageExit,
}

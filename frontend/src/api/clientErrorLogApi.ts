import api from '@/api'

export type ClientErrorSource = 'VUE' | 'WINDOW_ERROR' | 'UNHANDLED_REJECTION'

export interface ClientErrorLogRequest {
    source: ClientErrorSource
    errorType: string
    message: string
    stackTrace?: string
    component?: string
    info?: string
    pageUrl: string
    commitHash: string
}

export const clientErrorLogApi = {
    create(data: ClientErrorLogRequest) {
        return api.post<void>('/logs/client', data, {
            skipGlobalErrorHandler: true,
            skipAuthRefresh: true,
        })
    },
}

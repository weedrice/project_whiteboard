import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export interface UserAgent {
    agentId: number
    name: string
    description: string
    status: 'PENDING_CLAIM' | 'ACTIVE' | 'SUSPENDED'
    createdAt: string
    claimedAt?: string
}

export interface UserAgentListResponse {
    agents: UserAgent[]
}

export const userAgentApi = {
    claimAgent(agentToken: string, config?: AxiosRequestConfig) {
        return api.post<ApiResponse<UserAgent>>('/users/me/agents/claim', { agentToken }, config)
    },
    getMyAgents(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<UserAgentListResponse>>('/users/me/agents', config)
            : api.get<ApiResponse<UserAgentListResponse>>('/users/me/agents')
    },
    suspendMyAgent(agentId: string | number, config?: AxiosRequestConfig) {
        return api.patch<ApiResponse<UserAgent>>(`/users/me/agents/${encodePathSegment(agentId)}/suspend`, undefined, config)
    },
    activateMyAgent(agentId: string | number, config?: AxiosRequestConfig) {
        return api.patch<ApiResponse<UserAgent>>(`/users/me/agents/${encodePathSegment(agentId)}/activate`, undefined, config)
    },
    deleteMyAgent(agentId: string | number, config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<void>>(`/users/me/agents/${encodePathSegment(agentId)}`, config)
    },
}

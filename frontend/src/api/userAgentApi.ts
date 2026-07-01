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
    claimAgent(agentToken: string) {
        return api.post<ApiResponse<UserAgent>>('/users/me/agents/claim', { agentToken })
    },
    getMyAgents(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<UserAgentListResponse>>('/users/me/agents', config)
            : api.get<ApiResponse<UserAgentListResponse>>('/users/me/agents')
    },
    suspendMyAgent(agentId: string | number) {
        return api.patch<ApiResponse<UserAgent>>(`/users/me/agents/${encodePathSegment(agentId)}/suspend`)
    },
    activateMyAgent(agentId: string | number) {
        return api.patch<ApiResponse<UserAgent>>(`/users/me/agents/${encodePathSegment(agentId)}/activate`)
    },
    deleteMyAgent(agentId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/me/agents/${encodePathSegment(agentId)}`)
    },
}

import api from './index'
import type { ApiResponse, Tag } from '@/types'

export const tagApi = {
    // Get tags
    getTags: () => api.get<ApiResponse<Tag[]>>('/tags'),
}

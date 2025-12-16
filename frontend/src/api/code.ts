import axios from '@/api'
import type { ApiResponse, Code } from '@/types'

export const codeApi = {
    getCodes(typeCode: string) {
        return axios.get<ApiResponse<Code[]>>(`/codes/${typeCode}`)
    }
}

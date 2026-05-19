import axios from '@/api'
import type { ApiResponse, Code } from '@/types'

interface CommonCodeDetailResponse {
    codeValue: string
    codeName: string
    sortOrder?: number
}

const toCode = (detail: CommonCodeDetailResponse): Code => ({
    code: detail.codeValue,
    value: detail.codeValue,
    name: detail.codeName,
    sortOrder: detail.sortOrder
})

export const codeApi = {
    async getCodes(typeCode: string) {
        const response = await axios.get<ApiResponse<CommonCodeDetailResponse[]>>(`/common-codes/${typeCode}/details`)

        return {
            ...response,
            data: {
                ...response.data,
                data: response.data.data.map(toCode)
            }
        }
    }
}

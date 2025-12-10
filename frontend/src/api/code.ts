import axios from '@/api'

export const codeApi = {
    getCodes(typeCode: string) {
        return axios.get(`/codes/${typeCode}`)
    }
}

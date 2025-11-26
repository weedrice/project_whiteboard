import axios from '@/api'

export const codeApi = {
    getCodes(typeCode) {
        return axios.get(`/api/codes/${typeCode}`)
    }
}

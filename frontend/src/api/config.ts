import axios from '@/api'

export const configApi = {
    getConfig(key: string) {
        return axios.get(`/configs/${key}`)
    }
}

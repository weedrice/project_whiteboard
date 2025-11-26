import axios from '@/api'

export const configApi = {
    getConfig(key) {
        return axios.get(`/configs/${key}`)
    }
}

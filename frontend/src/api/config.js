import axios from '@/api'

export const configApi = {
    getConfigs() {
        return axios.get('/api/configs')
    },
    getConfig(key) {
        return axios.get(`/api/configs/${key}`)
    }
}

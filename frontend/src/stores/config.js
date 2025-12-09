import { defineStore } from 'pinia'
import { configApi } from '@/api/config'
import logger from '@/utils/logger'

export const useConfigStore = defineStore('config', {
    state: () => ({
        configs: {},
        loading: false,
        error: null
    }),

    actions: {
        async fetchConfig(key) {
            if (this.configs[key]) return this.configs[key]

            this.loading = true
            try {
                const { data } = await configApi.getConfig(key)
                this.configs[key] = data
                return data
            } catch (error) {
                this.error = error
                logger.error(`Failed to fetch config ${key}:`, error)
                return null
            } finally {
                this.loading = false
            }
        },

        async fetchAllConfigs() {
            this.loading = true
            try {
                const { data } = await configApi.getConfigs()
                // Assuming response.data is an object or array of configs
                // Adjust based on actual API response structure
                if (Array.isArray(data)) {
                    data.forEach(config => {
                        this.configs[config.key] = config.value
                    })
                } else {
                    this.configs = { ...this.configs, ...data }
                }
            } catch (error) {
                this.error = error
                logger.error('Failed to fetch all configs:', error)
            } finally {
                this.loading = false
            }
        }
    },

    getters: {
        getConfig: (state) => (key) => state.configs[key]
    }
})

import { defineStore } from 'pinia'
import { configApi } from '@/api/config'
import logger from '@/utils/logger'

interface Config {
    key: string
    value: string
}

interface ConfigState {
    configs: Record<string, string>;
    loading: boolean;
    error: Error | null;
}

export const useConfigStore = defineStore('config', {
    state: (): ConfigState => ({
        configs: {},
        loading: false,
        error: null
    }),

    actions: {
        async fetchConfig(key: string) {
            if (this.configs[key]) return this.configs[key]

            this.loading = true
            try {
                const { data } = await configApi.getConfig(key)
                this.configs[key] = data
                return data
            } catch (error) {
                this.error = error as Error
                logger.error(`Failed to fetch config ${key}:`, error)
                return null
            } finally {
                this.loading = false
            }
        },

        async fetchAllConfigs() {
            this.loading = true
            try {
                // getConfigs is now defined in configApi
                const { data } = await configApi.getConfigs()

                if (Array.isArray(data)) {
                    data.forEach((config: Config) => {
                        this.configs[config.key] = config.value
                    })
                } else {
                    this.configs = { ...this.configs, ...data }
                }
            } catch (error) {
                this.error = error as Error
                logger.error('Failed to fetch all configs:', error)
            } finally {
                this.loading = false
            }
        }
    },

    getters: {
        getConfig: (state) => (key: string) => state.configs[key]
    }
})


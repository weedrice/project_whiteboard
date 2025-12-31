import { defineStore } from 'pinia'
import { configApi } from '@/api/config'
import logger from '@/utils/logger'
import type { GlobalConfig } from '@/types'

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
                if (data.success && data.data) {
                    this.configs[key] = data.data.configValue
                    return data.data.configValue
                }
                return null
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
                const { data } = await configApi.getConfigs()

                if (data.success && Array.isArray(data.data)) {
                    data.data.forEach((config: GlobalConfig) => {
                        this.configs[config.configKey] = config.configValue
                    })
                }
            } catch (error) {
                this.error = error as Error
                logger.error('Failed to fetch all configs:', error)
            } finally {
                this.loading = false
            }
        },

        async fetchPublicConfigs() {
            this.loading = true
            try {
                const { data } = await configApi.getPublicConfigs()
                if (data.success && data.data) {
                    this.configs = { ...this.configs, ...data.data }
                }
            } catch (error) {
                this.error = error as Error
                logger.error('Failed to fetch public configs:', error)
            } finally {
                this.loading = false
            }
        }
    },

    getters: {
        getConfig: (state) => (key: string) => state.configs[key]
    }
})


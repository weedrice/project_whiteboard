import { defineStore } from 'pinia'
import { configApi } from '@/api/config'
import logger from '@/utils/logger'
import type { ConfigEntry, GlobalConfig } from '@/types'

interface ConfigState {
    configs: Record<string, string>;
    loading: boolean;
    error: Error | null;
}

interface ConfigKeyValue {
    key: string;
    value: string;
}

function configEntriesToRecord(configs: ConfigKeyValue[]) {
    return configs.reduce<Record<string, string>>((acc, config) => {
        acc[config.key] = config.value
        return acc
    }, {})
}

function mergeConfigEntries(target: Record<string, string>, configs: ConfigKeyValue[]) {
    Object.assign(target, configEntriesToRecord(configs))
}

async function withConfigLoading<T>(
    state: ConfigState,
    errorMessage: string,
    action: () => Promise<T>,
    fallback: T
): Promise<T> {
    state.loading = true
    try {
        return await action()
    } catch (error: unknown) {
        state.error = error as Error
        logger.error(errorMessage, error)
        return fallback
    } finally {
        state.loading = false
    }
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

            return withConfigLoading(this, `Failed to fetch config ${key}:`, async () => {
                const { data } = await configApi.getConfig(key)
                if (data.success && data.data) {
                    this.configs[data.data.key] = data.data.value
                    return data.data.value
                }
                return null
            }, null)
        },

        async fetchAllConfigs() {
            await withConfigLoading(this, 'Failed to fetch all configs:', async () => {
                const { data } = await configApi.getConfigs()

                if (data.success && Array.isArray(data.data)) {
                    mergeConfigEntries(this.configs, data.data as GlobalConfig[])
                }
            }, undefined)
        },

        async fetchPublicConfigs() {
            await withConfigLoading(this, 'Failed to fetch public configs:', async () => {
                const { data } = await configApi.getPublicConfigs()
                if (data.success && Array.isArray(data.data)) {
                    this.configs = { ...this.configs, ...configEntriesToRecord(data.data as ConfigEntry[]) }
                }
            }, undefined)
        }
    },

    getters: {
        getConfig: (state) => (key: string) => state.configs[key]
    }
})


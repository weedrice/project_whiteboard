import { defineStore } from 'pinia'
import { configApi } from '@/api/config'
import { unwrapApiData } from '@/api/response'
import logger from '@/utils/logger'
import type { ConfigEntry, GlobalConfig } from '@/types'

interface ConfigState {
    configs: Record<string, string>;
    loading: boolean;
    error: Error | null;
    publicConfigsLoaded: boolean;
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
    state.error = null
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
        error: null,
        publicConfigsLoaded: false
    }),

    actions: {
        async fetchConfig(key: string) {
            if (Object.prototype.hasOwnProperty.call(this.configs, key)) return this.configs[key]

            return withConfigLoading(this, `Failed to fetch config ${key}:`, async () => {
                const { data } = await configApi.getConfig(key)
                const config = unwrapApiData(data)
                if (data.success && config) {
                    this.configs[config.key] = config.value
                    return config.value
                }
                return null
            }, null)
        },

        async fetchAllConfigs() {
            await withConfigLoading(this, 'Failed to fetch all configs:', async () => {
                const { data } = await configApi.getConfigs()
                const configs = unwrapApiData(data)

                if (data.success && Array.isArray(configs)) {
                    mergeConfigEntries(this.configs, configs as GlobalConfig[])
                }
            }, undefined)
        },

        async fetchPublicConfigs() {
            const loaded = await withConfigLoading(this, 'Failed to fetch public configs:', async () => {
                const { data } = await configApi.getPublicConfigs()
                const configs = unwrapApiData(data)
                if (data.success && Array.isArray(configs)) {
                    this.configs = { ...this.configs, ...configEntriesToRecord(configs as ConfigEntry[]) }
                    return true
                }
                return false
            }, false)
            this.publicConfigsLoaded = loaded
            return loaded
        },

        invalidateConfig(key: string) {
            delete this.configs[key]
        }
    },

    getters: {
        getConfig: (state) => (key: string) => state.configs[key]
    }
})


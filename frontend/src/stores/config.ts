import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { configApi } from '@/api/config'
import { unwrapApiData } from '@/api/response'
import logger from '@/utils/logger'
import type { ConfigEntry, GlobalConfig } from '@/types'

type RequestFlight<T> = {
    revision: number
    promise: Promise<T>
}

const PUBLIC_REQUEST_KEY = '$public'
const ALL_REQUEST_KEY = '$all'

export const useConfigStore = defineStore('config', () => {
    const configs = ref<Record<string, string>>({})
    const loadingKeys = reactive<Record<string, boolean>>({})
    const errorsByKey = reactive<Record<string, Error | null>>({})
    const publicConfigsLoaded = ref(false)
    const revisions = new Map<string, number>()
    const configWriteGenerations = new Map<string, number>()
    const flights = new Map<string, RequestFlight<unknown>>()
    let writeGeneration = 0

    const loading = computed(() => Object.values(loadingKeys).some(Boolean))
    const error = computed(() => Object.values(errorsByKey).find((value): value is Error => value instanceof Error) ?? null)

    function nextRevision(key: string) {
        const revision = (revisions.get(key) ?? 0) + 1
        revisions.set(key, revision)
        return revision
    }

    function isCurrent(key: string, revision: number) {
        return revisions.get(key) === revision
    }

    function beginRequest(key: string) {
        const revision = nextRevision(key)
        const requestWriteGeneration = ++writeGeneration
        loadingKeys[key] = true
        errorsByKey[key] = null
        return { revision, requestWriteGeneration }
    }

    function writeConfigIfCurrent(key: string, value: string, requestWriteGeneration: number) {
        if ((configWriteGenerations.get(key) ?? 0) > requestWriteGeneration) return false
        configs.value[key] = value
        configWriteGenerations.set(key, requestWriteGeneration)
        return true
    }

    function finishRequest(key: string, revision: number, caughtError?: unknown) {
        if (!isCurrent(key, revision)) return
        loadingKeys[key] = false
        errorsByKey[key] = caughtError instanceof Error ? caughtError : null
    }

    function setFlight<T>(key: string, revision: number, promise: Promise<T>) {
        const flight: RequestFlight<T> = { revision, promise }
        flights.set(key, flight)
        void promise.finally(() => {
            if (flights.get(key) === flight) flights.delete(key)
        }).catch(() => undefined)
        return promise
    }

    function currentFlight<T>(key: string) {
        return flights.get(key) as RequestFlight<T> | undefined
    }

    async function fetchConfig(key: string) {
        if (Object.prototype.hasOwnProperty.call(configs.value, key)) return configs.value[key]

        const existing = currentFlight<string | null>(key)
        if (existing && isCurrent(key, existing.revision)) return existing.promise

        const { revision, requestWriteGeneration } = beginRequest(key)
        const request = (async () => {
            try {
                const { data } = await configApi.getConfig(key)
                const config = unwrapApiData(data)
                if (!isCurrent(key, revision)) return null
                if (data.success && config) {
                    return writeConfigIfCurrent(config.key, config.value, requestWriteGeneration)
                        ? config.value
                        : (configs.value[config.key] ?? null)
                }
                return null
            } catch (caughtError: unknown) {
                if (isCurrent(key, revision)) {
                    logger.error(`Failed to fetch config ${key}:`, caughtError)
                    finishRequest(key, revision, caughtError)
                }
                return null
            } finally {
                if (isCurrent(key, revision) && errorsByKey[key] == null) {
                    finishRequest(key, revision)
                }
            }
        })()
        return setFlight(key, revision, request)
    }

    async function fetchAllConfigs() {
        const existing = currentFlight<void>(ALL_REQUEST_KEY)
        if (existing && isCurrent(ALL_REQUEST_KEY, existing.revision)) return existing.promise

        const { revision, requestWriteGeneration } = beginRequest(ALL_REQUEST_KEY)
        const request = (async () => {
            try {
                const { data } = await configApi.getConfigs()
                const nextConfigs = unwrapApiData(data)
                if (isCurrent(ALL_REQUEST_KEY, revision) && data.success && Array.isArray(nextConfigs)) {
                    for (const config of nextConfigs as GlobalConfig[]) {
                        writeConfigIfCurrent(config.key, config.value, requestWriteGeneration)
                    }
                }
            } catch (caughtError: unknown) {
                if (isCurrent(ALL_REQUEST_KEY, revision)) {
                    logger.error('Failed to fetch all configs:', caughtError)
                    finishRequest(ALL_REQUEST_KEY, revision, caughtError)
                }
            } finally {
                if (isCurrent(ALL_REQUEST_KEY, revision) && errorsByKey[ALL_REQUEST_KEY] == null) {
                    finishRequest(ALL_REQUEST_KEY, revision)
                }
            }
        })()
        return setFlight(ALL_REQUEST_KEY, revision, request)
    }

    async function fetchPublicConfigs() {
        const existing = currentFlight<boolean>(PUBLIC_REQUEST_KEY)
        if (existing && isCurrent(PUBLIC_REQUEST_KEY, existing.revision)) return existing.promise

        const { revision, requestWriteGeneration } = beginRequest(PUBLIC_REQUEST_KEY)
        const request = (async () => {
            try {
                const { data } = await configApi.getPublicConfigs()
                const nextConfigs = unwrapApiData(data)
                if (!isCurrent(PUBLIC_REQUEST_KEY, revision)) return false
                const loaded = data.success && Array.isArray(nextConfigs)
                if (loaded) {
                    for (const config of nextConfigs as ConfigEntry[]) {
                        writeConfigIfCurrent(config.key, config.value, requestWriteGeneration)
                    }
                }
                publicConfigsLoaded.value = loaded
                return loaded
            } catch (caughtError: unknown) {
                if (isCurrent(PUBLIC_REQUEST_KEY, revision)) {
                    publicConfigsLoaded.value = false
                    logger.error('Failed to fetch public configs:', caughtError)
                    finishRequest(PUBLIC_REQUEST_KEY, revision, caughtError)
                }
                return false
            } finally {
                if (isCurrent(PUBLIC_REQUEST_KEY, revision) && errorsByKey[PUBLIC_REQUEST_KEY] == null) {
                    finishRequest(PUBLIC_REQUEST_KEY, revision)
                }
            }
        })()
        return setFlight(PUBLIC_REQUEST_KEY, revision, request)
    }

    function invalidateConfig(key: string) {
        delete configs.value[key]
        configWriteGenerations.set(key, ++writeGeneration)
        nextRevision(key)
        loadingKeys[key] = false
        errorsByKey[key] = null
    }

    function invalidatePublicConfigs() {
        nextRevision(PUBLIC_REQUEST_KEY)
        publicConfigsLoaded.value = false
        loadingKeys[PUBLIC_REQUEST_KEY] = false
        errorsByKey[PUBLIC_REQUEST_KEY] = null
    }

    const getConfig = (key: string) => configs.value[key]

    return {
        configs,
        loading,
        error,
        loadingKeys,
        errorsByKey,
        publicConfigsLoaded,
        fetchConfig,
        fetchAllConfigs,
        fetchPublicConfigs,
        invalidateConfig,
        invalidatePublicConfigs,
        getConfig,
    }
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConfigStore } from '../config'
import { configApi } from '@/api/config'
import logger from '@/utils/logger'

vi.mock('@/api/config', () => ({
    configApi: {
        getConfig: vi.fn(),
        getPublicConfigs: vi.fn(),
        getConfigs: vi.fn(),
    }
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn()
    }
}))

describe('Config Store', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
    })

    it('stores single config DTO by key/value fields', async () => {
        vi.mocked(configApi.getConfig).mockResolvedValue({
            data: {
                success: true,
                data: {
                    key: 'site.name',
                    value: 'Noviis'
                }
            }
        } as never)

        const store = useConfigStore()
        const value = await store.fetchConfig('site.name')

        expect(value).toBe('Noviis')
        expect(store.configs['site.name']).toBe('Noviis')
    })

    it('merges public config DTO list into keyed store state', async () => {
        vi.mocked(configApi.getPublicConfigs).mockResolvedValue({
            data: {
                success: true,
                data: [
                    { key: 'site.name', value: 'Noviis' },
                    { key: 'board.create.enabled', value: 'true' }
                ]
            }
        } as never)

        const store = useConfigStore()
        await store.fetchPublicConfigs()

        expect(store.configs['site.name']).toBe('Noviis')
        expect(store.configs['board.create.enabled']).toBe('true')
    })

    it('merges admin config DTO list into existing keyed store state', async () => {
        vi.mocked(configApi.getConfigs).mockResolvedValue({
            data: {
                success: true,
                data: [
                    { key: 'site.name', value: 'Noviis' },
                    { key: 'points.post', value: '10' }
                ]
            }
        } as never)

        const store = useConfigStore()
        store.configs.existing = 'keep'
        await store.fetchAllConfigs()

        expect(store.configs).toMatchObject({
            existing: 'keep',
            'site.name': 'Noviis',
            'points.post': '10'
        })
        expect(store.loading).toBe(false)
    })

    it('stores errors and clears loading when config fetch fails', async () => {
        const error = new Error('network failed')
        vi.mocked(configApi.getConfig).mockRejectedValue(error)

        const store = useConfigStore()
        const value = await store.fetchConfig('site.name')

        expect(value).toBeNull()
        expect(store.error).toBe(error)
        expect(store.loading).toBe(false)
        expect(logger.error).toHaveBeenCalledWith('Failed to fetch config site.name:', error)
    })
})

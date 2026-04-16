import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConfigStore } from '../config'
import { configApi } from '@/api/config'

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
})

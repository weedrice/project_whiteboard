import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { clearAuthScopedQueries, configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { usePwaReloadBlocker, whenPwaReloadSafe } from '@/pwaReloadGuard'
import { createDeferred } from '@/test/async'
import { useGlobalSettingsManager } from '../useGlobalSettingsManager'
import type { GlobalConfig, User } from '@/types'

vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let client: QueryClient
let wrapper: VueWrapper | undefined
let manager: ReturnType<typeof useGlobalSettingsManager>
let serverConfig: GlobalConfig
const adapter = vi.fn<(config: InternalAxiosRequestConfig) => Promise<AxiosResponse>>()
const originalAdapter = api.defaults.adapter

function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } }
}

function respond(config: InternalAxiosRequestConfig) {
  if (config.method === 'get' && config.url === '/admin/configs') {
    return response(config, [{ ...serverConfig }])
  }
  if (config.method === 'put' && config.url === '/admin/configs/NOBICON_PRICE') {
    const payload = JSON.parse(config.data) as { value: string, description?: string }
    serverConfig = {
      key: 'NOBICON_PRICE', value: payload.value.trim(), description: payload.description?.trim(),
    }
    return response(config, { ...serverConfig })
  }
  throw new Error(`Unexpected request: ${config.method} ${config.url}`)
}

async function mountManager() {
  wrapper = mount(defineComponent({
    setup() {
      manager = useGlobalSettingsManager()
      usePwaReloadBlocker(manager.hasUnsavedChanges)
      return () => JSON.stringify(manager.configs.value)
    },
  }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
  await vi.waitFor(() => expect(manager.configs.value).toEqual([serverConfig]))
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore(pinia)
  auth.accessToken = 'admin-session-token'
  auth.user = { userId: 7 } as User
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  serverConfig = { key: 'NOBICON_PRICE', value: '100', description: 'Price' }
  adapter.mockReset().mockImplementation(async config => respond(config))
  api.defaults.adapter = adapter
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  client.clear()
  disposePinia(pinia)
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  api.defaults.adapter = originalAdapter
  vi.restoreAllMocks()
})

describe('normalized config saves with real Vue Query and Axios', () => {
  it('acknowledges the response and releases a deferred PWA update before the refetch finishes', async () => {
    await mountManager()
    const refetch = createDeferred<AxiosResponse>()
    let refetchConfig!: InternalAxiosRequestConfig
    adapter.mockImplementation(async config => {
      if (config.method === 'get') {
        refetchConfig = config
        return refetch.promise
      }
      return respond(config)
    })
    manager.updateDraft('NOBICON_PRICE', { value: '  200  ', description: '  Saved description  ' })
    const reload = vi.fn()
    expect(whenPwaReloadSafe(reload)).toBe(false)

    await manager.handleSave('NOBICON_PRICE')
    await vi.waitFor(() => expect(refetchConfig).toBeDefined())

    expect(manager.configs.value).toEqual([{ key: 'NOBICON_PRICE', value: '200', description: 'Saved description' }])
    expect(manager.hasUnsavedChanges.value).toBe(false)
    expect(reload).toHaveBeenCalledOnce()
    refetch.resolve(respond(refetchConfig))
    await flushPromises()
    expect(manager.hasUnsavedChanges.value).toBe(false)
    expect(reload).toHaveBeenCalledOnce()
  })

  it('acknowledges a save after a background refetch and same-session token rotation', async () => {
    await mountManager()
    const saved = createDeferred<AxiosResponse>()
    let saveResponse!: AxiosResponse
    adapter.mockImplementation(async config => {
      if (config.method === 'put') {
        saveResponse = respond(config)
        return saved.promise
      }
      return respond(config)
    })
    manager.updateDraft('NOBICON_PRICE', { value: '  200  ' })
    const pending = manager.handleSave('NOBICON_PRICE')
    await vi.waitFor(() => expect(saveResponse).toBeDefined())
    await client.invalidateQueries()
    expect(manager.hasUnsavedChanges.value).toBe(true)
    auth.accessToken = 'rotated-admin-session-token'
    saved.resolve(saveResponse)
    await pending
    await flushPromises()

    expect(manager.configs.value[0]?.value).toBe('200')
    expect(manager.hasUnsavedChanges.value).toBe(false)
    expect(saveResponse.config.expectedSession).toEqual({ sessionGeneration: 0, userId: 7 })
    expect(whenPwaReloadSafe(vi.fn())).toBe(true)
  })

  it('preserves newer edits while updating the saved baseline, until the newer draft is saved', async () => {
    await mountManager()
    const saved = createDeferred<AxiosResponse>()
    let saveResponse!: AxiosResponse
    adapter.mockImplementationOnce(async config => {
      saveResponse = respond(config)
      return saved.promise
    })
    manager.updateDraft('NOBICON_PRICE', { value: '  200  ', description: '  Saved  ' })
    const pending = manager.handleSave('NOBICON_PRICE')
    await vi.waitFor(() => expect(saveResponse).toBeDefined())
    manager.updateDraft('NOBICON_PRICE', { value: '  300  ', description: '  New draft  ' })
    saved.resolve(saveResponse)
    await pending
    await flushPromises()

    expect(manager.configs.value[0]).toMatchObject({ value: '  300  ', description: '  New draft  ' })
    expect(manager.hasUnsavedChanges.value).toBe(true)
    const reload = vi.fn()
    expect(whenPwaReloadSafe(reload)).toBe(false)
    await manager.handleSave('NOBICON_PRICE')
    await flushPromises()
    expect(manager.configs.value[0]).toMatchObject({ value: '300', description: 'New draft' })
    expect(manager.hasUnsavedChanges.value).toBe(false)
    expect(reload).toHaveBeenCalledOnce()
  })

  it('keeps the draft and PWA blocker when saving fails', async () => {
    await mountManager()
    adapter.mockRejectedValueOnce(new Error('Save failed'))
    manager.updateDraft('NOBICON_PRICE', { value: '  200  ' })

    await manager.handleSave('NOBICON_PRICE')

    expect(manager.configs.value[0]?.value).toBe('  200  ')
    expect(manager.hasUnsavedChanges.value).toBe(true)
    expect(whenPwaReloadSafe(vi.fn())).toBe(false)
  })

  it('preserves edits back to the old baseline when a refetch arrives before save acknowledgement', async () => {
    await mountManager()
    const saved = createDeferred<AxiosResponse>()
    let saveResponse!: AxiosResponse
    adapter.mockImplementationOnce(async config => {
      saveResponse = respond(config)
      return saved.promise
    })
    manager.updateDraft('NOBICON_PRICE', { value: '200' })
    const pending = manager.handleSave('NOBICON_PRICE')
    await vi.waitFor(() => expect(saveResponse).toBeDefined())
    manager.updateDraft('NOBICON_PRICE', { value: '100' })
    await client.invalidateQueries()
    expect(manager.configs.value[0]?.value).toBe('100')

    saved.resolve(saveResponse)
    await pending
    await flushPromises()

    expect(manager.configs.value[0]?.value).toBe('100')
    expect(manager.hasUnsavedChanges.value).toBe(true)
    expect(whenPwaReloadSafe(vi.fn())).toBe(false)
    await manager.handleSave('NOBICON_PRICE')
    await flushPromises()
    expect(serverConfig.value).toBe('100')
    expect(manager.hasUnsavedChanges.value).toBe(false)
  })

  it('does not acknowledge an old response or overwrite the next account draft', async () => {
    await mountManager()
    const saved = createDeferred<AxiosResponse>()
    let saveResponse!: AxiosResponse
    adapter.mockImplementationOnce(async config => {
      saveResponse = respond(config)
      return saved.promise
    })
    const toast = vi.spyOn(useToastStore(), 'addToast')
    manager.updateDraft('NOBICON_PRICE', { value: '  200  ' })
    const pending = manager.handleSave('NOBICON_PRICE')
    await vi.waitFor(() => expect(saveResponse).toBeDefined())

    serverConfig = { key: 'NOBICON_PRICE', value: '400', description: 'Next account' }
    auth.sessionGeneration += 1
    auth.user = { userId: 8 } as User
    auth.accessToken = 'next-admin-session-token'
    notifyAuthSessionBoundary(auth.sessionGeneration)
    clearAuthScopedQueries(client)
    await vi.waitFor(() => expect(manager.configs.value[0]?.value).toBe('400'))
    manager.updateDraft('NOBICON_PRICE', { value: '  500  ' })
    saved.resolve(saveResponse)
    await pending
    await flushPromises()

    expect(saveResponse.config.signal?.aborted).toBe(true)
    expect(manager.configs.value[0]?.value).toBe('  500  ')
    expect(manager.hasUnsavedChanges.value).toBe(true)
    expect(toast).not.toHaveBeenCalledWith('admin.settings.messages.saved', 'success')
  })

  it('releases its PWA blocker on unmount and ignores the late save response', async () => {
    await mountManager()
    const saved = createDeferred<AxiosResponse>()
    let saveResponse!: AxiosResponse
    adapter.mockImplementationOnce(async config => {
      saveResponse = respond(config)
      return saved.promise
    })
    const toast = vi.spyOn(useToastStore(), 'addToast')
    manager.updateDraft('NOBICON_PRICE', { value: '  200  ' })
    const pending = manager.handleSave('NOBICON_PRICE')
    await vi.waitFor(() => expect(saveResponse).toBeDefined())
    wrapper!.unmount()
    wrapper = undefined
    expect(whenPwaReloadSafe(vi.fn())).toBe(true)
    saved.resolve(saveResponse)
    await pending

    expect(manager.configs.value[0]?.value).toBe('  200  ')
    expect(toast).not.toHaveBeenCalledWith('admin.settings.messages.saved', 'success')
  })
})

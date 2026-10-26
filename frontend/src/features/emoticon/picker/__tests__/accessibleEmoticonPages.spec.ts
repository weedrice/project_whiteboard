import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'
import type { EmoticonMaster } from '@/types/emoticon'
import { useAccessibleEmoticonPicker } from '../useAccessibleEmoticonPicker'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'

const mocks = vi.hoisted(() => ({ auth: null as ReturnType<typeof createAuth> | null }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))
vi.mock('@/router', () => ({ default: { push: vi.fn(), currentRoute: { value: { meta: {} } } } }))

function createAuth() {
  return reactive({
    sessionGeneration: 1, user: { userId: 1 }, accessToken: 'token-a',
    fetchUser: vi.fn(async () => true), setTokens: vi.fn(), applyTokenIfCurrent: vi.fn(() => true), clearSessionState: vi.fn(),
  })
}

const pack = (id: number): EmoticonMaster => ({
  emoticonId: id, name: `Pack${id}`, tags: [`tag${id}`], isActive: true,
  createdAt: '2026-01-01', modifiedAt: '2026-01-01',
  images: [{ imageId: id * 10, emoticonId: id, imageUrl: `/uploads/${id}.png`, sortOrder: 0 }],
})
const packs = (count: number, start = 1) => Array.from({ length: count }, (_, index) => pack(start + index))
function page(items: EmoticonMaster[], number: number, size = 100) {
  return { content: items.slice(number * size, (number + 1) * size), page: number, size,
    totalElements: items.length, totalPages: Math.ceil(items.length / size), hasNext: (number + 1) * size < items.length }
}
const key = (generation = 1) => sessionQueryKey(generation, emoticonQueryKeys.accessiblePicker)
const originalAdapter = api.defaults.adapter
let auth: ReturnType<typeof createAuth>
let client: QueryClient
let wrapper: VueWrapper
let query: ReturnType<typeof useAccessibleEmoticonPicker>
let requests: InternalAxiosRequestConfig[]
let purchased: EmoticonMaster[]
let owned: EmoticonMaster[]
let respond: (config: InternalAxiosRequestConfig) => unknown | Promise<unknown>

beforeEach(() => {
  auth = createAuth()
  mocks.auth = auth
  persistAccessToken(auth.accessToken)
  configureAuthQueryScope(() => auth.sessionGeneration)
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  requests = []
  purchased = []
  owned = []
  respond = config => {
    if (config.url === '/emoticons/purchased') return page(purchased, config.params.page ?? 0)
    if (config.url === '/emoticons/my') return page(owned, config.params.page ?? 0)
    if (config.url?.match(/^\/emoticons\/\d+$/)) return pack(Number(config.url.split('/').at(-1)))
    throw new Error(`Unexpected request: ${config.url}`)
  }
  api.defaults.adapter = async config => {
    requests.push(config)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data: await respond(config) } }
  }
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } } })
})

function mountQuery() {
  wrapper = mount(defineComponent({ setup() {
    query = useAccessibleEmoticonPicker(() => true)
    return () => null
  } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
}

afterEach(() => {
  wrapper?.unmount()
  client.clear()
  api.defaults.adapter = originalAdapter
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  clearStoredAuthTokens(false)
  vi.restoreAllMocks()
})

describe('accessible emoticon pages with real Vue Query and Axios', () => {
  it.each(['purchased', 'owned'] as const)('searches and selects the 101st %s pack in the real picker', async source => {
    if (source === 'purchased') purchased = packs(101)
    else owned = packs(101)
    wrapper = mount(EmoticonPicker, { props: { show: true }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
    await vi.waitFor(() => expect(wrapper.findAll('.emoticon-btn')).toHaveLength(101))
    await wrapper.get('.search-input').setValue('tag101')
    expect(wrapper.findAll('.emoticon-btn')).toHaveLength(1)
    await wrapper.get('.emoticon-btn').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('.image-btn')).toHaveLength(1))
    await wrapper.get('.image-btn').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([pack(101).images[0]])
    expect(requests.filter(request => request.url === `/emoticons/${source === 'owned' ? 'my' : 'purchased'}`).map(request => request.params.page)).toEqual([0, 1])
  })

  it('merges every page of both sources and deduplicates IDs across and within pages', async () => {
    purchased = [...packs(100), pack(100), pack(101)]
    owned = packs(101, 101)
    mountQuery()
    await vi.waitFor(() => expect(query.isSuccess.value).toBe(true))
    expect(query.data.value?.map(item => item.emoticonId)).toEqual(packs(201).map(item => item.emoticonId))
    expect(requests).toHaveLength(4)
    for (const request of requests) expect(request.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
  })

  it('does not publish a partial list when a later page fails and retries the whole list', async () => {
    purchased = packs(101)
    const normal = respond
    let fail = true
    respond = config => {
      if (fail && config.url === '/emoticons/purchased' && config.params.page === 1) throw new Error('later page unavailable')
      return normal(config)
    }
    mountQuery()
    await vi.waitFor(() => expect(query.isError.value).toBe(true))
    expect(client.getQueryData(key())).toBeUndefined()
    fail = false
    await query.refetch()
    expect(query.data.value).toHaveLength(101)
    expect(requests.filter(request => request.url === '/emoticons/purchased').map(request => request.params.page)).toEqual([0, 1, 0, 1])
  })

  it.each(['cancel', 'account'] as const)('stops later requests and discards partial data after %s during page two', async action => {
    purchased = packs(201)
    const delayed = createDeferred<ReturnType<typeof page>>()
    const normal = respond
    respond = config => config.url === '/emoticons/purchased' && config.params.page === 1 && config.expectedSession?.sessionGeneration === 1
      ? delayed.promise : normal(config)
    mountQuery()
    await vi.waitFor(() => expect(requests.some(request => request.params?.page === 1)).toBe(true))
    const pendingRequest = requests.find(request => request.params?.page === 1)!
    if (action === 'account') {
      purchased = [pack(999)]
      auth.sessionGeneration = 2
      auth.user = { userId: 2 }
      auth.accessToken = 'token-b'
      persistAccessToken(auth.accessToken)
      notifyAuthSessionBoundary(2)
    } else await client.cancelQueries({ queryKey: key() })
    delayed.resolve(page(packs(201), 1))
    await flushPromises()
    expect(pendingRequest.signal?.aborted).toBe(true)
    expect(requests.some(request => request.params?.page === 2 && request.expectedSession?.sessionGeneration === 1)).toBe(false)
    expect(client.getQueryData(key())).toBeUndefined()
    const expectedIds = action === 'account' ? [999] : undefined
    await vi.waitFor(() => expect(query.data.value?.map(item => item.emoticonId)).toEqual(expectedIds))
  })

  it('continues paging through a normal access-token rotation in the same session', async () => {
    purchased = packs(101)
    const normal = respond
    respond = config => {
      const result = normal(config)
      if (config.url === '/emoticons/purchased' && (config.params.page ?? 0) === 0) {
        auth.accessToken = 'rotated-token'
        persistAccessToken(auth.accessToken)
      }
      return result
    }
    mountQuery()
    await vi.waitFor(() => expect(query.data.value).toHaveLength(101))
    const second = requests.find(request => request.url === '/emoticons/purchased' && request.params.page === 1)!
    expect(second.headers.Authorization).toBe('Bearer rotated-token')
    expect(second.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
  })

  it('keeps the previous complete cache when a refetch fails on a later page', async () => {
    purchased = [pack(1)]
    mountQuery()
    await vi.waitFor(() => expect(query.isSuccess.value).toBe(true))
    purchased = packs(101, 2)
    const normal = respond
    respond = config => {
      if (config.url === '/emoticons/purchased' && config.params.page === 1) throw new Error('offline')
      return normal(config)
    }
    await query.refetch()
    expect(query.isError.value).toBe(true)
    expect(client.getQueryData(key())).toEqual([pack(1)])
  })

  it('bounds loading by the first page count even when later counts keep growing', async () => {
    respond = config => config.url === '/emoticons/my' ? page([], 0) : {
      content: [pack(config.params.page + 1)], page: config.params.page, size: 100,
      totalPages: config.params.page + 2, hasNext: true,
    }
    mountQuery()
    await vi.waitFor(() => expect(query.isSuccess.value).toBe(true))
    expect(query.data.value?.map(item => item.emoticonId)).toEqual([1, 2])
    expect(requests.filter(request => request.url === '/emoticons/purchased')).toHaveLength(2)
  })

  it.each(['repeated page', 'infinite total'] as const)('rejects %s metadata instead of looping or publishing an incomplete list', async defect => {
    respond = config => config.url === '/emoticons/my' ? page([], 0) : {
      content: [pack(1)], page: 0, size: 100,
      totalPages: defect === 'infinite total' ? Infinity : 2, hasNext: true,
    }
    mountQuery()
    await vi.waitFor(() => expect(query.isError.value).toBe(true))
    expect(client.getQueryData(key())).toBeUndefined()
    expect(requests.filter(request => request.url === '/emoticons/purchased')).toHaveLength(defect === 'infinite total' ? 1 : 2)
  })

  it('ends at an empty page when the collection shrinks during loading', async () => {
    respond = config => config.url === '/emoticons/my' ? page([], 0) : {
      content: config.params.page === 0 ? [pack(1)] : [], page: config.params.page, size: 100,
      totalPages: 3, hasNext: true,
    }
    mountQuery()
    await vi.waitFor(() => expect(query.isSuccess.value).toBe(true))
    expect(query.data.value?.map(item => item.emoticonId)).toEqual([1])
    expect(requests.filter(request => request.url === '/emoticons/purchased')).toHaveLength(2)
  })
})

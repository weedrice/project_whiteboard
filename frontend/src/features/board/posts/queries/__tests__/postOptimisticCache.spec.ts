import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { postQueryKeys } from '../postQueryKeys'
import { usePost } from '../usePost'

const auth = vi.hoisted(() => ({
  sessionGeneration: 1,
  user: { userId: 1 },
  accessToken: 'token-a',
  fetchUser: vi.fn(async () => true),
  setTokens: vi.fn(),
  applyTokenIfCurrent: vi.fn(() => true),
  clearSessionState: vi.fn(),
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/router', () => ({ default: { push: vi.fn(), currentRoute: { value: { meta: {} } } } }))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

const actions = ['like', 'unlike', 'scrap', 'unscrap'] as const
type Action = typeof actions[number]
const post = (action: Action = 'like') => ({
  postId: 3, title: 'Post', liked: action === 'unlike', likeCount: 4,
  scrapped: action === 'unscrap',
})
const updatedPost = (action: Action) => ({
  ...post(action),
  ...(action === 'like' ? { liked: true, likeCount: 5 } : {}),
  ...(action === 'unlike' ? { liked: false, likeCount: 3 } : {}),
  ...(action === 'scrap' ? { scrapped: true } : {}),
  ...(action === 'unscrap' ? { scrapped: false } : {}),
})
const detailKeys = (generation = 1) => (['3', 3] as const).flatMap(id => (
  [true, false].map(incrementView => sessionQueryKey(generation, postQueryKeys.detail(id, incrementView)))
))
const versionKey = sessionQueryKey(1, postQueryKeys.versions('3'))
const relatedKey = sessionQueryKey(1, postQueryKeys.related('3', 5))
const originalVersions = [{ versionId: 1, title: 'Earlier title' }]
const originalRelated = [{ postId: 8, title: 'Related post' }]
const newerVersions = [{ versionId: 2, title: 'Newest title' }, ...originalVersions]
const newerRelated = [{ postId: 9, title: 'New related post', isNotice: false, isNsfw: false }]
const originalAdapter = api.defaults.adapter
let client: QueryClient
let wrapper: VueWrapper | undefined
let mutations: Record<Action, { mutateAsync: (postId: string | number) => Promise<unknown> }>
let mutationOutcome: ReturnType<typeof deferred<'success' | 'failure'>>
let auxiliaryResponse: ReturnType<typeof deferred<void>>
let mutationRequests: InternalAxiosRequestConfig[]
let auxiliaryRequests: InternalAxiosRequestConfig[]
let related: ReturnType<ReturnType<typeof usePost>['useRelatedPosts']>
let versions: ReturnType<ReturnType<typeof usePost>['usePostVersions']>

beforeEach(() => {
  vi.clearAllMocks()
  auth.sessionGeneration = 1
  auth.user = { userId: 1 }
  auth.accessToken = 'token-a'
  persistAccessToken(auth.accessToken)
  configureAuthQueryScope(() => auth.sessionGeneration)
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  mutationOutcome = deferred()
  auxiliaryResponse = deferred()
  mutationRequests = []
  auxiliaryRequests = []
  client = new QueryClient({ defaultOptions: {
    queries: { retry: false, staleTime: Infinity, gcTime: Infinity },
    mutations: { retry: false, gcTime: 0 },
  } })
  api.defaults.adapter = async config => {
    let data: unknown
    if (config.url === '/posts/3/like' || config.url === '/posts/3/scrap') {
      mutationRequests.push(config)
      if (await mutationOutcome.promise === 'failure') {
        throw new AxiosError('Write failed', 'ERR_BAD_RESPONSE', config, undefined, {
          config, status: 500, statusText: 'Server error', headers: {},
          data: { success: false, message: 'Write failed' },
        })
      }
      data = 1
    } else if (config.url === '/posts/3/related' || config.url === '/posts/3/versions') {
      auxiliaryRequests.push(config)
      await auxiliaryResponse.promise
      data = config.url.endsWith('/versions') ? newerVersions : newerRelated
    } else {
      throw new Error(`Unexpected request: ${config.url}`)
    }
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } }
  }
})

function mountMutations(action: Action, includeAuxiliary = false) {
  detailKeys().forEach(key => client.setQueryData(key, post(action)))
  detailKeys(2).forEach(key => client.setQueryData(key, post(action)))
  client.setQueryData(sessionQueryKey(1, postQueryKeys.detail('4')), { ...post(action), postId: 4 })
  client.setQueryData(versionKey, originalVersions)
  client.setQueryData(relatedKey, originalRelated)
  wrapper = mount(defineComponent({
    setup() {
      const hooks = usePost()
      mutations = {
        like: hooks.useLikePost(), unlike: hooks.useUnlikePost(),
        scrap: hooks.useScrapPost(), unscrap: hooks.useUnscrapPost(),
      }
      if (includeAuxiliary) {
        related = hooks.useRelatedPosts(ref('3'))
        versions = hooks.usePostVersions(ref('3'), ref(true))
      }
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
}

function expectOtherCachesUnchanged(action: Action) {
  expect(client.getQueryData(versionKey)).toEqual(originalVersions)
  expect(client.getQueryData(relatedKey)).toEqual(originalRelated)
  detailKeys(2).forEach(key => {
    expect(client.getQueryData(key)).toEqual(post(action))
    expect(client.getQueryState(key)?.isInvalidated).toBe(false)
  })
  expect(client.getQueryData(sessionQueryKey(1, postQueryKeys.detail('4')))).toEqual({ ...post(action), postId: 4 })
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  client.clear()
  api.defaults.adapter = originalAdapter
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  clearStoredAuthTokens(false)
  vi.restoreAllMocks()
})

describe('optimistic post cache boundaries with real Vue Query and Axios', () => {
  for (const outcome of ['success', 'failure'] as const) {
    it.each(actions)(`keeps auxiliary arrays intact during %s and after ${outcome}`, async action => {
      mountMutations(action)
      const result = mutations[action].mutateAsync('3').then(
        () => ({ outcome: 'success' }),
        error => ({ outcome: 'failure', status: error.response?.status }),
      )
      await vi.waitFor(() => expect(mutationRequests).toHaveLength(1))
      expect(mutationRequests[0]!.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
      expectOtherCachesUnchanged(action)
      detailKeys().forEach(key => expect(client.getQueryData(key)).toEqual(updatedPost(action)))

      mutationOutcome.resolve(outcome)
      expect(await result).toEqual(outcome === 'success' ? { outcome } : { outcome, status: 500 })
      detailKeys().forEach(key => expect(client.getQueryData(key)).toEqual(
        outcome === 'success' ? updatedPost(action) : post(action),
      ))
      expectOtherCachesUnchanged(action)
    })
  }

  it.each(actions)('lets auxiliary reads finish during %s and retains their result on rollback', async action => {
    mountMutations(action, true)
    const reads = Promise.all([related.refetch(), versions.refetch()])
    await vi.waitFor(() => expect(auxiliaryRequests).toHaveLength(2))
    const result = mutations[action].mutateAsync('3').catch(error => error)
    await vi.waitFor(() => expect(mutationRequests).toHaveLength(1))
    expect(auxiliaryRequests.every(request => !request.signal?.aborted)).toBe(true)

    auxiliaryResponse.resolve()
    await reads
    expect(client.getQueryData(versionKey)).toEqual(newerVersions)
    expect(client.getQueryData(relatedKey)).toEqual(newerRelated)
    mutationOutcome.resolve('failure')
    expect(await result).toMatchObject({ response: { status: 500 } })
    expect(client.getQueryData(versionKey)).toEqual(newerVersions)
    expect(client.getQueryData(relatedKey)).toEqual(newerRelated)
    detailKeys().forEach(key => expect(client.getQueryData(key)).toEqual(post(action)))
    await flushPromises()
  })

  it.each(['success', 'failure'] as const)('does not restart auxiliary reads when a scrap settles with %s', async outcome => {
    mountMutations('scrap', true)
    const reads = Promise.all([related.refetch(), versions.refetch()])
    await vi.waitFor(() => expect(auxiliaryRequests).toHaveLength(2))
    const result = mutations.scrap.mutateAsync('3').then(
      () => ({ outcome: 'success' }),
      error => ({ outcome: 'failure', status: error.response?.status }),
    )
    await vi.waitFor(() => expect(mutationRequests).toHaveLength(1))
    mutationOutcome.resolve(outcome)
    expect(await result).toEqual(outcome === 'success' ? { outcome } : { outcome, status: 500 })
    expect(auxiliaryRequests).toHaveLength(2)
    expect(auxiliaryRequests.every(request => !request.signal?.aborted)).toBe(true)
    expect(client.getQueryData(versionKey)).toEqual(originalVersions)
    expect(client.getQueryData(relatedKey)).toEqual(originalRelated)
    auxiliaryResponse.resolve()
    await reads
    expect(client.getQueryData(versionKey)).toEqual(newerVersions)
    expect(client.getQueryData(relatedKey)).toEqual(newerRelated)
  })

  it('does not apply a delayed failure to the next session caches', async () => {
    mountMutations('scrap')
    const result = mutations.scrap.mutateAsync('3').catch(error => error)
    await vi.waitFor(() => expect(mutationRequests).toHaveLength(1))
    auth.sessionGeneration = 2
    auth.user = { userId: 2 }
    auth.accessToken = 'token-b'
    persistAccessToken(auth.accessToken)
    notifyAuthSessionBoundary(2)
    mutationOutcome.resolve('failure')
    expect(await result).toMatchObject({ name: 'AbortError' })
    detailKeys(2).forEach(key => {
      expect(client.getQueryData(key)).toEqual(post('scrap'))
      expect(client.getQueryState(key)?.isInvalidated).toBe(false)
    })
    expect(client.getQueryData(versionKey)).toEqual(originalVersions)
    expect(client.getQueryData(relatedKey)).toEqual(originalRelated)
  })
})

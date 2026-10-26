import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import type { PostPoll } from '@/types'
import { postDetailQueryKey, usePost } from '../usePost'

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

const poll = (selected: boolean): PostPoll => ({
  pollId: 1, question: 'Choose', multipleChoiceEnabled: false, anonymousEnabled: true,
  options: [{ optionId: 7, optionText: 'Option', sortOrder: 0, selected, voteCount: selected ? 1 : 0 }],
})
const post = (selected: boolean) => ({ postId: 3, title: 'Poll post', liked: false, likeCount: 0, poll: poll(selected) })
const key = (generation = 1, incrementView = true, postId: string | number = '3') => sessionQueryKey(generation, postDetailQueryKey(postId, incrementView))
const originalAdapter = api.defaults.adapter
let client: QueryClient
let wrapper: VueWrapper
let hooks: ReturnType<typeof usePost>
let detail: ReturnType<ReturnType<typeof usePost>['usePostDetail']>
let vote: ReturnType<ReturnType<typeof usePost>['useVotePoll']>
let cancelVote: ReturnType<ReturnType<typeof usePost>['useDeletePollVote']>
let like: ReturnType<ReturnType<typeof usePost>['useLikePost']>
let related: ReturnType<ReturnType<typeof usePost>['useRelatedPosts']>
let versions: ReturnType<ReturnType<typeof usePost>['usePostVersions']>
let detailResponse: ReturnType<typeof deferred<ReturnType<typeof post>>>
let pollResponse: ReturnType<typeof deferred<PostPoll>>
let auxiliaryResponse: ReturnType<typeof deferred<never[]>>
let detailRequests: InternalAxiosRequestConfig[]
let pollRequests: InternalAxiosRequestConfig[]
let auxiliaryRequests: InternalAxiosRequestConfig[]

beforeEach(() => {
  vi.clearAllMocks()
  auth.sessionGeneration = 1
  auth.user = { userId: 1 }
  auth.accessToken = 'token-a'
  persistAccessToken(auth.accessToken)
  configureAuthQueryScope(() => auth.sessionGeneration)
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  detailResponse = deferred()
  pollResponse = deferred()
  auxiliaryResponse = deferred()
  detailRequests = []
  pollRequests = []
  auxiliaryRequests = []
  api.defaults.adapter = async config => {
    let data: unknown
    if (config.url === '/posts/3') {
      detailRequests.push(config)
      data = await detailResponse.promise
    } else if (config.url === '/posts/3/poll/vote') {
      pollRequests.push(config)
      data = await pollResponse.promise
    } else if (config.url === '/posts/3/like') {
      data = 1
    } else if (config.url === '/posts/3/related' || config.url === '/posts/3/versions') {
      auxiliaryRequests.push(config)
      data = await auxiliaryResponse.promise
    } else throw new Error(`Unexpected request: ${config.url}`)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } }
  }
  client = new QueryClient({ defaultOptions: {
    queries: { retry: false, staleTime: Infinity, gcTime: Infinity },
    mutations: { retry: false, gcTime: 0 },
  } })
})

function mountPost(selected = false, includeAuxiliary = false) {
  client.setQueryData(key(), post(selected))
  client.setQueryData(key(1, false), post(selected))
  client.setQueryData(key(1, true, 3), post(selected))
  wrapper = mount(defineComponent({
    setup() {
      hooks = usePost()
      detail = hooks.usePostDetail(ref('3'))
      vote = hooks.useVotePoll()
      cancelVote = hooks.useDeletePollVote()
      like = hooks.useLikePost()
      if (includeAuxiliary) {
        related = hooks.useRelatedPosts(ref('3'))
        versions = hooks.usePostVersions(ref('3'), ref(true))
      }
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
}

function switchAccount() {
  auth.sessionGeneration = 2
  auth.user = { userId: 2 }
  auth.accessToken = 'token-b'
  persistAccessToken(auth.accessToken)
  notifyAuthSessionBoundary(2)
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

describe('poll mutations with real Vue Query and Axios', () => {
  it.each(['vote', 'cancel'] as const)('keeps %s results when the earlier like-triggered GET finishes last', async action => {
    const before = action === 'cancel'
    mountPost(before)
    await like.mutateAsync('3')
    await vi.waitFor(() => expect(detailRequests).toHaveLength(1))
    expect(detail.isLoading.value).toBe(false)
    const pending = action === 'vote'
      ? vote.mutateAsync({ postId: 3, data: { optionIds: [7] } })
      : cancelVote.mutateAsync(3)
    await vi.waitFor(() => expect(pollRequests).toHaveLength(1))
    expect(pollRequests[0]!.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
    expect(pollRequests[0]!.method).toBe(action === 'vote' ? 'post' : 'delete')
    pollResponse.resolve(poll(!before))
    await pending
    detailResponse.resolve(post(before))
    await flushPromises()
    expect(detail.data.value?.poll).toEqual(poll(!before))
    expect(client.getQueryData<{ poll: PostPoll }>(key(1, false))?.poll).toEqual(poll(!before))
    expect(client.getQueryData<{ poll: PostPoll }>(key(1, true, 3))?.poll).toEqual(poll(!before))
    expect(detailRequests[0]!.signal?.aborted).toBe(true)
  })

  it('also cancels a refetch started while a vote request is pending', async () => {
    mountPost()
    const pending = vote.mutateAsync({ postId: 3, data: { optionIds: [7] } })
    await vi.waitFor(() => expect(pollRequests).toHaveLength(1))
    const refetch = detail.refetch()
    await vi.waitFor(() => expect(detailRequests).toHaveLength(1))
    pollResponse.resolve(poll(true))
    await pending
    detailResponse.resolve(post(false))
    await refetch
    await flushPromises()
    expect(detail.data.value?.poll).toEqual(poll(true))
  })

  it.each(['vote', 'cancel'] as const)('allows related posts and version history to finish during %s', async action => {
    mountPost(action === 'cancel', true)
    await vi.waitFor(() => expect(auxiliaryRequests).toHaveLength(2))
    const pending = action === 'vote'
      ? vote.mutateAsync({ postId: 3, data: { optionIds: [7] } })
      : cancelVote.mutateAsync(3)
    await vi.waitFor(() => expect(pollRequests).toHaveLength(1))
    pollResponse.resolve(poll(action === 'vote'))
    await pending
    expect(auxiliaryRequests.every(request => !request.signal?.aborted)).toBe(true)
    auxiliaryResponse.resolve([])
    await flushPromises()
    expect(related.data.value).toEqual([])
    expect(versions.data.value).toEqual([])
    expect(related.isSuccess.value).toBe(true)
    expect(versions.isSuccess.value).toBe(true)
  })

  it.each(['vote', 'cancel'] as const)('ignores a delayed %s response after switching accounts', async action => {
    mountPost(action === 'cancel')
    const pending = action === 'vote'
      ? vote.mutateAsync({ postId: 3, data: { optionIds: [7] } })
      : cancelVote.mutateAsync(3)
    const settled = pending.catch(error => error)
    await vi.waitFor(() => expect(pollRequests).toHaveLength(1))
    switchAccount()
    client.setQueryData(key(2), post(false))
    pollResponse.resolve(poll(action === 'vote'))
    expect(await settled).toMatchObject({ name: 'AbortError' })
    expect(client.getQueryData<{ poll: PostPoll }>(key(2))?.poll).toEqual(poll(false))
  })

  it('rechecks the session after awaiting detail cancellation', async () => {
    mountPost()
    const cancellation = deferred<void>()
    const cancelQueries = client.cancelQueries.bind(client)
    const cancel = vi.spyOn(client, 'cancelQueries').mockImplementationOnce(async (...args) => {
      await cancelQueries(...args)
      await cancellation.promise
    })
    const pending = vote.mutateAsync({ postId: 3, data: { optionIds: [7] } })
    const settled = pending.catch(error => error)
    await vi.waitFor(() => expect(pollRequests).toHaveLength(1))
    pollResponse.resolve(poll(true))
    await vi.waitFor(() => expect(cancel).toHaveBeenCalledTimes(4))
    switchAccount()
    client.setQueryData(key(2), post(false))
    cancellation.resolve()
    expect(await settled).toMatchObject({ name: 'AbortError' })
    expect(client.getQueryData<{ poll: PostPoll }>(key(2))?.poll).toEqual(poll(false))
    expect(client.getQueryData<{ poll: PostPoll }>(key())?.poll).toEqual(poll(false))
  })
})

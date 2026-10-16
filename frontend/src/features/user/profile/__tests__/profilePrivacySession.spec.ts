import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { createUserFeatureContext } from '@/features/user/userFeatureContext'
import { useUserProfileFeature } from '../useUserProfileFeature'
import { useUserActivityFeature } from '@/features/user/activity/useUserActivityFeature'
import { useUserSettingsFeature } from '@/features/user/settings/useUserSettingsFeature'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { clearAuthScopedQueries, configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'
import { isProfileSectionPrivate } from '../profilePrivacy'
import type { PublicUserProfile, User } from '@/types'

vi.mock('@/router', () => ({ default: { push: vi.fn() } }))

let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let client: QueryClient
let wrapper: VueWrapper
const adapter = vi.fn()
const oldAdapter = api.defaults.adapter
const activeTab = ref('overview')
const publicFlag = ref(false)
let profile: ReturnType<ReturnType<typeof useUserProfileFeature>['useUserProfile']>
let badges: ReturnType<ReturnType<typeof useUserActivityFeature>['useUserBadges']>
let posts: ReturnType<ReturnType<typeof useUserProfileFeature>['usePublicProfilePosts']>
let comments: ReturnType<ReturnType<typeof useUserProfileFeature>['usePublicProfileComments']>
let mutation: ReturnType<ReturnType<typeof useUserSettingsFeature>['useUpdateUserSettings']>

function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data: { success: true, data }, status: 200, statusText: 'OK', headers: {} }
}
function profileData(): PublicUserProfile {
  const allowed = publicFlag.value || auth.user?.userId === 7
  return {
    userId: 7, displayName: 'Owner', createdAt: allowed ? '2026-01-01' : null,
    postCount: allowed ? 12 : null, commentCount: allowed ? 34 : null,
    profileOverviewPublic: publicFlag.value, profilePostsPublic: publicFlag.value, profileCommentsPublic: publicFlag.value,
    canViewOverview: allowed, canViewPosts: allowed, canViewComments: allowed,
  }
}
function mountResources() {
  wrapper = mount(defineComponent({
    setup() {
      const context = createUserFeatureContext()
      const feature = useUserProfileFeature(context)
      profile = feature.useUserProfile(ref('7'))
      posts = feature.usePublicProfilePosts(ref('7'), ref({ page: 0 }), computed(() => !!profile.data.value?.canViewPosts && activeTab.value === 'posts'))
      comments = feature.usePublicProfileComments(ref('7'), ref({ page: 0 }), computed(() => !!profile.data.value?.canViewComments && activeTab.value === 'comments'))
      badges = useUserActivityFeature(context).useUserBadges(ref('7'), computed(() => !!profile.data.value?.canViewOverview && activeTab.value === 'overview'))
      mutation = useUserSettingsFeature(context).useUpdateUserSettings()
      return () => JSON.stringify({ profile: profile.data.value, badges: badges.data.value, posts: posts.data.value })
    },
  }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
}
function transition(userId: number | null) {
  auth.sessionGeneration += 1
  auth.user = userId === null ? null : { userId } as User
  auth.accessToken = userId === null ? null : 'another-session-token'
  notifyAuthSessionBoundary(auth.sessionGeneration)
  clearAuthScopedQueries(client)
}

beforeEach(() => {
  adapter.mockReset()
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore(pinia)
  auth.accessToken = 'owner-session-token'
  auth.user = { userId: 7 } as User
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 60_000 }, mutations: { retry: false } } })
  api.defaults.adapter = adapter
  activeTab.value = 'overview'
  publicFlag.value = false
  adapter.mockImplementation(async (config: InternalAxiosRequestConfig) => {
    if (config.url === '/users/7') return response(config, profileData())
    if (config.url?.endsWith('/badges')) return response(config, [{ badgeCode: 'PRIVATE_BADGE', acquired: true }])
    if (config.url?.endsWith('/settings')) return response(config, { profileOverviewPublic: false })
    return response(config, { content: [{ title: 'Private activity' }], totalElements: 1, totalPages: 1, number: 0, size: 10 })
  })
})
afterEach(() => {
  wrapper?.unmount()
  client.clear()
  disposePinia(pinia)
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  api.defaults.adapter = oldAdapter
})

describe('profile privacy with real Vue Query and Axios', () => {
  it('waits for profile permissions and only fetches the active allowed tab', async () => {
    const deferred = createDeferred<AxiosResponse>()
    let profileConfig!: InternalAxiosRequestConfig
    adapter.mockImplementationOnce((config: InternalAxiosRequestConfig) => { profileConfig = config; return deferred.promise })
    mountResources()
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledOnce())
    expect(badges.data.value).toBeUndefined()
    deferred.resolve(response(profileConfig, profileData()))
    await vi.waitFor(() => expect(badges.data.value).toHaveLength(1))
    expect(adapter.mock.calls.map(([config]) => config.url)).toEqual(['/users/7', '/users/7/badges'])
    activeTab.value = 'posts'
    await vi.waitFor(() => expect(posts.data.value?.content).toHaveLength(1))
    expect(comments.data.value).toBeUndefined()
    const request = adapter.mock.calls.at(-1)![0]
    expect(request.expectedSession).toEqual({ sessionGeneration: 0, userId: 7 })
    expect(request.signal).toBeDefined()
  })

  it.each([null, 8])('drops owner-only profile and badge data when viewer changes to %s', async (userId) => {
    mountResources()
    await vi.waitFor(() => expect(badges.data.value).toHaveLength(1))
    transition(userId)
    await vi.waitFor(() => expect(profile.data.value?.canViewOverview).toBe(false))
    expect(profile.data.value?.postCount).toBeNull()
    expect(badges.data.value).toBeUndefined()
    expect(client.getQueryData(sessionQueryKey(0, userQueryKeys.badges('7')))).toBeUndefined()
    expect(adapter.mock.calls.filter(([config]) => config.url?.endsWith('/badges'))).toHaveLength(1)
    activeTab.value = 'posts'
    await flushPromises()
    expect(posts.data.value).toBeUndefined()
    expect(adapter.mock.calls.filter(([config]) => config.url?.endsWith('/posts'))).toHaveLength(0)
  })

  it('rejects late owner-only badge responses after logout', async () => {
    const deferred = createDeferred<AxiosResponse>()
    let badgeConfig!: InternalAxiosRequestConfig
    adapter.mockImplementation(async (config: InternalAxiosRequestConfig) => {
      if (config.url === '/users/7') return response(config, profileData())
      badgeConfig = config
      return deferred.promise
    })
    mountResources()
    await vi.waitFor(() => expect(badgeConfig).toBeDefined())
    transition(null)
    await vi.waitFor(() => expect(profile.data.value?.canViewOverview).toBe(false))
    deferred.resolve(response(badgeConfig, [{ badgeCode: 'LATE_PRIVATE_BADGE' }]))
    await flushPromises()
    expect(badgeConfig.signal?.aborted).toBe(true)
    expect(badges.data.value).toBeUndefined()
    expect(wrapper.text()).not.toContain('LATE_PRIVATE_BADGE')
  })

  it('preserves allowed cached data during normal same-session token rotation', async () => {
    mountResources()
    await vi.waitFor(() => expect(badges.data.value).toHaveLength(1))
    auth.accessToken = 'rotated-owner-token'
    await flushPromises()
    expect(profile.data.value?.canViewOverview).toBe(true)
    expect(badges.data.value).toHaveLength(1)
    expect(adapter).toHaveBeenCalledTimes(2)
  })

  it('invalidates profile, all activity pages and badges after a privacy save', async () => {
    mountResources()
    await vi.waitFor(() => expect(badges.data.value).toHaveLength(1))
    const postKey = sessionQueryKey(0, userQueryKeys.publicPosts(7, { page: 2 }))
    const commentKey = sessionQueryKey(0, userQueryKeys.publicComments(7, { page: 3 }))
    const otherKey = sessionQueryKey(1, userQueryKeys.profile(7))
    client.setQueryData(postKey, { content: [] })
    client.setQueryData(commentKey, { content: [] })
    client.setQueryData(otherKey, profileData())
    await mutation.mutateAsync({ profileOverviewPublic: false })
    expect(client.getQueryState(postKey)?.isInvalidated).toBe(true)
    expect(client.getQueryState(commentKey)?.isInvalidated).toBe(true)
    expect(client.getQueryState(otherKey)?.isInvalidated).toBe(false)
    await vi.waitFor(() => expect(adapter.mock.calls.filter(([config]) => config.url === '/users/7')).toHaveLength(2))
    await vi.waitFor(() => expect(adapter.mock.calls.filter(([config]) => config.url === '/users/7/badges')).toHaveLength(2))
  })

  it('does not invalidate a new session after an old privacy save completes', async () => {
    mountResources()
    await vi.waitFor(() => expect(badges.data.value).toHaveLength(1))
    const deferred = createDeferred<AxiosResponse>()
    let saveConfig!: InternalAxiosRequestConfig
    adapter.mockImplementationOnce((config: InternalAxiosRequestConfig) => { saveConfig = config; return deferred.promise })
    const pending = mutation.mutateAsync({ profilePostsPublic: false })
    const settled = pending.then(() => null, (error: unknown) => error)
    await vi.waitFor(() => expect(saveConfig).toBeDefined())
    transition(8)
    await vi.waitFor(() => expect(profile.data.value?.canViewPosts).toBe(false))
    const nextKey = sessionQueryKey(1, userQueryKeys.publicPosts(7, { page: 2 }))
    client.setQueryData(nextKey, { content: [] })
    deferred.resolve(response(saveConfig, { profilePostsPublic: false }))
    expect(await settled).toMatchObject({ name: 'AbortError' })
    expect(saveConfig.signal?.aborted).toBe(true)
    expect(client.getQueryState(nextKey)?.isInvalidated).toBe(false)
    expect(profile.data.value?.canViewPosts).toBe(false)
  })

  it('recognizes only the dedicated private-section 403 as privacy', () => {
    const config = {} as InternalAxiosRequestConfig
    const privateError = new AxiosError('private', 'ERR_BAD_REQUEST', config, null, { ...response(config, null), status: 403, data: { error: { code: 'U012' } } })
    expect(isProfileSectionPrivate(privateError)).toBe(true)
    privateError.response!.data.error.code = 'U009'
    expect(isProfileSectionPrivate(privateError)).toBe(false)
  })
})

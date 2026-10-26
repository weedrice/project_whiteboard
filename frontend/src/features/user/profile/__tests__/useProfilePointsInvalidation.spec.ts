import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useAuthStore } from '@/stores/auth'
import { createUserFeatureContext } from '@/features/user/userFeatureContext'
import { useUserProfileFeature } from '../useUserProfileFeature'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { configureAuthQueryScope, sessionQueryKey } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'
import { axiosApiSuccess } from '@/test/factories'
import type { User } from '@/types'

const mocks = vi.hoisted(() => ({ updateMyProfile: vi.fn(), getMyPoint: vi.fn(), getPointHistory: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { updateMyProfile: mocks.updateMyProfile } }))

let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let client: QueryClient
let wrapper: VueWrapper
let mutation: ReturnType<ReturnType<typeof useUserProfileFeature>['useUpdateMyProfile']>

beforeEach(() => {
  vi.resetAllMocks()
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore(pinia)
  auth.accessToken = 'current-session-token'
  auth.user = { userId: 7 } as User
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 60_000 }, mutations: { retry: false } } })
  wrapper = mount(defineComponent({
    setup() {
      mutation = useUserProfileFeature(createUserFeatureContext()).useUpdateMyProfile()
      return () => null
    },
  }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
})

afterEach(() => {
  wrapper.unmount()
  client.clear()
  disposePinia(pinia)
  configureAuthQueryScope(() => 0)
})

function profileResponse(spentPoints: number | null) {
  return axiosApiSuccess({
    userId: 7, displayName: 'Owner', profileImageUrl: '/new.png', spentPoints,
    remainingPoints: spentPoints === null ? null : 6000 - spentPoints,
  })
}

function seedPointCaches() {
  const pointKey = sessionQueryKey(0, userQueryKeys.myPoints(7))
  const historyKey = sessionQueryKey(0, userQueryKeys.pointHistories({ page: 0 }))
  const olderHistoryKey = sessionQueryKey(0, userQueryKeys.pointHistories({ page: 1 }))
  const otherSessionKey = sessionQueryKey(1, userQueryKeys.myPoints(8))
  const settingsKey = sessionQueryKey(0, userQueryKeys.settings)
  client.setQueryData(pointKey, { currentPoint: 6000 })
  client.setQueryData(historyKey, { content: [] })
  client.setQueryData(olderHistoryKey, { content: [] })
  client.setQueryData(otherSessionKey, { currentPoint: 9000 })
  client.setQueryData(settingsKey, { theme: 'LIGHT' })
  return { pointKey, historyKey, olderHistoryKey, otherSessionKey, settingsKey }
}

describe('paid profile image change and point caches', () => {
  it.each([
    { payload: { displayName: 'Renamed' }, profileImageUrl: '/old.png' },
    { payload: { removeProfileImage: true }, profileImageUrl: null },
    { payload: { profileImageId: 501 }, profileImageUrl: '/old.png' },
  ])('preserves unused free change and balance for a name edit, removal, or image no-op: $payload', async ({ payload, profileImageUrl }) => {
    auth.user = {
      ...auth.user!, points: 6000, profileImageUrl: '/old.png', profileImageChangeFreeAvailable: true,
    }
    mocks.updateMyProfile.mockResolvedValueOnce(axiosApiSuccess({
      userId: 7, displayName: 'Renamed', profileImageUrl, spentPoints: null, remainingPoints: null,
    }))

    await mutation.mutateAsync(payload)

    expect(auth.user).toMatchObject({
      displayName: 'Renamed', profileImageUrl: profileImageUrl ?? undefined, points: 6000, profileImageChangeFreeAvailable: true,
    })
  })

  it.each(['mounted', 'unmounted'] as const)('refreshes fresh balance and history after a paid save while %s', async (lifetime) => {
    const keys = seedPointCaches()
    const response = createDeferred<ReturnType<typeof profileResponse>>()
    mocks.updateMyProfile.mockReturnValueOnce(response.promise)
    mocks.getMyPoint.mockResolvedValue({ currentPoint: 5000 })
    mocks.getPointHistory.mockResolvedValue({ content: [{ historyId: 1, amount: -1000 }] })
    const pending = mutation.mutateAsync({ profileImageId: 501 })
    await flushPromises()
    expect(mocks.updateMyProfile).toHaveBeenCalledOnce()
    if (lifetime === 'unmounted') wrapper.unmount()

    response.resolve(profileResponse(1000))
    await pending

    expect(client.getQueryState(keys.pointKey)?.isInvalidated).toBe(true)
    expect(client.getQueryState(keys.historyKey)?.isInvalidated).toBe(true)
    expect(client.getQueryState(keys.olderHistoryKey)?.isInvalidated).toBe(true)
    expect(client.getQueryState(keys.otherSessionKey)?.isInvalidated).toBe(false)
    expect(client.getQueryState(keys.settingsKey)?.isInvalidated).toBe(false)

    const [point, history] = await Promise.all([
      client.fetchQuery({ queryKey: keys.pointKey, queryFn: mocks.getMyPoint }),
      client.fetchQuery({ queryKey: keys.historyKey, queryFn: mocks.getPointHistory }),
    ])
    expect(point).toEqual({ currentPoint: 5000 })
    expect(history).toEqual({ content: [{ historyId: 1, amount: -1000 }] })
    expect(mocks.getMyPoint).toHaveBeenCalledOnce()
    expect(mocks.getPointHistory).toHaveBeenCalledOnce()
  })

  it.each([null, 0])('keeps point caches fresh when the successful save spends %s points', async (spentPoints) => {
    const keys = seedPointCaches()
    mocks.updateMyProfile.mockResolvedValueOnce(profileResponse(spentPoints))

    await mutation.mutateAsync({ profileImageId: 501 })

    expect(client.getQueryState(keys.pointKey)?.isInvalidated).toBe(false)
    expect(client.getQueryState(keys.historyKey)?.isInvalidated).toBe(false)
    expect(client.getQueryState(keys.olderHistoryKey)?.isInvalidated).toBe(false)
  })

  it('does not invalidate point caches when the profile write fails', async () => {
    const keys = seedPointCaches()
    mocks.updateMyProfile.mockRejectedValueOnce(new Error('Write rejected'))

    await expect(mutation.mutateAsync({ profileImageId: 501 })).rejects.toThrow('Write rejected')

    expect(client.getQueryState(keys.pointKey)?.isInvalidated).toBe(false)
    expect(client.getQueryState(keys.historyKey)?.isInvalidated).toBe(false)
  })

  it('does not invalidate either session after a paid response belongs to a previous session', async () => {
    const keys = seedPointCaches()
    const response = createDeferred<ReturnType<typeof profileResponse>>()
    mocks.updateMyProfile.mockReturnValueOnce(response.promise)
    const pending = mutation.mutateAsync({ profileImageId: 501 })
    await flushPromises()
    auth.sessionGeneration += 1
    auth.user = { userId: 8 } as User

    response.resolve(profileResponse(1000))
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })

    expect(client.getQueryState(keys.pointKey)?.isInvalidated).toBe(false)
    expect(client.getQueryState(keys.historyKey)?.isInvalidated).toBe(false)
    expect(client.getQueryState(keys.otherSessionKey)?.isInvalidated).toBe(false)
  })
})

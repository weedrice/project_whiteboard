import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useAuthStore } from '@/stores/auth'
import { createUserFeatureContext } from '@/features/user/userFeatureContext'
import { useUserProfileFeature } from '../useUserProfileFeature'
import { useProfileUpdateSubmit } from '../useProfileUpdateSubmit'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { configureAuthQueryScope, sessionQueryKey } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'
import { axiosApiSuccess } from '@/test/factories'
import type { User } from '@/types'

const mocks = vi.hoisted(() => ({
  updateMyProfile: vi.fn(), getMe: vi.fn(), uploadFile: vi.fn(), discardUploads: vi.fn(),
}))
vi.mock('@/api/user', () => ({ userApi: { updateMyProfile: mocks.updateMyProfile } }))
vi.mock('@/api/auth', () => ({ authApi: { getMe: mocks.getMe } }))
vi.mock('@/api/file', () => ({ fileApi: { uploadFile: mocks.uploadFile, discardUploads: mocks.discardUploads } }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn(), warn: vi.fn() } }))

let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let client: QueryClient
let serverUser: User
const wrappers: VueWrapper[] = []

beforeEach(() => {
  vi.resetAllMocks()
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore(pinia)
  auth.accessToken = 'same-session-token'
  auth.user = {
    userId: 7, loginId: 'owner', displayName: 'Old Name', email: 'owner@example.com',
    role: 'USER', status: 'ACTIVE', createdAt: '2026-10-01', isEmailVerified: true,
    profileImageUrl: '/old.png', profileImageChangeFreeAvailable: true,
    profileImageChangeCost: 1000, points: 6000,
  } as User
  serverUser = { ...auth.user, displayName: 'Updated Name', profileImageUrl: '/new.png', profileImageChangeFreeAvailable: false }
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 60_000 }, mutations: { retry: false } } })
  mocks.uploadFile.mockResolvedValue(axiosApiSuccess({ fileId: 501 }))
  mocks.discardUploads.mockResolvedValue(axiosApiSuccess({ discardedCount: 1 }))
  mocks.getMe.mockImplementation(async () => axiosApiSuccess({ ...serverUser }))
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  client.clear()
  disposePinia(pinia)
  configureAuthQueryScope(() => 0)
})

function savedProfile() {
  return axiosApiSuccess({
    userId: 7, displayName: 'Updated Name', profileImageUrl: '/new.png',
    spentPoints: null, remainingPoints: null,
  })
}

function mountSubmit() {
  const confirm = vi.fn().mockResolvedValue(false)
  const addToast = vi.fn()
  const onClose = vi.fn()
  const onRefreshed = vi.fn()
  let submitter!: ReturnType<typeof useProfileUpdateSubmit>
  const wrapper = mount(defineComponent({
    setup() {
      const mutation = useUserProfileFeature(createUserFeatureContext()).useUpdateMyProfile()
      submitter = useProfileUpdateSubmit({
        selectedFile: ref(new File(['photo'], 'profile.png', { type: 'image/png' })),
        getDisplayName: () => 'Updated Name',
        updateProfile: mutation.mutateAsync,
        refreshUser: auth.fetchUser,
        authSession: auth,
        confirm,
        isProfileImageChangeFree: () => auth.user?.profileImageChangeFreeAvailable !== false,
        getProfileImageChangeCost: () => auth.user?.profileImageChangeCost ?? 1000,
        getCurrentPoints: () => auth.user?.points ?? 0,
        addToast,
        t: key => key,
        onRefreshed,
        onClose,
      })
      return () => `${auth.user?.displayName}; free=${auth.user?.profileImageChangeFreeAvailable}`
    },
  }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
  wrappers.push(wrapper)
  return { wrapper, submitter, confirm, addToast, onClose, onRefreshed }
}

function changeAccount() {
  auth.sessionGeneration += 1
  auth.accessToken = 'next-account-token'
  auth.user = { ...serverUser, userId: 8, displayName: 'Other User', profileImageUrl: '/other.png' }
}

describe('profile write completion and editor lifetime', () => {
  it('refreshes shared auth state after unmount and requires the paid confirmation in the next editor', async () => {
    const write = createDeferred<ReturnType<typeof savedProfile>>()
    mocks.updateMyProfile.mockReturnValueOnce(write.promise)
    const meKey = sessionQueryKey(auth.sessionGeneration, userQueryKeys.me)
    client.setQueryData(meKey, { ...auth.user })
    const first = mountSubmit()
    const pending = first.submitter.updateProfile()
    await flushPromises()
    expect(mocks.updateMyProfile).toHaveBeenCalledOnce()

    first.wrapper.unmount()
    write.resolve(savedProfile())
    await pending

    expect(client.getQueryState(meKey)?.isInvalidated).toBe(true)
    expect(mocks.getMe).toHaveBeenCalledOnce()
    expect(auth.user).toMatchObject({ displayName: 'Updated Name', profileImageUrl: '/new.png', profileImageChangeFreeAvailable: false })
    expect(mocks.discardUploads).not.toHaveBeenCalled()
    expect(first.addToast).not.toHaveBeenCalled()
    expect(first.onClose).not.toHaveBeenCalled()
    expect(first.onRefreshed).not.toHaveBeenCalled()

    const reopened = mountSubmit()
    await reopened.submitter.updateProfile()
    expect(reopened.wrapper.text()).toBe('Updated Name; free=false')
    expect(reopened.confirm).toHaveBeenCalledExactlyOnceWith('user.profile.profileImageCostConfirm')
    expect(mocks.updateMyProfile).toHaveBeenCalledTimes(1)
  })

  it('finishes auth refresh when the editor closes while that refresh is pending', async () => {
    const hydration = createDeferred<ReturnType<typeof axiosApiSuccess<User>>>()
    mocks.updateMyProfile.mockResolvedValueOnce(savedProfile())
    mocks.getMe.mockReturnValueOnce(hydration.promise)
    const mounted = mountSubmit()
    const pending = mounted.submitter.updateProfile()
    await flushPromises()
    expect(mocks.getMe).toHaveBeenCalledOnce()

    mounted.wrapper.unmount()
    hydration.resolve(axiosApiSuccess(serverUser))
    await pending

    expect(auth.user?.displayName).toBe('Updated Name')
    expect(mounted.addToast).not.toHaveBeenCalled()
    expect(mounted.onClose).not.toHaveBeenCalled()
    expect(mounted.onRefreshed).not.toHaveBeenCalled()
  })

  it('does not refresh or update the next account after a previous-session write succeeds', async () => {
    const write = createDeferred<ReturnType<typeof savedProfile>>()
    mocks.updateMyProfile.mockReturnValueOnce(write.promise)
    const mounted = mountSubmit()
    const pending = mounted.submitter.updateProfile()
    await flushPromises()
    expect(mocks.updateMyProfile).toHaveBeenCalledOnce()
    mounted.wrapper.unmount()
    changeAccount()

    write.resolve(savedProfile())
    await pending

    expect(mocks.getMe).not.toHaveBeenCalled()
    expect(auth.user).toMatchObject({ userId: 8, displayName: 'Other User', profileImageUrl: '/other.png' })
    expect(mocks.discardUploads).not.toHaveBeenCalled()
    expect(mounted.addToast).not.toHaveBeenCalled()
  })

  it('rejects an old auth hydration response after an account switch during refresh', async () => {
    const hydration = createDeferred<ReturnType<typeof axiosApiSuccess<User>>>()
    mocks.updateMyProfile.mockResolvedValueOnce(savedProfile())
    mocks.getMe.mockReturnValueOnce(hydration.promise)
    const mounted = mountSubmit()
    const pending = mounted.submitter.updateProfile()
    await flushPromises()
    expect(mocks.getMe).toHaveBeenCalledOnce()
    changeAccount()

    hydration.resolve(axiosApiSuccess(serverUser))
    await pending

    expect(auth.user).toMatchObject({ userId: 8, displayName: 'Other User', profileImageUrl: '/other.png' })
    expect(mounted.addToast).not.toHaveBeenCalled()
    expect(mounted.onRefreshed).not.toHaveBeenCalled()
    expect(mounted.onClose).not.toHaveBeenCalled()
  })

  it('cleans a rejected upload association after unmount without refreshing or showing an error', async () => {
    const write = createDeferred<ReturnType<typeof savedProfile>>()
    mocks.updateMyProfile.mockReturnValueOnce(write.promise)
    const mounted = mountSubmit()
    const pending = mounted.submitter.updateProfile()
    await flushPromises()
    expect(mocks.updateMyProfile).toHaveBeenCalledOnce()
    mounted.wrapper.unmount()

    write.reject({ response: { status: 400, data: { error: { message: 'Rejected' } } } })
    await pending

    expect(mocks.discardUploads).toHaveBeenCalledExactlyOnceWith([501], expect.objectContaining({
      expectedSession: { sessionGeneration: 0, userId: 7 },
    }))
    expect(mocks.getMe).not.toHaveBeenCalled()
    expect(auth.user?.displayName).toBe('Old Name')
    expect(mounted.addToast).not.toHaveBeenCalled()
    expect(mounted.onClose).not.toHaveBeenCalled()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, reactive, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import ProfileBasicForm from '@/components/user/ProfileBasicForm.vue'
import { useProfileUpdateSubmit } from '../useProfileUpdateSubmit'
import { createDeferred } from '@/test/async'
import { axiosApiSuccess } from '@/test/factories'

const mocks = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  discardUploads: vi.fn(),
}))

vi.mock('@/api/file', () => ({ fileApi: mocks }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn(), warn: vi.fn() } }))

function mountProfileSubmit() {
  const confirm = vi.fn().mockResolvedValue(true)
  const updateProfile = vi.fn().mockResolvedValue(undefined)
  const refreshUser = vi.fn().mockResolvedValue(undefined)
  const addToast = vi.fn()
  const onClose = vi.fn()
  const onRefreshed = vi.fn()
  const authSession = reactive({ sessionGeneration: 1, user: { userId: 7 }, accessToken: 'initial-token' })
  const selectedFile = ref<File | null>(new File(['photo'], 'profile.png', { type: 'image/png' }))
  const submissions: Promise<void>[] = []
  let nextFileId = 501
  mocks.uploadFile.mockImplementation(async () => axiosApiSuccess({ fileId: nextFileId++ }))
  mocks.discardUploads.mockResolvedValue(axiosApiSuccess({ discardedCount: 1 }))
  let submitter!: ReturnType<typeof useProfileUpdateSubmit>
  const wrapper = mount(defineComponent({
    setup() {
      submitter = useProfileUpdateSubmit({
        selectedFile,
        getDisplayName: () => 'Test User',
        updateProfile,
        refreshUser,
        addToast,
        t: key => key,
        confirm,
        isProfileImageChangeFree: () => false,
        getProfileImageChangeCost: () => 10,
        getCurrentPoints: () => 100,
        onRefreshed,
        onClose,
        authSession,
      })
      return () => h(ProfileBasicForm, {
        displayName: 'Test User',
        profileImageDisplayUrl: '',
        profileImageError: false,
        onSubmit: () => { submissions.push(submitter.updateProfile()) },
      })
    },
  }))

  async function submitForm() {
    await wrapper.get('form').trigger('submit')
    await flushPromises()
  }

  return {
    wrapper, submitter, submissions, submitForm, confirm, updateProfile,
    refreshUser, addToast, onClose, onRefreshed, authSession, selectedFile,
  }
}

describe('profile form submission lifecycle', () => {
  beforeEach(() => { vi.resetAllMocks() })

  it.each(['confirmation', 'upload', 'write', 'refresh'] as const)(
    'ignores repeated form submission while awaiting %s, including after ordinary token rotation',
    async (stage) => {
      const harness = mountProfileSubmit()
      const gate = createDeferred<void>()
      const pause = {
        confirmation: () => harness.confirm.mockImplementationOnce(async () => {
          await gate.promise
          return true
        }),
        upload: () => mocks.uploadFile.mockImplementationOnce(async () => {
          await gate.promise
          return axiosApiSuccess({ fileId: 501 })
        }),
        write: () => harness.updateProfile.mockReturnValueOnce(gate.promise),
        refresh: () => harness.refreshUser.mockReturnValueOnce(gate.promise),
      }
      pause[stage]()

      try {
        await harness.submitForm()
        expect(harness.submitter.loading.value).toBe(true)
        const beforeRepeat = [
          harness.confirm.mock.calls.length, mocks.uploadFile.mock.calls.length,
          harness.updateProfile.mock.calls.length, harness.refreshUser.mock.calls.length,
        ]

        harness.authSession.accessToken = 'rotated-token'
        await harness.submitForm()

        expect(harness.submitter.loading.value).toBe(true)
        expect([
          harness.confirm.mock.calls.length, mocks.uploadFile.mock.calls.length,
          harness.updateProfile.mock.calls.length, harness.refreshUser.mock.calls.length,
        ]).toEqual(beforeRepeat)

        gate.resolve(undefined)
        await Promise.all(harness.submissions)
        expect(harness.confirm).toHaveBeenCalledOnce()
        expect(mocks.uploadFile).toHaveBeenCalledOnce()
        expect(harness.updateProfile).toHaveBeenCalledExactlyOnceWith({ displayName: 'Test User', profileImageId: 501 })
        expect(harness.refreshUser).toHaveBeenCalledOnce()
        expect(harness.onClose).toHaveBeenCalledOnce()
        expect(harness.submitter.loading.value).toBe(false)
      } finally {
        gate.resolve(undefined)
        await Promise.allSettled(harness.submissions)
        harness.wrapper.unmount()
      }
    },
  )

  it('allows another submission after the image-change confirmation is canceled', async () => {
    const harness = mountProfileSubmit()
    harness.confirm.mockResolvedValueOnce(false)

    await harness.submitForm()

    expect(harness.submitter.loading.value).toBe(false)
    expect(mocks.uploadFile).not.toHaveBeenCalled()
    await harness.submitForm()

    expect(harness.confirm).toHaveBeenCalledTimes(2)
    expect(mocks.uploadFile).toHaveBeenCalledOnce()
    expect(harness.updateProfile).toHaveBeenCalledExactlyOnceWith({ displayName: 'Test User', profileImageId: 501 })
    expect(harness.onClose).toHaveBeenCalledOnce()
    harness.wrapper.unmount()
  })

  it('allows another submission after a failed final write', async () => {
    const harness = mountProfileSubmit()
    harness.updateProfile.mockRejectedValueOnce(new Error('Save failed'))

    await harness.submitForm()

    expect(harness.submitter.loading.value).toBe(false)
    expect(harness.onClose).not.toHaveBeenCalled()
    await harness.submitForm()

    expect(harness.updateProfile.mock.calls.map(([payload]) => payload)).toEqual([
      { displayName: 'Test User', profileImageId: 501 },
      { displayName: 'Test User', profileImageId: 502 },
    ])
    expect(harness.onClose).toHaveBeenCalledOnce()
    expect(harness.submitter.loading.value).toBe(false)
    harness.wrapper.unmount()
  })

  it('allows the new session to save while an old completion cannot release its pending state', async () => {
    const harness = mountProfileSubmit()
    const firstWrite = createDeferred<void>()
    const secondWrite = createDeferred<void>()
    harness.updateProfile.mockReturnValueOnce(firstWrite.promise).mockReturnValueOnce(secondWrite.promise)

    try {
      await harness.submitForm()
      expect(harness.updateProfile).toHaveBeenCalledExactlyOnceWith({ displayName: 'Test User', profileImageId: 501 })

      harness.authSession.sessionGeneration += 1
      harness.authSession.user = { userId: 8 }
      harness.selectedFile.value = new File(['new photo'], 'new-profile.png', { type: 'image/png' })
      expect(harness.submitter.loading.value).toBe(false)
      await harness.submitForm()

      expect(harness.updateProfile).toHaveBeenCalledTimes(2)
      expect(mocks.uploadFile.mock.calls.map(([, config]) => config.expectedSession)).toEqual([
        { sessionGeneration: 1, userId: 7 },
        { sessionGeneration: 2, userId: 8 },
      ])
      firstWrite.resolve(undefined)
      await harness.submissions[0]
      expect(harness.submitter.loading.value).toBe(true)
      expect(harness.onClose).not.toHaveBeenCalled()
      expect(harness.addToast).not.toHaveBeenCalled()

      await harness.submitForm()
      expect(harness.updateProfile).toHaveBeenCalledTimes(2)
      secondWrite.resolve(undefined)
      await Promise.all(harness.submissions)

      expect(harness.onClose).toHaveBeenCalledOnce()
      expect(harness.refreshUser).toHaveBeenCalledOnce()
      expect(harness.submitter.loading.value).toBe(false)
    } finally {
      firstWrite.resolve(undefined)
      secondWrite.resolve(undefined)
      await Promise.allSettled(harness.submissions)
      harness.wrapper.unmount()
    }
  })
})

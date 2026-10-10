import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryNew from '../InquiryNew.vue'
import { configureAuthQueryScope } from '@/queryAuthScope'
import { useAuthStore } from '@/stores/auth'
import { createDeferred } from '@/test/async'
import type { User } from '@/types/user'

const calls = vi.hoisted(() => ({
  create: vi.fn(),
  replace: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/inquiry', () => ({ inquiryApi: { create: calls.create } }))
vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({ push: calls.push, replace: calls.replace }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

const user = (userId: number): User => ({
  userId,
  loginId: `user-${userId}`,
  displayName: `User ${userId}`,
  email: `user-${userId}@example.test`,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
})
const created = (inquiryId = 42) => ({ data: { success: true, data: { inquiryId } } })

interface ViewState {
  title: string
  fileIds: number[]
  createdInquiryId: number | null
  errorMessage: string
}

let pinia: Pinia
let authStore: ReturnType<typeof useAuthStore>
let queryClient: QueryClient
const wrappers: VueWrapper[] = []

function mountView() {
  const uploader = {
    beginSubmission: vi.fn(() => true),
    commitUploads: vi.fn(),
    failSubmission: vi.fn().mockResolvedValue(undefined),
    discardUploads: vi.fn().mockResolvedValue(undefined),
  }
  const uploaderStub = defineComponent({
    setup(_, { expose }) {
      expose(uploader)
      return () => h('div')
    },
  })
  const wrapper = mount(InquiryNew, {
    global: {
      plugins: [pinia, [VueQueryPlugin, { queryClient }]],
      stubs: {
        InquiryImageUploader: uploaderStub,
        BaseButton: { props: ['disabled', 'to'], template: '<button :disabled="disabled" :data-to="to"><slot /></button>' },
        PageHeader: { template: '<header><slot name="actions" /></header>' },
      },
    },
  })
  wrappers.push(wrapper)
  return { wrapper, uploader, view: wrapper.vm as unknown as ViewState }
}

async function submit(wrapper: VueWrapper) {
  await wrapper.get('input').setValue('문의 제목')
  await wrapper.get('textarea').setValue('문의 내용')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  calls.create.mockReset()
  calls.replace.mockReset().mockResolvedValue(undefined)
  calls.push.mockReset().mockResolvedValue(undefined)
  pinia = createPinia()
  setActivePinia(pinia)
  authStore = useAuthStore(pinia)
  authStore.sessionGeneration = 1
  authStore.accessToken = 'session-token'
  authStore.user = user(1)
  configureAuthQueryScope(() => authStore.sessionGeneration, () => authStore.user?.userId ?? null)
  queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity },
      mutations: { retry: false, gcTime: 0 },
    },
  })
  queryClient.setQueryData(['session', 1, 'inquiries', 'mine'], [])
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  queryClient.clear()
  disposePinia(pinia)
  setActivePinia(undefined)
  configureAuthQueryScope(() => 0)
})

describe('InquiryNew with real Vue Query', () => {
  it('commits and invalidates a successful submission after unmount without changing either view or navigating', async () => {
    const response = createDeferred<ReturnType<typeof created>>()
    calls.create.mockReturnValueOnce(response.promise)
    const original = mountView()
    original.view.fileIds = [11]
    await submit(original.wrapper)
    expect(calls.create).toHaveBeenCalledExactlyOnceWith({
      category: 'SERVICE_USE', title: '문의 제목', content: '문의 내용', fileIds: [11],
    }, expect.objectContaining({
      expectedSession: { sessionGeneration: 1, userId: 1 }, signal: expect.any(AbortSignal),
    }))

    original.wrapper.unmount()
    const replacement = mountView()
    await replacement.wrapper.get('input').setValue('새 문의 제목')
    response.resolve(created())
    await flushPromises()

    expect(original.uploader.commitUploads).toHaveBeenCalledOnce()
    expect(original.uploader.failSubmission).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(['session', 1, 'inquiries', 'mine'])?.isInvalidated).toBe(true)
    expect(calls.replace).not.toHaveBeenCalled()
    expect(original.view.createdInquiryId).toBeNull()
    expect(original.view.fileIds).toEqual([11])
    expect(replacement.view.title).toBe('새 문의 제목')
    expect(replacement.view.createdInquiryId).toBeNull()
    expect(replacement.uploader.commitUploads).not.toHaveBeenCalled()
  })

  it('does not navigate when the view unmounts while successful cache invalidation is pending', async () => {
    const refresh = createDeferred<void>()
    const invalidate = queryClient.invalidateQueries.bind(queryClient)
    vi.spyOn(queryClient, 'invalidateQueries').mockImplementationOnce(async (filters, options) => {
      await invalidate(filters, options)
      await refresh.promise
    })
    calls.create.mockResolvedValueOnce(created())
    const { wrapper, view, uploader } = mountView()
    await submit(wrapper)
    expect(view.createdInquiryId).toBe(42)
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    expect(calls.replace).not.toHaveBeenCalled()

    wrapper.unmount()
    refresh.resolve()
    await flushPromises()

    expect(uploader.commitUploads).toHaveBeenCalledOnce()
    expect(uploader.failSubmission).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(['session', 1, 'inquiries', 'mine'])?.isInvalidated).toBe(true)
    expect(calls.replace).not.toHaveBeenCalled()
  })

  it('cleans up failed submission resources after unmount without updating the abandoned form', async () => {
    const response = createDeferred<ReturnType<typeof created>>()
    calls.create.mockReturnValueOnce(response.promise)
    const { wrapper, view, uploader } = mountView()
    view.fileIds = [11]
    await submit(wrapper)
    wrapper.unmount()
    response.reject(new Error('creation failed'))
    await flushPromises()

    expect(uploader.failSubmission).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      expectedSession: { sessionGeneration: 1, userId: 1 }, signal: expect.any(AbortSignal),
    }))
    expect(uploader.commitUploads).not.toHaveBeenCalled()
    expect(calls.replace).not.toHaveBeenCalled()
    expect(view.errorMessage).toBe('')
    expect(view.fileIds).toEqual([11])
    expect(queryClient.getQueryState(['session', 1, 'inquiries', 'mine'])?.isInvalidated).toBe(false)
  })

  it('preserves same-session token rotation, navigation recovery and duplicate submission protection', async () => {
    const response = createDeferred<ReturnType<typeof created>>()
    calls.create.mockReturnValueOnce(response.promise)
    calls.replace.mockRejectedValueOnce(new Error('navigation failed'))
    const { wrapper, view, uploader } = mountView()
    await submit(wrapper)
    authStore.accessToken = 'rotated-session-token'
    response.resolve(created())
    await flushPromises()

    expect(uploader.commitUploads).toHaveBeenCalledOnce()
    expect(uploader.failSubmission).not.toHaveBeenCalled()
    expect(calls.replace).toHaveBeenCalledExactlyOnceWith('/inquiries/42')
    expect(view.createdInquiryId).toBe(42)
    expect(wrapper.get('[role="alert"]').text()).toBe('inquiry.form.createdNavigationFailed')
    expect(wrapper.get('[data-to="/inquiries/42"]').text()).toBe('inquiry.form.openCreated')
    await wrapper.get('form').trigger('submit')
    expect(calls.create).toHaveBeenCalledOnce()
  })

  it('opens the created inquiry after a mounted submission succeeds', async () => {
    calls.create.mockResolvedValueOnce(created())
    const { wrapper, view, uploader } = mountView()
    await submit(wrapper)

    expect(calls.replace).toHaveBeenCalledExactlyOnceWith('/inquiries/42')
    expect(uploader.commitUploads).toHaveBeenCalledOnce()
    expect(uploader.failSubmission).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(['session', 1, 'inquiries', 'mine'])?.isInvalidated).toBe(true)
    expect(view.createdInquiryId).toBe(42)
    expect(view.errorMessage).toBe('')
  })

  it('does not publish navigation errors after the view unmounts during navigation', async () => {
    const navigation = createDeferred<void>()
    calls.create.mockResolvedValueOnce(created())
    calls.replace.mockReturnValueOnce(navigation.promise)
    const { wrapper, view, uploader } = mountView()
    await submit(wrapper)
    expect(calls.replace).toHaveBeenCalledExactlyOnceWith('/inquiries/42')
    wrapper.unmount()
    navigation.reject(new Error('navigation failed'))
    await flushPromises()

    expect(view.errorMessage).toBe('')
    expect(uploader.failSubmission).not.toHaveBeenCalled()
  })

  it.each(['generation', 'identity'] as const)('rejects a changed %s while waiting for invalidation', async (boundary) => {
    const refresh = createDeferred<void>()
    vi.spyOn(queryClient, 'invalidateQueries').mockReturnValueOnce(refresh.promise)
    calls.create.mockResolvedValueOnce(created())
    const { wrapper, view, uploader } = mountView()
    await submit(wrapper)
    expect(view.createdInquiryId).toBe(42)
    authStore.$patch(boundary === 'generation'
      ? { sessionGeneration: 2, user: user(2) }
      : { user: user(2) })
    refresh.resolve()
    await flushPromises()

    expect(calls.replace).not.toHaveBeenCalled()
    expect(view.createdInquiryId).toBeNull()
    expect(view.errorMessage).toBe('')
    expect(uploader.commitUploads).toHaveBeenCalledOnce()
    expect(uploader.failSubmission).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      expectedSession: { sessionGeneration: 1, userId: 1 },
    }))
  })
})

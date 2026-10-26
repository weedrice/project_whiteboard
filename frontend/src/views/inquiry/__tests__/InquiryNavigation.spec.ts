import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { defineComponent, h, onBeforeUnmount } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryNew from '../InquiryNew.vue'
import InquiryDetail from '../InquiryDetail.vue'
import { configureAuthQueryScope } from '@/queryAuthScope'
import { useAuthStore } from '@/stores/auth'
import { whenPwaReloadSafe } from '@/pwaReloadGuard'
import { createDeferred } from '@/test/async'

const calls = vi.hoisted(() => ({
  create: vi.fn(), detail: vi.fn(), message: vi.fn(), confirm: vi.fn(), discard: vi.fn(), commit: vi.fn(),
}))
vi.mock('@/api/inquiry', () => ({ inquiryApi: {
  create: calls.create, getMineDetail: calls.detail, addMessage: calls.message,
} }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: calls.confirm }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

const response = (inquiryId = 41) => ({ data: { success: true, data: {
  inquiryId, title: 'Question', status: 'NEW', category: 'OTHER', messages: [],
  allowedActions: { canAddMessage: true, canWithdraw: false, canClose: false },
} } })
const UploaderStub = defineComponent({
  emits: ['uploading', 'update:modelValue'],
  setup(_, { expose }) {
    expose({ beginSubmission: () => true, commitUploads: calls.commit,
      discardUploads: calls.discard, failSubmission: vi.fn() })
    onBeforeUnmount(calls.discard)
    return () => h('div')
  },
})
let pinia: Pinia
let queryClient: QueryClient
let router: Router
let wrapper: VueWrapper

async function open(path: string) {
  router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/inquiries', component: { template: '<div>history</div>' } },
    { path: '/inquiries/new', component: InquiryNew },
    { path: '/inquiries/:inquiryId', component: InquiryDetail },
  ] })
  await router.push(path)
  await router.isReady()
  wrapper = mount(RouterView, { global: {
    plugins: [pinia, router, [VueQueryPlugin, { queryClient }]],
    stubs: {
      InquiryImageUploader: UploaderStub, InquiryTimeline: true,
      PageHeader: { template: '<header><slot name="actions" /></header>' },
    },
  } })
  await flushPromises()
}

function unloadBlocked() {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

beforeEach(() => {
  vi.clearAllMocks()
  calls.confirm.mockReset().mockResolvedValue(false)
  calls.create.mockReset().mockResolvedValue(response())
  calls.detail.mockReset().mockImplementation(async (id: number) => response(id))
  calls.message.mockReset().mockResolvedValue(response())
  pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore(pinia)
  auth.$patch({ sessionGeneration: 1, accessToken: 'test-token', user: {
    userId: 1, loginId: 'tester', displayName: 'Tester', email: 'tester@example.test',
    status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
  } })
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  queryClient = new QueryClient({ defaultOptions: {
    queries: { retry: false, gcTime: Infinity }, mutations: { retry: false, gcTime: 0 },
  } })
})
afterEach(() => {
  wrapper?.unmount()
  queryClient.clear()
  disposePinia(pinia)
  setActivePinia(undefined)
  configureAuthQueryScope(() => 0)
})

describe('inquiry draft navigation', () => {
  it('keeps the new inquiry and attachments when cancel navigation is rejected', async () => {
    await open('/inquiries/new')
    await wrapper.get('input').setValue('Private title')
    await wrapper.get('textarea').setValue('Private body')
    wrapper.getComponent(UploaderStub).vm.$emit('update:modelValue', [11])
    await wrapper.get('form button[type="button"]').trigger('click')
    await flushPromises()
    expect(calls.confirm).toHaveBeenCalledWith('inquiry.common.leaveConfirm')
    expect(router.currentRoute.value.path).toBe('/inquiries/new')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('Private body')
    expect(calls.discard).not.toHaveBeenCalled()

    calls.confirm.mockResolvedValueOnce(true)
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe('/inquiries')
    expect(calls.discard).toHaveBeenCalledOnce()
    expect(calls.create).not.toHaveBeenCalled()
  })

  it.each(['/inquiries/new', '/inquiries/41'])('protects refresh and PWA reload while %s is dirty', async (path) => {
    await open(path)
    expect(unloadBlocked()).toBe(false)
    await wrapper.get('textarea').setValue('Unsent text')
    expect(unloadBlocked()).toBe(true)
    const reload = vi.fn()
    expect(whenPwaReloadSafe(reload)).toBe(false)
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe(path)
    expect(reload).not.toHaveBeenCalled()
    calls.confirm.mockResolvedValueOnce(true)
    await router.push('/inquiries')
    expect(reload).toHaveBeenCalledOnce()
    expect(unloadBlocked()).toBe(false)
  })

  it('protects a reply when the same detail component changes inquiry IDs', async () => {
    await open('/inquiries/41')
    await wrapper.get('textarea').setValue('Reply for 41')
    await router.push('/inquiries/41?view=thread')
    expect(calls.confirm).not.toHaveBeenCalled()
    await router.push('/inquiries/42')
    expect(router.currentRoute.value.path).toBe('/inquiries/41')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('Reply for 41')
    expect(calls.discard).not.toHaveBeenCalled()
    calls.confirm.mockResolvedValueOnce(true)
    await router.push('/inquiries/42')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/inquiries/42')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('blocks leaving during creation and navigates without a prompt after success', async () => {
    const pending = createDeferred<ReturnType<typeof response>>()
    calls.create.mockReturnValueOnce(pending.promise)
    await open('/inquiries/new')
    await wrapper.get('input').setValue('Title')
    await wrapper.get('textarea').setValue('Body')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe('/inquiries/new')
    expect(calls.confirm).not.toHaveBeenCalled()
    expect(unloadBlocked()).toBe(true)
    pending.resolve(response())
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/inquiries/41')
    expect(calls.confirm).not.toHaveBeenCalled()
    expect(calls.create).toHaveBeenCalledOnce()
    expect(unloadBlocked()).toBe(false)
  })

  it('blocks route updates during reply submission and clears protection after success', async () => {
    const pending = createDeferred<ReturnType<typeof response>>()
    calls.message.mockReturnValueOnce(pending.promise)
    await open('/inquiries/41')
    await wrapper.get('textarea').setValue('Reply')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await router.push('/inquiries/42')
    expect(router.currentRoute.value.path).toBe('/inquiries/41')
    expect(calls.confirm).not.toHaveBeenCalled()
    pending.resolve(response())
    await flushPromises()
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe('/inquiries')
    expect(calls.confirm).not.toHaveBeenCalled()
  })

  it('protects attachment-only drafts and in-flight uploads', async () => {
    await open('/inquiries/new')
    wrapper.getComponent(UploaderStub).vm.$emit('uploading', true)
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe('/inquiries/new')
    expect(calls.confirm).not.toHaveBeenCalled()
    wrapper.getComponent(UploaderStub).vm.$emit('uploading', false)
    wrapper.getComponent(UploaderStub).vm.$emit('update:modelValue', [11])
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe('/inquiries/new')
    expect(calls.confirm).toHaveBeenCalledOnce()
  })

  it('allows leaving after a saved reply while its background refresh is still pending', async () => {
    await open('/inquiries/41')
    const refresh = createDeferred<ReturnType<typeof response>>()
    calls.detail.mockReturnValueOnce(refresh.promise)
    await wrapper.get('textarea').setValue('Saved reply')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(calls.commit).toHaveBeenCalledOnce()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
    await router.push('/inquiries')
    expect(router.currentRoute.value.path).toBe('/inquiries')
    expect(calls.confirm).not.toHaveBeenCalled()
    refresh.resolve(response())
    await flushPromises()
  })
})

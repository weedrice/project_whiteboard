import { failAuthScopedMutation } from '@/test/authScopedMutationOptions'
import { useAuthStore as getMutationTestAuthStore } from '@/stores/auth'
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import InquiryNew from '../InquiryNew.vue'
import { configureAuthQueryScope } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'

const { state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    state: {
      commitUploads: vi.fn(),
      discardUploads: vi.fn().mockResolvedValue(undefined),
      beginSubmission: vi.fn(() => true),
      failSubmission: vi.fn().mockResolvedValue(undefined),
      invalidateQueries: vi.fn().mockResolvedValue(undefined),
      mutation: { mutate: vi.fn(), isPending: refOf(false) },
      mutationOptions: null as null | {
        onSuccess?: (response: unknown, variables: Record<string, unknown>) => unknown
        onError?: (error: unknown, variables: Record<string, unknown>) => unknown
      },
      routerPush: vi.fn().mockResolvedValue(undefined),
      routerReplace: vi.fn().mockResolvedValue(undefined),
      sessionGeneration: 0,
    },
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({ push: state.routerPush, replace: state.routerReplace }),
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/vue-query')>(),
  useMutation: (options: typeof state.mutationOptions) => {
    state.mutationOptions = options
    return { ...state.mutation, mutateAsync: async (...args: unknown[]) => state.mutation.mutate(...args) }
  },
  useQueryClient: () => ({ invalidateQueries: state.invalidateQueries }),
}))

vi.mock('@/api/inquiry', () => ({
  inquiryApi: { create: vi.fn() },
}))

const InquiryImageUploaderStub = defineComponent({
  props: { disabled: Boolean },
  emits: ['uploading'],
  setup(_, { expose }) {
    expose({
      commitUploads: state.commitUploads,
      discardUploads: state.discardUploads,
      beginSubmission: state.beginSubmission,
      failSubmission: state.failSubmission,
    })
    return () => h('div', { 'data-test': 'uploader' })
  },
})

function mountView() {
  return mount(InquiryNew, {
    global: {
      stubs: {
        InquiryImageUploader: InquiryImageUploaderStub,
        BaseButton: { props: ['disabled', 'to'], template: '<button :disabled="disabled" :data-to="to"><slot /></button>' },
        PageHeader: { template: '<header><slot name="actions" /></header>' },
      },
    },
  })
}

describe('InquiryNew', () => {
  beforeEach(() => {
    getMutationTestAuthStore().sessionGeneration = 0
    state.commitUploads.mockReset()
    state.discardUploads.mockClear()
    state.beginSubmission.mockClear()
    state.failSubmission.mockClear()
    state.invalidateQueries.mockClear()
    state.routerPush.mockClear()
    state.routerReplace.mockClear()
    state.mutation.mutate.mockClear()
    state.mutation.isPending.value = false
    state.mutationOptions = null
    state.sessionGeneration = 0
    configureAuthQueryScope(() => state.sessionGeneration)
  })

  it('links to the inquiry history from the creation page', () => {
    const wrapper = mountView()

    expect(wrapper.get('[data-to="/inquiries"]').text()).toBe('inquiry.list.title')
  })

  it('invalidates the auth-scoped inquiry list before navigating after creation', async () => {
    mountView()

    const uploader = { commitUploads: state.commitUploads }
    await state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 42 } },
    }, { generation: 0, uploader })
    await flushPromises()

    expect(state.commitUploads).toHaveBeenCalledOnce()
    expect(state.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 0, 'inquiries', 'mine'],
    })
    expect(state.routerReplace).toHaveBeenCalledWith('/inquiries/42')
  })

  it('commits the server result without navigating a replacement login session', async () => {
    mountView()
    state.sessionGeneration = 1

    await state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 42 } },
    }, { generation: 0, uploader: { commitUploads: state.commitUploads } })

    expect(state.commitUploads).toHaveBeenCalledOnce()
    expect(state.invalidateQueries).not.toHaveBeenCalled()
    expect(state.routerReplace).not.toHaveBeenCalled()
  })

  it('discards temporary uploads when creation fails', async () => {
    mountView()

    await failAuthScopedMutation(state.mutationOptions!, new Error('failed'), { uploader: { failSubmission: state.failSubmission } })
    await flushPromises()

    expect(state.failSubmission).toHaveBeenCalledOnce()
    expect(state.routerReplace).not.toHaveBeenCalled()
  })

  it('does not navigate to the old inquiry after switching accounts during cache refresh', async () => {
    mountView()
    const refresh = createDeferred<void>()
    state.invalidateQueries.mockReturnValueOnce(refresh.promise)
    const pending = state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 42 } },
    }, { generation: 0, uploader: { commitUploads: state.commitUploads } })
    await flushPromises()
    expect(state.invalidateQueries).toHaveBeenCalledOnce()
    state.sessionGeneration = 1
    refresh.resolve()
    await pending
    expect(state.routerReplace).not.toHaveBeenCalled()
  })

  it('does not submit while an image upload is in flight', async () => {
    const wrapper = mountView()
    wrapper.findComponent(InquiryImageUploaderStub).vm.$emit('uploading', true)
    await wrapper.vm.$nextTick()

    await wrapper.get('form').trigger('submit')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('inquiry.upload.uploading')
  })

  it('clears private fields when the same component survives a new session', async () => {
    const wrapper = mountView()
    const vm = wrapper.vm as unknown as { title: string; content: string; fileIds: number[]; createdInquiryId: number | null }
    vm.title = 'private title'
    vm.content = 'private body'
    vm.fileIds = [22]
    vm.createdInquiryId = 77
    getMutationTestAuthStore().sessionGeneration += 1
    await wrapper.vm.$nextTick()
    expect(vm.title).toBe('')
    expect(vm.content).toBe('')
    expect(vm.fileIds).toEqual([])
    expect(vm.createdInquiryId).toBeNull()
    expect(wrapper.get('input').attributes('disabled')).toBeUndefined()
  })

  it('locks the entire form while creation is pending', async () => {
    const wrapper = mountView()
    void (wrapper.vm as unknown as { operations: { run: (key: string, action: () => Promise<void>) => Promise<unknown> } }).operations.run('create', () => new Promise(() => {}))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('select').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.getComponent(InquiryImageUploaderStub).props('disabled')).toBe(true)

    await wrapper.get('form').trigger('submit')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
  })

  it('keeps a recovery link and prevents duplicate submission when navigation fails after creation', async () => {
    state.routerReplace.mockRejectedValueOnce(new Error('navigation failed'))
    const wrapper = mountView()
    const uploader = { commitUploads: state.commitUploads }

    await state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 77 } },
    }, { generation: 0, uploader })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('inquiry.form.createdNavigationFailed')
    expect(wrapper.text()).toContain('inquiry.form.openCreated')

    await wrapper.get('form').trigger('submit')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
  })
})

vi.mock('@/composables/useAuthScopedMutation', async () => {
  const { useMutation } = await import('@tanstack/vue-query')
  const { authScopedMutationOptions } = await import('@/test/authScopedMutationOptions')
  const { captureAuthSessionIntent, isAuthSessionIntentCurrent } = await import('@/utils/authSessionIntent')
  const context = () => {
    const store = getMutationTestAuthStore()
    const intent = captureAuthSessionIntent(store)
    return { isCurrent: () => isAuthSessionIntentCurrent(store, intent) }
  }
  return {
    useAuthScopedMutation: (options: Record<string, any>) => useMutation({
      ...authScopedMutationOptions(options, getMutationTestAuthStore),
      onSuccess: (response: unknown, variables: Record<string, unknown>) => options.onSuccess?.(response, variables, undefined, context()),
      onError: (error: unknown, variables: Record<string, unknown>) => options.onError?.(error, variables, undefined, context()),
    }),
  }
})

vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  const auth = reactive({ sessionGeneration: 0 })
  return { useAuthStore: () => auth }
})

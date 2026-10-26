import { failAuthScopedMutation } from '@/test/authScopedMutationOptions'
import { useAuthStore as getMutationTestAuthStore } from '@/stores/auth'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryDetail from '../InquiryDetail.vue'
import { createDeferred } from '@/test/async'

const { state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    state: {
      route: null as unknown as { params: { inquiryId: string } },
      uploaderInstances: [] as Array<{
        discardUploads: ReturnType<typeof vi.fn>
        commitUploads: ReturnType<typeof vi.fn>
        beginSubmission: ReturnType<typeof vi.fn>
        failSubmission: ReturnType<typeof vi.fn>
      }>,
      mutationOptions: [] as Array<{
        onSuccess?: (response: unknown, variables: Record<string, unknown>) => unknown
        onError?: (error: unknown, variables: Record<string, unknown>) => unknown
      }>,
      mutations: [
        { mutate: vi.fn(), isPending: refOf(false) },
        { mutate: vi.fn(), isPending: refOf(false) },
      ],
      invalidateQueries: vi.fn().mockResolvedValue(undefined),
      setQueryData: vi.fn(),
      confirm: vi.fn(),
      refOf,
    },
  }
})

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', async (importOriginal) => {
  const { reactive } = await import('vue')
  state.route = reactive({ params: { inquiryId: '41' } })
  return {
    ...await importOriginal<typeof import('vue-router')>(),
    useRoute: () => state.route,
    useRouter: () => ({ push: vi.fn() }),
  }
})
vi.mock('@/api/inquiry', () => ({ inquiryApi: { addMessage: vi.fn(), withdraw: vi.fn(), close: vi.fn(), getMineDetail: vi.fn() } }))
vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: () => ({
    data: state.refOf({
      inquiryId: 41,
      title: 'Question',
      status: 'NEW',
      category: 'OTHER',
      closureReason: null,
      allowedActions: { canAddMessage: true, canWithdraw: true, canClose: false },
      messages: [],
    }),
    isLoading: state.refOf(false),
    error: state.refOf(null),
  }),
}))
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: state.confirm }),
}))
vi.mock('@tanstack/vue-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/vue-query')>(),
  useMutation: (options: typeof state.mutationOptions[number]) => {
    state.mutationOptions.push(options)
    const mutation = state.mutations[state.mutationOptions.length - 1]!
    return { ...mutation, mutateAsync: async (...args: unknown[]) => mutation.mutate(...args) }
  },
  useQueryClient: () => ({ invalidateQueries: state.invalidateQueries, setQueryData: state.setQueryData }),
}))

const UploaderStub = defineComponent({
  props: { disabled: Boolean },
  setup(_, { expose }) {
    const instance = {
      discardUploads: vi.fn().mockResolvedValue(undefined),
      commitUploads: vi.fn(),
      beginSubmission: vi.fn(() => true),
      failSubmission: vi.fn().mockResolvedValue(undefined),
    }
    state.uploaderInstances.push(instance)
    expose(instance)
    return () => h('div')
  },
})

const wrappers: VueWrapper[] = []
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

function mountView() {
  const wrapper = mount(InquiryDetail, {
    global: {
      stubs: {
        InquiryImageUploader: UploaderStub,
        InquiryTimeline: true,
        BaseButton: { template: '<button><slot /></button>' },
        PageHeader: { template: '<div><slot name="actions" /></div>' },
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('InquiryDetail', () => {
  beforeEach(() => {
    getMutationTestAuthStore().sessionGeneration = 0
    state.route.params.inquiryId = '41'
    state.uploaderInstances.length = 0
    state.mutationOptions.length = 0
    state.mutations.forEach((mutation) => {
      mutation.mutate.mockClear()
      mutation.isPending.value = false
    })
    state.invalidateQueries.mockClear()
    state.setQueryData.mockClear()
    state.confirm.mockReset().mockResolvedValue(true)
  })

  it('discards temporary uploads when adding a message fails', async () => {
    const wrapper = mountView()
    const activeUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader

    await failAuthScopedMutation(state.mutationOptions[0], new Error('failed'), {
      inquiryId: 41,
      draftEpoch: 0,
      generation: 0,
      uploader: activeUploader,
    })
    await flushPromises()

    expect(activeUploader.failSubmission).toHaveBeenCalledOnce()
  })

  it('clears and discards the previous inquiry draft when the route parameter changes', async () => {
    const wrapper = mountView()
    const previousUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader
    await wrapper.get('textarea').setValue('draft for inquiry 41')

    state.route.params.inquiryId = '42'
    await nextTick()

    expect(previousUploader.discardUploads).toHaveBeenCalledOnce()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('keeps an old response scoped to its inquiry after navigating away', async () => {
    const wrapper = mountView()
    const previousUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader
    const variables = {
      inquiryId: 41,
      draftEpoch: 0,
      generation: 0,
      uploader: previousUploader,
    }

    state.route.params.inquiryId = '42'
    await nextTick()
    ;(wrapper.vm as unknown as { fileIds: number[] }).fileIds = [202]
    await state.mutationOptions[0]?.onSuccess?.({ data: { success: true, data: { inquiryId: 41 } } }, variables)

    expect(state.setQueryData).toHaveBeenCalledWith(
      ['session', 0, 'inquiries', 'detail', 41],
      { inquiryId: 41 },
    )
    expect(previousUploader.commitUploads).toHaveBeenCalledOnce()
    expect((wrapper.vm as unknown as { fileIds: number[] }).fileIds).toEqual([202])
  })

  it('does not let an old failed submission clear the next inquiry draft', async () => {
    const wrapper = mountView()
    const previousUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader
    const variables = {
      inquiryId: 41,
      draftEpoch: 0,
      generation: 0,
      uploader: previousUploader,
    }

    state.route.params.inquiryId = '42'
    await nextTick()
    ;(wrapper.vm as unknown as { fileIds: number[] }).fileIds = [203]
    await failAuthScopedMutation(state.mutationOptions[0], new Error('late failure'), variables)

    expect(previousUploader.failSubmission).toHaveBeenCalledOnce()
    expect((wrapper.vm as unknown as { fileIds: number[] }).fileIds).toEqual([203])
  })

  it('blocks message composition and submission while a close or withdraw action is pending', async () => {
    const wrapper = mountView()
    void (wrapper.vm as unknown as { operations: { run: (key: string, action: () => Promise<void>) => Promise<unknown> } }).operations.run('action', () => new Promise(() => {}))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.getComponent(UploaderStub).props('disabled')).toBe(true)

    await wrapper.get('form').trigger('submit')

    expect(state.mutations[0]!.mutate).not.toHaveBeenCalled()
  })

  it('cancels a pending confirmation and clears private draft values at a session boundary', async () => {
    const pending = createDeferred<boolean>()
    state.confirm.mockReturnValueOnce(pending.promise)
    const wrapper = mountView()
    const vm = wrapper.vm as unknown as { runAction: (action: 'withdraw') => Promise<void>; content: string; fileIds: number[] }
    vm.content = 'private draft'
    vm.fileIds = [20]
    const action = vm.runAction('withdraw')
    getMutationTestAuthStore().sessionGeneration += 1
    pending.resolve(true)
    await action
    expect(state.mutations[1]!.mutate).not.toHaveBeenCalled()
    expect(vm.content).toBe('')
    expect(vm.fileIds).toEqual([])
  })

  it('uses the shared confirm modal before withdrawing an inquiry', async () => {
    state.confirm.mockResolvedValueOnce(false)
    const wrapper = mountView()
    const runAction = (wrapper.vm as unknown as {
      runAction: (action: 'withdraw' | 'close') => Promise<void>
    }).runAction

    await runAction('withdraw')

    expect(state.confirm).toHaveBeenCalledWith('inquiry.detail.withdrawConfirm')
    expect(state.mutations[1]!.mutate).not.toHaveBeenCalled()

    state.confirm.mockResolvedValueOnce(true)
    await runAction('withdraw')

    expect(state.mutations[1]!.mutate).toHaveBeenCalledWith(expect.objectContaining({
      action: 'withdraw',
      inquiryId: 41,
    }))
  })

  it('ignores a confirmation resolved after navigating to another inquiry', async () => {
    const pendingConfirm = createDeferred<boolean>()
    state.confirm.mockReturnValueOnce(pendingConfirm.promise)
    const wrapper = mountView()
    const runAction = (wrapper.vm as unknown as {
      runAction: (action: 'withdraw' | 'close') => Promise<void>
    }).runAction

    const action = runAction('withdraw')
    state.route.params.inquiryId = '42'
    await nextTick()
    pendingConfirm.resolve(true)
    await action

    expect(state.mutations[1]!.mutate).not.toHaveBeenCalled()
  })

  it('renders localized status and category labels instead of raw enum values', () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('inquiry.status.NEW')
    expect(wrapper.text()).toContain('inquiry.category.OTHER')
  })
})

vi.mock('@/composables/useAuthScopedMutation', async () => {
    const { useMutation } = await import('@tanstack/vue-query')
    const { authScopedMutationOptions } = await import('@/test/authScopedMutationOptions')
    return { useAuthScopedMutation: (options: Record<string, any>) => useMutation(authScopedMutationOptions(options, getMutationTestAuthStore)) }
})

vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  const auth = reactive({ sessionGeneration: 0 })
  return { useAuthStore: () => auth }
})

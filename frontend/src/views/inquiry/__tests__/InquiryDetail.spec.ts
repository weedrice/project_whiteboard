import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryDetail from '../InquiryDetail.vue'

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
vi.mock('@tanstack/vue-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/vue-query')>(),
  useMutation: (options: typeof state.mutationOptions[number]) => {
    state.mutationOptions.push(options)
    return state.mutations[state.mutationOptions.length - 1]!
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

function mountView() {
  return mount(InquiryDetail, {
    global: {
      stubs: {
        InquiryImageUploader: UploaderStub,
        InquiryTimeline: true,
        BaseButton: { template: '<button><slot /></button>' },
        PageHeader: { template: '<div><slot name="actions" /></div>' },
      },
    },
  })
}

describe('InquiryDetail', () => {
  beforeEach(() => {
    state.route.params.inquiryId = '41'
    state.uploaderInstances.length = 0
    state.mutationOptions.length = 0
    state.mutations.forEach((mutation) => {
      mutation.mutate.mockClear()
      mutation.isPending.value = false
    })
    state.invalidateQueries.mockClear()
    state.setQueryData.mockClear()
  })

  it('discards temporary uploads when adding a message fails', async () => {
    const wrapper = mountView()
    const activeUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader

    await state.mutationOptions[0]?.onError?.(new Error('failed'), {
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
    await state.mutationOptions[0]?.onError?.(new Error('late failure'), variables)

    expect(previousUploader.failSubmission).toHaveBeenCalledOnce()
    expect((wrapper.vm as unknown as { fileIds: number[] }).fileIds).toEqual([203])
  })

  it('blocks message composition and submission while a close or withdraw action is pending', async () => {
    state.mutations[1]!.isPending.value = true
    const wrapper = mountView()

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.getComponent(UploaderStub).props('disabled')).toBe(true)

    await wrapper.get('form').trigger('submit')

    expect(state.mutations[0]!.mutate).not.toHaveBeenCalled()
  })

  it('renders localized status and category labels instead of raw enum values', () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('inquiry.status.NEW')
    expect(wrapper.text()).toContain('inquiry.category.OTHER')
  })
})

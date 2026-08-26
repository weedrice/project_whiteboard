import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminInquiryPosts from '../AdminInquiryPosts.vue'
import AdminDetailModalShell from '@/components/admin/AdminDetailModalShell.vue'

const { state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    state: {
      refOf,
      uploaderInstances: [] as Array<{
        discardUploads: ReturnType<typeof vi.fn>
        commitUploads: ReturnType<typeof vi.fn>
        beginSubmission: ReturnType<typeof vi.fn>
        failSubmission: ReturnType<typeof vi.fn>
      }>,
      mutationOptions: null as null | {
        onSuccess?: (response: unknown, variables: Record<string, unknown>) => unknown
        onError?: (error: unknown, variables: Record<string, unknown>) => unknown
      },
      mutation: { mutate: vi.fn(), isPending: refOf(false) },
      invalidateQueries: vi.fn().mockResolvedValue(undefined),
      setQueryData: vi.fn(),
      routerPush: vi.fn().mockResolvedValue(undefined),
      route: null as unknown as { params: { inquiryId?: string } },
      detailQueryOptions: null as null | { queryKey: { value: readonly unknown[] } },
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
    useRouter: () => ({ push: state.routerPush }),
  }
})
vi.mock('@tanstack/vue-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/vue-query')>(),
  useMutation: (options: typeof state.mutationOptions) => {
    state.mutationOptions = options
    return state.mutation
  },
  useQueryClient: () => ({ invalidateQueries: state.invalidateQueries, setQueryData: state.setQueryData }),
}))
vi.mock('@/composables/useApiQuery', () => ({
  useApiPageQuery: () => ({
    data: state.refOf({ content: [], totalPages: 0 }),
    isLoading: state.refOf(false),
    error: state.refOf(null),
    refetch: vi.fn(),
  }),
  useApiQuery: (options: typeof state.detailQueryOptions) => {
    state.detailQueryOptions = options
    return {
      data: state.refOf({
        inquiryId: 41,
        title: 'Question',
        authorName: 'Author',
        status: 'NEW',
        effectivePriority: 'NORMAL',
        messages: [],
        closureDetail: null,
      }),
      isLoading: state.refOf(false),
      isFetching: state.refOf(false),
      error: state.refOf(null),
    }
  },
}))
vi.mock('@/features/admin/inquiries/useAdminInquiryPosts', () => ({
  useAdminInquiryPosts: () => ({
    posts: state.refOf([]), page: state.refOf(0), totalPages: state.refOf(0), totalElements: state.refOf(0),
    isLoading: state.refOf(false), selectedPostId: state.refOf(null), selectedInquiry: state.refOf(null),
    isDetailLoading: state.refOf(false), isDetailFetching: state.refOf(false), detailError: state.refOf(null),
    openDetail: vi.fn(), closeDetail: vi.fn(), handlePageChange: vi.fn(),
  }),
}))
vi.mock('@/api/inquiry', () => ({ inquiryApi: {} }))

const UploaderStub = defineComponent({
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

describe('AdminInquiryPosts upload cleanup', () => {
  beforeEach(() => {
    state.uploaderInstances.length = 0
    state.mutationOptions = null
    state.routerPush.mockReset()
    state.routerPush.mockImplementation(async (destination: string) => {
      if (destination === '/admin/inquiries') delete state.route.params.inquiryId
    })
    state.setQueryData.mockClear()
    state.mutation.mutate.mockClear()
    state.mutation.isPending.value = false
    state.detailQueryOptions = null
    state.route.params.inquiryId = '41'
  })

  it('discards temporary uploads when a reply request fails', async () => {
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })

    expect(wrapper.findComponent(AdminDetailModalShell).exists()).toBe(true)

    const activeUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader
    await state.mutationOptions?.onError?.(new Error('failed'), {
      action: 'reply',
      inquiryId: 41,
      composeEpoch: 0,
      generation: 0,
      uploader: activeUploader,
    })
    await flushPromises()

    expect(activeUploader.failSubmission).toHaveBeenCalledOnce()
  })

  it('discards and clears the draft before switching to another inquiry', async () => {
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    const textarea = wrapper.get('textarea')
    await textarea.setValue('draft for inquiry 41')

    state.route.params.inquiryId = '42'
    await nextTick()
    await flushPromises()
    await nextTick()

    expect(state.uploaderInstances.map((instance) => instance.discardUploads.mock.calls.length)).toContain(1)
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('discards and clears the draft before closing the detail', async () => {
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    const textarea = wrapper.get('textarea')
    await textarea.setValue('draft to discard')
    const closeButton = wrapper.findAll('button').find((button) => button.text() === 'inquiry.common.close')

    await closeButton!.trigger('click')
    await flushPromises()
    await nextTick()

    expect(state.uploaderInstances.map((instance) => instance.discardUploads.mock.calls.length)).toContain(1)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(state.routerPush).toHaveBeenCalledWith('/admin/inquiries')
  })

  it('keeps the route-driven detail open when navigation is rejected', async () => {
    state.routerPush.mockImplementationOnce(async () => ({ type: 'aborted' }))
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    await wrapper.get('textarea').setValue('draft remains')
    const closeButton = wrapper.findAll('button').find((button) => button.text() === 'inquiry.common.close')

    await closeButton!.trigger('click')
    await flushPromises()

    expect(state.route.params.inquiryId).toBe('41')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('draft remains')
    expect(state.uploaderInstances.every((instance) => instance.discardUploads.mock.calls.length === 0)).toBe(true)
  })

  it('keeps an old action response scoped to the inquiry that started it', async () => {
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    const previousUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader
    const variables = {
      action: 'reply',
      inquiryId: 41,
      composeEpoch: 0,
      generation: 0,
      uploader: previousUploader,
    }

    state.route.params.inquiryId = '42'
    await nextTick()
    ;(wrapper.vm as unknown as { fileIds: number[] }).fileIds = [302]
    await state.mutationOptions?.onSuccess?.(
      { data: { success: true, data: { inquiryId: 41 } } },
      variables,
    )

    expect(state.setQueryData).toHaveBeenCalledWith(
      ['session', 0, 'admin', 'support-inquiries', 'detail', 41],
      { inquiryId: 41 },
    )
    expect(previousUploader.commitUploads).toHaveBeenCalledOnce()
    expect((wrapper.vm as unknown as { fileIds: number[] }).fileIds).toEqual([302])
  })

  it('does not let an old failed action clear the next inquiry draft', async () => {
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    const previousUploader = (wrapper.vm as unknown as {
      uploader: typeof state.uploaderInstances[number]
    }).uploader

    state.route.params.inquiryId = '42'
    await nextTick()
    ;(wrapper.vm as unknown as { fileIds: number[] }).fileIds = [303]
    await state.mutationOptions?.onError?.(new Error('late failure'), {
      action: 'reply',
      inquiryId: 41,
      composeEpoch: 0,
      generation: 0,
      uploader: previousUploader,
    })

    expect(previousUploader.failSubmission).toHaveBeenCalledOnce()
    expect((wrapper.vm as unknown as { fileIds: number[] }).fileIds).toEqual([303])
  })

  it('does not let an older asynchronous cleanup restore a stale selected inquiry', async () => {
    let resolveFirstDiscard!: () => void
    const firstDiscard = new Promise<void>((resolve) => { resolveFirstDiscard = resolve })
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    state.uploaderInstances.forEach((instance) => {
      instance.discardUploads.mockReturnValue(firstDiscard)
    })

    state.route.params.inquiryId = '42'
    await nextTick()
    state.route.params.inquiryId = '43'
    await nextTick()
    expect(state.detailQueryOptions?.queryKey.value.at(-1)).toBe(43)

    resolveFirstDiscard()
    await flushPromises()

    expect(state.detailQueryOptions?.queryKey.value.at(-1)).toBe(43)
  })

  it('blocks duplicate administrator commands at the function boundary', async () => {
    state.mutation.isPending.value = true
    const wrapper = mount(AdminInquiryPosts, {
      global: {
        stubs: {
          Teleport: true,
          InquiryImageUploader: UploaderStub,
          InquiryTimeline: true,
          AdminDataPage: { template: '<div><slot /></div>' },
          AdminPaginatedTable: true,
          AdminInquiryDetailModal: true,
          Pagination: true,
          BaseButton: { template: '<button><slot /></button>' },
        },
      },
    })
    const startButton = wrapper.findAll('button').find((button) => button.text() === 'inquiry.admin.start')

    await startButton!.trigger('click')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
  })
})

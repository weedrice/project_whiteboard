import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import AdminInquiryPosts from '../AdminInquiryPosts.vue'

const { identityT, state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    identityT: (key: string, params?: Record<string, unknown>) => (
      params?.count === undefined ? key : `${key}:${params.count}`
    ),
    state: {
      routerPush: vi.fn(),
      confirmWithReason: vi.fn(),
      route: { params: {} as { inquiryId?: string } },
      pageQueryOptions: null as null | { queryKey: { value: readonly unknown[] } },
      mutation: {
        mutate: vi.fn(),
        isPending: refOf(false),
      },
      listQuery: {
        data: refOf({
          content: [{
            inquiryId: 41,
            title: 'Account recovery',
            authorName: 'Ada',
            status: 'NEW',
            effectivePriority: 'HIGH',
            staffActionSince: '2026-08-25T01:00:00Z',
          }],
          totalPages: 2,
          totalElements: 1,
          page: 0,
          size: 20,
        }),
        isLoading: refOf(false),
        isFetching: refOf(false),
        error: refOf(null as unknown),
        refetch: vi.fn(),
      },
      detailQuery: {
        data: refOf(null as unknown),
        isLoading: refOf(false),
        isFetching: refOf(false),
        error: refOf(null as unknown),
      },
      legacy: {
        closeDetail: vi.fn(),
        detailError: refOf(null as unknown),
        error: refOf(null as unknown),
        handlePageChange: vi.fn(),
        isDetailFetching: refOf(false),
        isDetailLoading: refOf(false),
        isFetching: refOf(false),
        isLoading: refOf(false),
        openDetail: vi.fn(),
        page: refOf(0),
        posts: refOf([{
          id: 7,
          title: 'Legacy inquiry',
          summaryText: 'Archived question',
          authorName: 'Grace',
          createdAtText: '2026-05-26',
        }]),
        selectedInquiry: refOf(null as unknown),
        selectedPostId: refOf(null as number | null),
        sort: refOf('createdAt,desc'),
        totalElements: refOf(1),
        totalPages: refOf(1),
      },
    },
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: identityT }),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => state.route,
  useRouter: () => ({ push: state.routerPush }),
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/vue-query')>(),
  useMutation: () => state.mutation,
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiPageQuery: (options: { queryKey: { value: readonly unknown[] } }) => {
    state.pageQueryOptions = options
    return state.listQuery
  },
  useApiQuery: () => state.detailQuery,
}))

vi.mock('@/features/admin/inquiries/useAdminInquiryPosts', () => ({
  useAdminInquiryPosts: () => state.legacy,
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirmWithReason: state.confirmWithReason }),
}))

const AdminPaginatedTableStub = defineComponent({
  props: {
    caption: { type: String, default: '' },
    items: { type: Array, default: () => [] },
  },
  emits: ['row-click'],
  template: `
    <button
      type="button"
      :data-test="caption === 'inquiry.admin.newTab' ? 'new-row' : 'legacy-row'"
      @click="$emit('row-click', items[0])"
    >
      {{ items[0]?.title }}
    </button>
  `,
})

const InquiryImageUploaderStub = defineComponent({
  setup(_, { expose }) {
    expose({
      beginSubmission: () => true,
      commitUploads: vi.fn(),
      discardUploads: vi.fn(),
      failSubmission: vi.fn(),
    })
    return () => h('div', { 'data-test': 'inquiry-uploader' })
  },
})

function mountView(options: { realTable?: boolean } = {}) {
  return mount(AdminInquiryPosts, {
    global: {
      stubs: {
        AdminDataPage: { template: '<main><slot /></main>' },
        ...(!options.realTable ? { AdminPaginatedTable: AdminPaginatedTableStub } : {}),
        AdminInquiryDetailModal: true,
        InquiryTimeline: true,
        InquiryImageUploader: InquiryImageUploaderStub,
        BaseButton: { template: '<button><slot /></button>' },
        Pagination: true,
        Teleport: true,
      },
    },
  })
}

describe('AdminInquiryPosts', () => {
  beforeEach(() => {
    state.routerPush.mockReset()
    state.legacy.openDetail.mockReset()
    state.route.params = {}
    state.detailQuery.data.value = null
    state.detailQuery.error.value = null
    state.detailQuery.isLoading.value = false
    state.pageQueryOptions = null
    state.confirmWithReason.mockReset().mockResolvedValue('resolved reason')
    state.mutation.mutate.mockReset()
    state.mutation.isPending.value = false
  })

  it('renders independent inquiries and opens their dedicated admin route', async () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('Account recovery')
    expect(wrapper.text()).toContain('inquiry.priority.HIGH')
    expect(wrapper.text()).toContain('inquiry.status.NEW')
    expect(wrapper.text()).not.toContain('Legacy inquiry')

    await wrapper.get('[data-test="new-row"]').trigger('click')

    expect(state.routerPush).toHaveBeenCalledWith('/admin/inquiries/41')
  })

  it('uses the shared interactive table contract for opening an inquiry', async () => {
    const wrapper = mountView()
    const openButton = wrapper.get('[data-test="new-row"]')

    expect(openButton.text()).toBe('Account recovery')
    expect(openButton.attributes('type')).toBe('button')
    await openButton.trigger('click')

    expect(state.routerPush).toHaveBeenCalledWith('/admin/inquiries/41')
  })

  it('opens an inquiry from the real shared table with the keyboard', async () => {
    const wrapper = mountView({ realTable: true })
    const row = wrapper.get('tbody tr[tabindex="0"]')

    expect(row.attributes('aria-keyshortcuts')).toBe('Enter Space')
    await row.trigger('keydown', { key: 'Enter' })

    expect(state.routerPush).toHaveBeenCalledWith('/admin/inquiries/41')
  })

  it('keeps legacy inquiries in a separate read-only archive tab', async () => {
    const wrapper = mountView()
    const tabs = wrapper.findAll('[role="tab"]')

    await tabs[1]!.trigger('click')

    expect(wrapper.text()).toContain('inquiry.admin.archiveNotice')
    expect(wrapper.text()).toContain('Legacy inquiry')
    await wrapper.get('[data-test="legacy-row"]').trigger('click')
    expect(state.legacy.openDetail).toHaveBeenCalledWith(7)
  })

  it('connects accessible tabs to their panels and supports arrow-key selection', async () => {
    const wrapper = mountView()
    let tabs = wrapper.findAll('[role="tab"]')

    expect(tabs.map((item) => item.attributes('aria-selected'))).toEqual(['true', 'false'])
    expect(tabs.map((item) => item.attributes('aria-controls'))).toEqual([
      'admin-new-inquiries-panel',
      'admin-legacy-inquiries-panel',
    ])
    expect(wrapper.get('[role="tabpanel"]').attributes('aria-labelledby')).toBe('admin-new-inquiries-tab')

    await tabs[0]!.trigger('keydown', { key: 'ArrowRight' })
    await nextTick()
    tabs = wrapper.findAll('[role="tab"]')

    expect(tabs.map((item) => item.attributes('aria-selected'))).toEqual(['false', 'true'])
    expect(wrapper.get('[role="tabpanel"]').attributes('aria-labelledby')).toBe('admin-legacy-inquiries-tab')
  })

  it('applies the keyword only when the search form is submitted', async () => {
    const wrapper = mountView()
    const searchInput = wrapper.get('input[maxlength="200"]')

    await searchInput.setValue('  account  ')
    await nextTick()

    expect((state.pageQueryOptions!.queryKey.value[2] as { keyword?: string }).keyword).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await nextTick()

    expect((state.pageQueryOptions!.queryKey.value[2] as { keyword?: string }).keyword).toBe('account')
  })

  it('uses the shared prompt modal before closing an inquiry', async () => {
    state.route.params = { inquiryId: '41' }
    state.confirmWithReason.mockResolvedValueOnce(null)
    const wrapper = mountView()
    const runAction = (wrapper.vm as unknown as {
      runAction: (action: 'close') => Promise<void>
    }).runAction

    await runAction('close')

    expect(state.confirmWithReason).toHaveBeenCalledWith('inquiry.admin.closePrompt')
    expect(state.mutation.mutate).not.toHaveBeenCalled()

    state.confirmWithReason.mockResolvedValueOnce('policy violation')
    await runAction('close')

    expect(state.mutation.mutate).toHaveBeenCalledWith(expect.objectContaining({
      action: 'close',
      inquiryId: 41,
      reason: 'policy violation',
    }))
  })

  it('submits the selected internal-note mode through the inquiry action contract', async () => {
    state.route.params = { inquiryId: '41' }
    state.detailQuery.data.value = {
      inquiryId: 41,
      title: 'Account recovery',
      authorName: 'Ada',
      status: 'IN_PROGRESS',
      effectivePriority: 'HIGH',
      messages: [],
      closureDetail: null,
    }
    const wrapper = mountView()
    const modes = wrapper.findAll('[role="radio"]')

    expect(modes.map((mode) => mode.attributes('aria-checked'))).toEqual(['true', 'false'])
    await modes[1]!.trigger('click')
    await wrapper.get('textarea').setValue('Only administrators can read this.')
    await wrapper.get('form.space-y-3').trigger('submit')

    expect(modes[1]!.attributes('aria-checked')).toBe('true')
    expect(state.mutation.mutate).toHaveBeenCalledWith(expect.objectContaining({
      action: 'note',
      inquiryId: 41,
      content: 'Only administrators can read this.',
      fileIds: [],
    }))
  })
})

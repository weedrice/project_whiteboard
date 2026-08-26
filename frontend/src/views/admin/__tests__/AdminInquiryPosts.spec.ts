import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import AdminInquiryPosts from '../AdminInquiryPosts.vue'

const { identityT, state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    identityT: (key: string, params?: Record<string, unknown>) => (
      params?.count === undefined ? key : `${key}:${params.count}`
    ),
    state: {
      routerPush: vi.fn(),
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

const AdminPaginatedTableStub = defineComponent({
  props: { items: { type: Array, default: () => [] } },
  emits: ['row-click'],
  template: '<button data-test="legacy-row" @click="$emit(\'row-click\', items[0])">{{ items[0]?.title }}</button>',
})

function mountView() {
  return mount(AdminInquiryPosts, {
    global: {
      stubs: {
        AdminDataPage: { template: '<main><slot /></main>' },
        AdminPaginatedTable: AdminPaginatedTableStub,
        AdminInquiryDetailModal: true,
        InquiryTimeline: true,
        InquiryImageUploader: true,
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
    state.pageQueryOptions = null
  })

  it('renders independent inquiries and opens their dedicated admin route', async () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('Account recovery')
    expect(wrapper.text()).toContain('inquiry.priority.HIGH')
    expect(wrapper.text()).toContain('inquiry.status.NEW')
    expect(wrapper.text()).not.toContain('Legacy inquiry')

    await wrapper.get('tbody tr').trigger('click')

    expect(state.routerPush).toHaveBeenCalledWith('/admin/inquiries/41')
  })

  it('provides a native keyboard-accessible control for opening an inquiry', async () => {
    const wrapper = mountView()
    const openButton = wrapper.get('tbody button[type="button"]')

    expect(openButton.text()).toBe('Account recovery')
    await openButton.trigger('click')

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
})

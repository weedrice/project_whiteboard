import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createPaginationStub } from '@/test/vue-test-helpers'
import AdminInquiryPosts from '../AdminInquiryPosts.vue'

const { identityT, state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  const identityT = (key: string, params?: Record<string, unknown>) => {
    if (params?.count !== undefined) {
      return `${key}:${params.count}`
    }
    return key
  }

  return {
    identityT,
    state: {
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
      posts: refOf([
        {
          id: 7,
          title: 'Need help',
          summaryText: 'Question summary',
          authorName: 'Ada',
          createdAtText: '2026-05-26',
          statusLabelKey: 'admin.inquiries.status.pending',
          statusVariant: 'warning',
        },
      ]),
      selectedInquiry: refOf({
        id: 7,
        title: 'Need help',
        authorName: 'Ada',
        createdAtText: '2026-05-26',
        contentsHtml: '<p>Question body</p>',
      }),
      selectedPostId: refOf(null as number | null),
      sort: refOf('createdAt,desc'),
      totalElements: refOf(1),
      totalPages: refOf(3),
    },
  }
})

vi.mock('@/composables/useAdminInquiryPosts', () => ({
  useAdminInquiryPosts: () => state,
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      t: identityT,
    },
    install: vi.fn(),
  }),
  useI18n: () => ({
    t: identityT,
  }),
}))

const PaginationStub = createPaginationStub()

const DetailModalStub = defineComponent({
  props: {
    isOpen: Boolean,
    inquiry: Object,
    loading: Boolean,
    fetching: Boolean,
    error: null,
  },
  emits: ['close'],
  template: '<button data-test="detail-modal" @click="$emit(\'close\')">{{ isOpen }} {{ inquiry?.title }}</button>',
})

function mountView() {
  return mount(AdminInquiryPosts, {
    global: {
      stubs: {
        AdminInquiryDetailModal: DetailModalStub,
        BaseSpinner: true,
        Pagination: PaginationStub,
      },
    },
  })
}

describe('AdminInquiryPosts', () => {
  beforeEach(() => {
    state.closeDetail = vi.fn()
    state.handlePageChange = vi.fn()
    state.openDetail = vi.fn()
    state.detailError.value = null
    state.error.value = null
    state.isDetailFetching.value = false
    state.isDetailLoading.value = false
    state.isFetching.value = false
    state.isLoading.value = false
    state.page.value = 0
    state.posts.value = [
      {
        id: 7,
        title: 'Need help',
        summaryText: 'Question summary',
        authorName: 'Ada',
        createdAtText: '2026-05-26',
        statusLabelKey: 'admin.inquiries.status.pending',
        statusVariant: 'warning',
      },
    ]
    state.selectedInquiry.value = {
      id: 7,
      title: 'Need help',
      authorName: 'Ada',
      createdAtText: '2026-05-26',
      contentsHtml: '<p>Question body</p>',
    }
    state.selectedPostId.value = null
    state.sort.value = 'createdAt,desc'
    state.totalElements.value = 1
    state.totalPages.value = 3
  })

  it('renders inquiry rows and forwards pagination events through the shared footer', async () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('Need help')
    expect(wrapper.text()).toContain('Question summary')
    expect(wrapper.text()).toContain('admin.inquiries.total:1')
    expect(wrapper.get('[data-test="pagination"]').text()).toContain('0/3')

    await wrapper.get('[data-test="pagination"]').trigger('click')

    expect(state.handlePageChange).toHaveBeenCalledWith(2)
  })

  it('opens inquiry detail from row clicks and closes the extracted detail modal', async () => {
    state.selectedPostId.value = 7
    const wrapper = mountView()

    await wrapper.get('tbody tr.cursor-pointer').trigger('click')
    expect(state.openDetail).toHaveBeenCalledWith(7)

    await wrapper.get('[data-test="detail-modal"]').trigger('click')
    expect(state.closeDetail).toHaveBeenCalledTimes(1)
  })

  it('shows the shared footer loading message while refreshing', () => {
    state.isFetching.value = true
    const wrapper = mountView()

    expect(wrapper.text()).toContain('admin.inquiries.refreshing')
  })
})

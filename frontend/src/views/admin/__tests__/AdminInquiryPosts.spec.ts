import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive } from 'vue'
import { createPaginationStub, identityT } from '@/test/vue-test-helpers'
import type AdminInquiryPostsComponent from '../AdminInquiryPosts.vue'

type AdminInquiryPosts = typeof AdminInquiryPostsComponent

let AdminInquiryPosts: AdminInquiryPosts

const state = reactive({
  closeDetail: vi.fn(),
  detailError: null as unknown,
  error: null as unknown,
  handlePageChange: vi.fn(),
  isDetailFetching: false,
  isDetailLoading: false,
  isFetching: false,
  isLoading: false,
  openDetail: vi.fn(),
  page: 0,
  posts: [
    {
      id: 7,
      title: 'Need help',
      summaryText: 'Question summary',
      authorName: 'Ada',
      createdAtText: '2026-05-26',
      statusLabelKey: 'admin.inquiries.status.pending',
      statusVariant: 'warning',
    },
  ],
  selectedInquiry: {
    id: 7,
    title: 'Need help',
    authorName: 'Ada',
    createdAtText: '2026-05-26',
    contentsHtml: '<p>Question body</p>',
  },
  selectedPostId: null as number | null,
  sort: 'createdAt,desc',
  totalElements: 1,
  totalPages: 3,
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
    state.detailError = null
    state.error = null
    state.isDetailFetching = false
    state.isDetailLoading = false
    state.isFetching = false
    state.isLoading = false
    state.page = 0
    state.posts = [
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
    state.selectedInquiry = {
      id: 7,
      title: 'Need help',
      authorName: 'Ada',
      createdAtText: '2026-05-26',
      contentsHtml: '<p>Question body</p>',
    }
    state.selectedPostId = null
    state.sort = 'createdAt,desc'
    state.totalElements = 1
    state.totalPages = 3
  })

  beforeEach(async () => {
    AdminInquiryPosts = (await import('../AdminInquiryPosts.vue')).default
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
    state.selectedPostId = 7
    const wrapper = mountView()

    await wrapper.get('tbody tr.cursor-pointer').trigger('click')
    expect(state.openDetail).toHaveBeenCalledWith(7)

    await wrapper.get('[data-test="detail-modal"]').trigger('click')
    expect(state.closeDetail).toHaveBeenCalledTimes(1)
  })

  it('shows the shared footer loading message while refreshing', () => {
    state.isFetching = true
    const wrapper = mountView()

    expect(wrapper.text()).toContain('admin.inquiries.refreshing')
  })
})

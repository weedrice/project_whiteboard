import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, type Ref } from 'vue'
import type { PageResponse, Report } from '@/types'

const mocks = vi.hoisted(() => ({
  reportsData: {
    value: {
    content: [{
      reportId: 1,
      reporterId: 10,
      reporterDisplayName: 'Reporter',
      targetId: 20,
      targetType: 'POST',
      targetUserId: 20,
      targetDisplayName: 'Target',
      targetLoginId: 'target',
      reasonType: 'SPAM',
      contents: 'spam',
      remark: '',
      status: 'PENDING',
      createdAt: '2026-05-21T00:00:00',
      modifiedAt: '2026-05-21T00:00:00',
    } as Report],
    number: 0,
    size: 20,
    totalPages: 3,
    totalElements: 60,
    first: true,
    last: false,
    empty: false,
    },
  },
  isLoading: { value: false },
  refetch: vi.fn(),
  resolveReport: vi.fn(),
  confirm: vi.fn(),
  addToast: vi.fn(),
  params: null as Ref<{ page: number; size: number }> | null,
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useReports: (params: Ref<{ page: number; size: number }>) => {
      mocks.params = params
      return {
        data: mocks.reportsData,
        isLoading: mocks.isLoading,
        refetch: mocks.refetch,
      }
    },
    useResolveReport: () => ({
      mutateAsync: mocks.resolveReport,
    }),
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: mocks.confirm,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

import ReportManagement from '../ReportManagement.vue'

const ReportListStub = defineComponent({
  name: 'ReportList',
  props: {
    reports: {
      type: Array,
      required: true,
    },
  },
  template: '<div data-testid="report-list">{{ reports.length }}</div>',
})

const PageSizeSelectorStub = defineComponent({
  name: 'PageSizeSelector',
  props: {
    modelValue: {
      type: Number,
      required: true,
    },
    options: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['update:modelValue', 'change'],
  template: '<button data-testid="size-change" @click="$emit(\'update:modelValue\', 50); $emit(\'change\')">size</button>',
})

const PaginationStub = defineComponent({
  name: 'BasePaginationStub',
  props: {
    currentPage: {
      type: Number,
      required: true,
    },
    totalPages: {
      type: Number,
      required: true,
    },
  },
  emits: ['page-change'],
  template: '<button data-testid="page-change" @click="$emit(\'page-change\', 2)">{{ currentPage }}/{{ totalPages }}</button>',
})

const mountReportManagement = () => mount(ReportManagement, {
  global: {
    stubs: {
      ReportList: ReportListStub,
      ReportDetailModal: true,
      SanctionModal: true,
      PageSizeSelector: PageSizeSelectorStub,
      Pagination: PaginationStub,
    },
  },
})

describe('ReportManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.params = null
    mocks.isLoading.value = false
    mocks.reportsData.value = {
      ...mocks.reportsData.value,
      number: 0,
      size: 20,
      totalPages: 3,
      totalElements: 60,
    }
  })

  it('passes page and size state to useReports', () => {
    mountReportManagement()

    expect(mocks.params?.value).toEqual({ page: 0, size: 20 })
  })

  it('updates report query page from Pagination', async () => {
    const wrapper = mountReportManagement()

    await wrapper.get('[data-testid="page-change"]').trigger('click')

    expect(mocks.params?.value).toEqual({ page: 2, size: 20 })
  })

  it('resets to first page when page size changes', async () => {
    const wrapper = mountReportManagement()

    await wrapper.get('[data-testid="page-change"]').trigger('click')
    expect(mocks.params?.value).toEqual({ page: 2, size: 20 })

    await wrapper.get('[data-testid="size-change"]').trigger('click')

    expect(mocks.params?.value).toEqual({ page: 0, size: 50 })
  })
})

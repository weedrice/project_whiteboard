import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, type Ref } from 'vue'
import { createPinia } from 'pinia'
import type { Report } from '@/types'

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
  confirmWithReason: vi.fn(),
  addToast: vi.fn(),
  params: null as Ref<{
    page: number
    size: number
    status?: 'PENDING' | 'RESOLVED' | 'REJECTED'
    targetType?: 'POST' | 'COMMENT' | 'USER'
  }> | null,
}))

vi.mock('@/features/admin/useAdmin', () => ({
  useAdmin: () => ({
    useReports: (params: Ref<{
      page: number
      size: number
      status?: 'PENDING' | 'RESOLVED' | 'REJECTED'
      targetType?: 'POST' | 'COMMENT' | 'USER'
    }>) => {
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
    confirmWithReason: mocks.confirmWithReason,
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
  emits: ['resolve', 'reject', 'sanction', 'viewDetail'],
  template: `
    <div data-testid="report-list">
      {{ reports.length }}
      <button data-testid="resolve-report" @click="$emit('resolve', reports[0])">resolve</button>
      <button data-testid="reject-report" @click="$emit('reject', reports[0])">reject</button>
      <button data-testid="sanction-report" @click="$emit('sanction', reports[0])">sanction</button>
      <button data-testid="view-report" @click="$emit('viewDetail', reports[0])">view</button>
    </div>
  `,
})

const ReportDetailModalStub = defineComponent({
  name: 'ReportDetailModal',
  props: {
    isOpen: {
      type: Boolean,
      required: true,
    },
    report: {
      type: Object,
      default: null,
    },
  },
  emits: ['close'],
  template: '<button data-testid="detail-modal" :data-open="String(isOpen)" :data-report-id="report?.reportId ?? \'\'" @click="$emit(\'close\')">detail</button>',
})

const SanctionModalStub = defineComponent({
  name: 'SanctionModal',
  props: {
    isOpen: {
      type: Boolean,
      required: true,
    },
    user: {
      type: Object,
      required: true,
    },
  },
  emits: ['close', 'sanctioned'],
  template: `
    <div>
      <button
        data-testid="sanction-modal"
        :data-open="String(isOpen)"
        :data-user-id="user.id"
        :data-content-id="user.sanctionContentId"
        :data-content-type="user.sanctionContentType"
        @click="$emit('close')"
      >sanction modal</button>
      <button data-testid="sanctioned" @click="$emit('sanctioned', {
        targetUserId: user.id,
        reportId: user.reportId,
        modalRevision: user.modalRevision,
        sessionGeneration: 0
      })">sanctioned</button>
    </div>
  `,
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
    plugins: [createPinia()],
    stubs: {
      ReportList: ReportListStub,
      ReportDetailModal: ReportDetailModalStub,
      SanctionModal: SanctionModalStub,
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

  it('adds report filters to the query and resets the page', async () => {
    const wrapper = mountReportManagement()

    await wrapper.get('[data-testid="page-change"]').trigger('click')
    await wrapper.get('#admin-report-status-filter').setValue('PENDING')

    expect(mocks.params?.value).toEqual({ page: 0, size: 20, status: 'PENDING' })

    await wrapper.get('[data-testid="page-change"]').trigger('click')
    await wrapper.get('#admin-report-target-filter').setValue('COMMENT')

    expect(mocks.params?.value).toEqual({
      page: 0,
      size: 20,
      status: 'PENDING',
      targetType: 'COMMENT',
    })
  })

  it('does not directly refetch after resolving because mutation invalidates reports', async () => {
    mocks.confirmWithReason.mockResolvedValueOnce('reviewed')
    mocks.resolveReport.mockResolvedValueOnce(undefined)
    const wrapper = mountReportManagement()

    await wrapper.get('[data-testid="resolve-report"]').trigger('click')
    await flushPromises()

    expect(mocks.resolveReport).toHaveBeenCalledWith({ reportId: 1, data: { status: 'RESOLVED', remark: 'reviewed' } })
    expect(mocks.refetch).not.toHaveBeenCalled()
  })

  it('does not directly refetch after rejecting because mutation invalidates reports', async () => {
    mocks.confirmWithReason.mockResolvedValueOnce('insufficient evidence')
    mocks.resolveReport.mockResolvedValueOnce(undefined)
    const wrapper = mountReportManagement()

    await wrapper.get('[data-testid="reject-report"]').trigger('click')
    await flushPromises()

    expect(mocks.resolveReport).toHaveBeenCalledWith({ reportId: 1, data: { status: 'REJECTED', remark: 'insufficient evidence' } })
    expect(mocks.refetch).not.toHaveBeenCalled()
  })

  it('opens and closes report detail state through the modal contract', async () => {
    const wrapper = mountReportManagement()

    expect(wrapper.get('[data-testid="detail-modal"]').attributes('data-open')).toBe('false')

    await wrapper.get('[data-testid="view-report"]').trigger('click')

    expect(wrapper.get('[data-testid="detail-modal"]').attributes('data-open')).toBe('true')
    expect(wrapper.get('[data-testid="detail-modal"]').attributes('data-report-id')).toBe('1')

    await wrapper.get('[data-testid="detail-modal"]').trigger('click')

    expect(wrapper.get('[data-testid="detail-modal"]').attributes('data-open')).toBe('false')
  })

  it('maps report target data for sanctions without a redundant direct refetch', async () => {
    const wrapper = mountReportManagement()

    await wrapper.get('[data-testid="sanction-report"]').trigger('click')

    const modal = wrapper.get('[data-testid="sanction-modal"]')
    expect(modal.attributes('data-open')).toBe('true')
    expect(modal.attributes('data-user-id')).toBe('20')
    expect(modal.attributes('data-content-id')).toBe('20')
    expect(modal.attributes('data-content-type')).toBe('POST')

    await wrapper.get('[data-testid="sanctioned"]').trigger('click')

    expect(mocks.refetch).not.toHaveBeenCalled()
  })
})

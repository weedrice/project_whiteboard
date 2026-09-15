import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import InquiryStatusBadge from '@/components/inquiry/InquiryStatusBadge.vue'
import InquiryList from '../InquiryList.vue'

const { state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    state: {
      options: null as null | { queryKey: { value: readonly unknown[] } },
      query: {
        data: refOf<unknown>(null),
        isLoading: refOf(false),
        error: refOf<unknown>(null),
        refetch: vi.fn(),
      },
    },
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: { date?: string }) => params?.date ? `${key}:${params.date}` : key,
  }),
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiPageQuery: (options: { queryKey: { value: readonly unknown[] } }) => {
    state.options = options
    return state.query
  },
}))

vi.mock('@/api/inquiry', () => ({
  inquiryApi: { getMine: vi.fn() },
}))

const page = {
  content: [{
    inquiryId: 9,
    title: 'Account recovery',
    category: 'ACCOUNT',
    status: 'NEW',
    lastPublicMessageSummary: 'Please help',
    modifiedAt: '2026-09-15T10:00:00Z',
  }],
  totalPages: 2,
  totalElements: 1,
  page: 0,
  size: 20,
}

const mountView = () => mount(InquiryList, {
  global: {
    stubs: {
      RouterLink: RouterLinkStub,
      Pagination: { template: '<nav />' },
      PageHeader: { template: '<header><slot name="actions" /></header>' },
    },
  },
})

describe('InquiryList', () => {
  beforeEach(() => {
    state.options = null
    state.query.data.value = page
    state.query.isLoading.value = false
    state.query.error.value = null
    state.query.refetch.mockReset()
  })

  it('uses shared cards, selects, and status badges while preserving filter params', async () => {
    const wrapper = mountView()

    expect(wrapper.findAllComponents(BaseCard).length).toBeGreaterThanOrEqual(2)
    expect(wrapper.findAllComponents(BaseSelect)).toHaveLength(2)
    expect(wrapper.getComponent(InquiryStatusBadge).props('status')).toBe('NEW')
    expect(wrapper.text()).toContain('Account recovery')

    await wrapper.findAll('select')[0].setValue('RESOLVED')
    expect(state.options?.queryKey.value[2]).toMatchObject({ page: 0, status: 'RESOLVED' })
  })

  it('uses the shared retryable error and empty states', async () => {
    state.query.error.value = new Error('failed')
    const errorWrapper = mountView()

    expect(errorWrapper.get('[role="alert"]').text()).toContain('inquiry.common.loadFailed')
    await errorWrapper.get('[role="alert"] button').trigger('click')
    expect(state.query.refetch).toHaveBeenCalledOnce()

    state.query.error.value = null
    state.query.data.value = { ...page, content: [] }
    const emptyWrapper = mountView()
    expect(emptyWrapper.get('[role="status"]').text()).toContain('inquiry.common.empty')
  })
})

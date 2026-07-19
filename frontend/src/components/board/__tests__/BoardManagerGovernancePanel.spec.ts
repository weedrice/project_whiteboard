import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BoardManagerGovernancePanel from '../BoardManagerGovernancePanel.vue'
import ReportList from '@/components/admin/ReportList.vue'
import ReportDetailModal from '@/components/admin/ReportDetailModal.vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import type { Report } from '@/types'

const boardApiMock = vi.hoisted(() => ({
  getManagerReports: vi.fn(),
  getManagerAudits: vi.fn(),
}))

vi.mock('@/api/board', () => ({ boardApi: boardApiMock }))
vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key, locale: { value: 'ko' } }),
  }
})

const pageResponse = (size = 20, content: unknown[] = []) => ({
  data: {
    success: true,
    data: {
      content,
      page: 0,
      size,
      totalElements: 41,
      totalPages: 3,
      hasNext: true,
      hasPrevious: false,
      last: false,
    },
  },
})

describe('BoardManagerGovernancePanel', () => {
  const mountedWrappers: Array<ReturnType<typeof mount>> = []

  beforeEach(() => {
    vi.clearAllMocks()
    boardApiMock.getManagerReports.mockResolvedValue(pageResponse())
    boardApiMock.getManagerAudits.mockResolvedValue(pageResponse())
  })

  afterEach(() => {
    mountedWrappers.forEach((wrapper) => wrapper.unmount())
    mountedWrappers.length = 0
  })

  function mountPanel(enabled = true) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(BoardManagerGovernancePanel, {
      props: { boardUrl: 'free', enabled },
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true, RouterLink: true },
      },
    })
    mountedWrappers.push(wrapper)
    return { wrapper, queryClient }
  }

  it('isolates report and audit requests by board URL and query params', async () => {
    const { wrapper, queryClient } = mountPanel()
    await flushPromises()

    expect(boardApiMock.getManagerReports).toHaveBeenCalledWith(
      'free',
      { page: 0, size: 20 },
      { signal: expect.any(AbortSignal) },
    )
    expect(boardApiMock.getManagerAudits).toHaveBeenCalledWith(
      'free',
      { page: 0, size: 20, sort: 'createdAt,desc' },
      { signal: expect.any(AbortSignal) },
    )

    const reportsSection = wrapper.get('[aria-labelledby="board-manager-reports-title"]')
    const reportPageTwo = reportsSection.findAll('button').find((button) => button.text() === '2')
    expect(reportPageTwo).toBeTruthy()
    await reportPageTwo!.trigger('click')
    await flushPromises()
    expect(boardApiMock.getManagerReports).toHaveBeenLastCalledWith(
      'free',
      { page: 1, size: 20 },
      { signal: expect.any(AbortSignal) },
    )

    await wrapper.get('#board-report-status').setValue('PENDING')
    await flushPromises()
    expect(boardApiMock.getManagerReports).toHaveBeenLastCalledWith(
      'free',
      { page: 0, size: 20, status: 'PENDING' },
      { signal: expect.any(AbortSignal) },
    )

    const auditsSection = wrapper.get('[aria-labelledby="board-manager-audits-title"]')
    const auditPageTwo = auditsSection.findAll('button').find((button) => button.text() === '2')
    expect(auditPageTwo).toBeTruthy()
    await auditPageTwo!.trigger('click')
    await flushPromises()
    expect(boardApiMock.getManagerAudits).toHaveBeenLastCalledWith(
      'free',
      { page: 1, size: 20, sort: 'createdAt,desc' },
      { signal: expect.any(AbortSignal) },
    )

    await wrapper.get('#board-audit-action').setValue('POST_BLIND')
    await wrapper.get('#board-audit-actor-type').setValue('USER')
    await wrapper.get('#board-audit-actor-user-id').setValue('17')
    await wrapper.get('#board-audit-start-date').setValue('2026-07-01')
    await wrapper.get('#board-audit-end-date').setValue('2026-07-31')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(boardApiMock.getManagerAudits).toHaveBeenLastCalledWith(
      'free',
      {
        page: 0,
        size: 20,
        action: 'POST_BLIND',
        actorType: 'USER',
        actorUserId: 17,
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        sort: 'createdAt,desc',
      },
      { signal: expect.any(AbortSignal) },
    )

    const queryKeys = queryClient.getQueryCache().getAll().map((query) => query.queryKey)
    expect(queryKeys).toContainEqual(expect.arrayContaining([
      'board',
      'manager-reports',
      'free',
      expect.objectContaining({ status: 'PENDING' }),
    ]))
    expect(queryKeys).toContainEqual(expect.arrayContaining([
      'board',
      'manager-audits',
      'free',
      expect.objectContaining({ action: 'POST_BLIND', actorUserId: 17 }),
    ]))

    await wrapper.setProps({ boardUrl: 'qna' })
    await flushPromises()
    expect(boardApiMock.getManagerReports).toHaveBeenLastCalledWith(
      'qna',
      expect.objectContaining({ page: 0, status: 'PENDING' }),
      { signal: expect.any(AbortSignal) },
    )
    expect(boardApiMock.getManagerAudits).toHaveBeenLastCalledWith(
      'qna',
      expect.objectContaining({ page: 0, action: 'POST_BLIND' }),
      { signal: expect.any(AbortSignal) },
    )
  })

  it('does not request board-manager data before authorization is confirmed', async () => {
    const { wrapper } = mountPanel(false)
    await flushPromises()

    expect(boardApiMock.getManagerReports).not.toHaveBeenCalled()
    expect(boardApiMock.getManagerAudits).not.toHaveBeenCalled()

    await wrapper.setProps({ enabled: true })
    await flushPromises()
    expect(boardApiMock.getManagerReports).toHaveBeenCalledOnce()
    expect(boardApiMock.getManagerAudits).toHaveBeenCalledOnce()
  })

  it('clears an open report detail when its board, authorization, or auth session changes', async () => {
    const report: Report = {
      reportId: 7,
      reporterDisplayName: 'Reporter',
      targetType: 'POST',
      targetId: 11,
      reasonType: 'SPAM',
      status: 'PENDING',
      createdAt: '2026-07-19T10:00:00',
    }
    boardApiMock.getManagerReports.mockResolvedValue(pageResponse(20, [report]))
    const { wrapper } = mountPanel()
    await flushPromises()

    const detailModal = () => wrapper.getComponent(ReportDetailModal)
    wrapper.getComponent(ReportList).vm.$emit('viewDetail', report)
    await flushPromises()
    expect(detailModal().props()).toMatchObject({ isOpen: true, report })

    await wrapper.setProps({ boardUrl: 'qna' })
    expect(detailModal().props()).toMatchObject({ isOpen: false, report: null })
    await flushPromises()

    wrapper.getComponent(ReportList).vm.$emit('viewDetail', report)
    await flushPromises()
    await wrapper.setProps({ enabled: false })
    expect(detailModal().props()).toMatchObject({ isOpen: false, report: null })

    await wrapper.setProps({ enabled: true })
    await flushPromises()
    wrapper.getComponent(ReportList).vm.$emit('viewDetail', report)
    await flushPromises()
    notifyAuthSessionBoundary(1)
    await nextTick()
    expect(detailModal().props()).toMatchObject({ isOpen: false, report: null })
  })
})

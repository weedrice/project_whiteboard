import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminDashboard from '../AdminDashboard.vue'
import type { ModerationAuditSearchParams } from '@/types/admin'

const mocks = vi.hoisted(() => ({
  statsLoading: { __v_isRef: true, value: false },
  statsError: { __v_isRef: true, value: false },
  deepLoading: { __v_isRef: true, value: false },
  deepError: { __v_isRef: true, value: false },
  auditLoading: { __v_isRef: true, value: false },
  auditError: { __v_isRef: true, value: false },
  refetchStats: vi.fn(),
  refetchDeep: vi.fn(),
  refetchAudit: vi.fn(),
  stats: {
    __v_isRef: true,
    value: {
      totalUsers: 10,
      totalPosts: 25,
      pendingReports: 2,
      activeUsers: 4,
    },
  },
  deepStats: {
    __v_isRef: true,
    value: {
      days: 30,
      daily: [],
      topBoards: [],
      moderation: {
        pendingReports: 0,
        resolvedReports: 0,
        rejectedReports: 0,
        autoBlinds: 0,
        managerBlinds: 0,
      },
    },
  },
  audits: { __v_isRef: true, value: { content: [] } },
  auditParams: { value: null as unknown },
}))

vi.mock('@/features/admin/useAdmin', () => ({
  useAdmin: () => ({
    useDashboardStats: () => ({
      data: mocks.stats,
      isLoading: mocks.statsLoading,
      isError: mocks.statsError,
      refetch: mocks.refetchStats,
    }),
    useDeepDashboardStats: () => ({
      data: mocks.deepStats,
      isLoading: mocks.deepLoading,
      isError: mocks.deepError,
      refetch: mocks.refetchDeep,
    }),
    useModerationAudits: (params: unknown) => {
      mocks.auditParams.value = params
      return {
        data: mocks.audits,
        isLoading: mocks.auditLoading,
        isError: mocks.auditError,
        refetch: mocks.refetchAudit,
      }
    },
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('AdminDashboard', () => {
  beforeEach(() => {
    mocks.stats.value = {
      totalUsers: 10,
      totalPosts: 25,
      pendingReports: 2,
      activeUsers: 4,
    }
    mocks.statsLoading.value = false
    mocks.statsError.value = false
    mocks.deepLoading.value = false
    mocks.deepError.value = false
    mocks.auditLoading.value = false
    mocks.auditError.value = false
    vi.clearAllMocks()
  })

  it('renders dashboard metrics with detail links and empty activity state', () => {
    const wrapper = mount(AdminDashboard, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })

    expect(wrapper.findAll('.admin-metric-card')).toHaveLength(3)
    expect(wrapper.text()).toContain('admin.dashboard.totalUsers')
    expect(wrapper.text()).toContain('10')
    expect(wrapper.text()).toContain('admin.dashboard.pendingReports')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('admin.dashboard.activeUsers24h')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))).toEqual([
      '/admin/users',
      '/admin/reports',
      '/admin/users',
    ])
    expect(wrapper.text()).toContain('admin.dashboard.noActivity')
  })

  it('shows a retryable error instead of zero metrics when stats fail', async () => {
    mocks.statsError.value = true
    const wrapper = mount(AdminDashboard, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.findAll('.admin-metric-card')).toHaveLength(0)
    const error = wrapper.get('[role="alert"]')
    await error.get('button').trigger('click')
    expect(mocks.refetchStats).toHaveBeenCalledOnce()
  })

  it('searches audit logs by space and user names', async () => {
    const wrapper = mount(AdminDashboard, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    await wrapper.get('input[placeholder="admin.dashboard.auditBoardName"]').setValue(' 개발 ')
    await wrapper.get('input[placeholder="admin.dashboard.auditActorName"]').setValue(' 운영자 ')

    const params = mocks.auditParams.value as { value: ModerationAuditSearchParams }
    expect(params.value).toEqual(expect.objectContaining({
      boardName: '개발',
      actorName: '운영자',
    }))
    expect(params.value).not.toHaveProperty('boardUrl')
    expect(params.value).not.toHaveProperty('actorUserId')
  })
})

import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminDashboard from '../AdminDashboard.vue'

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
    useModerationAudits: () => ({
      data: mocks.audits,
      isLoading: mocks.auditLoading,
      isError: mocks.auditError,
      refetch: mocks.refetchAudit,
    }),
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
})

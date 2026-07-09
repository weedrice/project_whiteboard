import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminDashboard from '../AdminDashboard.vue'

const mocks = vi.hoisted(() => ({
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
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useDashboardStats: () => ({
      data: mocks.stats,
    }),
    useDeepDashboardStats: () => ({
      data: mocks.deepStats,
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
})

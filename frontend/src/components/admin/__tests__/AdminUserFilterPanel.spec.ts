import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AdminUserFilterPanel from '../AdminUserFilterPanel.vue'
import { createInitialAdminUserFilters } from '@/composables/useAdminUserListState'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('AdminUserFilterPanel', () => {
  it('connects every filter label to its form control', () => {
    const wrapper = mount(AdminUserFilterPanel, {
      props: {
        filterForm: createInitialAdminUserFilters(),
        getStatusLabel: (status: string) => status,
        getRoleLabel: (role: string) => role,
      },
      global: {
        stubs: {
          Search: true,
        },
      },
    })

    const expectedControlIds = [
      'admin-user-filter-status',
      'admin-user-filter-role',
      'admin-user-filter-email-verified',
      'admin-user-filter-super-admin',
      'admin-user-filter-withdrawn',
      'admin-user-filter-created-from',
      'admin-user-filter-created-to',
      'admin-user-filter-last-login-from',
      'admin-user-filter-last-login-to',
      'admin-user-filter-q',
    ]

    for (const id of expectedControlIds) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.find(`#${id}`).exists()).toBe(true)
    }
  })
})

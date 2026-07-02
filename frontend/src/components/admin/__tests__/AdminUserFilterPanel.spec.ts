import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AdminUserFilterPanel from '../AdminUserFilterPanel.vue'
import { createInitialAdminUserFilters } from '@/features/admin/users/useAdminUserListState'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('AdminUserFilterPanel', () => {
  const mountPanel = () => mount(AdminUserFilterPanel, {
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

  it('connects every filter label to its form control', () => {
    const wrapper = mountPanel()

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

  it('ignores composing enter before emitting search', async () => {
    const wrapper = mountPanel()
    const input = wrapper.get('#admin-user-filter-q')

    await input.trigger('keyup.enter', { isComposing: true })
    expect(wrapper.emitted('search')).toBeUndefined()

    await input.trigger('keyup.enter')
    expect(wrapper.emitted('search')).toHaveLength(1)
  })
})

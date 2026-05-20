import { describe, expect, it } from 'vitest'
import {
  getAdminUserRoleLabel,
  getAdminUserRoleVariant,
  getAdminUserStatusLabel,
  getAdminUserStatusVariant,
} from '../adminUserDisplay'

const t = (key: string) => key

describe('adminUserDisplay', () => {
  it('maps user statuses to badge variants', () => {
    expect(getAdminUserStatusVariant('ACTIVE')).toBe('success')
    expect(getAdminUserStatusVariant('SUSPENDED')).toBe('danger')
    expect(getAdminUserStatusVariant('SANCTIONED')).toBe('danger')
    expect(getAdminUserStatusVariant('DELETED')).toBe('warning')
    expect(getAdminUserStatusVariant('UNKNOWN')).toBe('gray')
  })

  it('maps user roles to badge variants', () => {
    expect(getAdminUserRoleVariant('SUPER_ADMIN')).toBe('danger')
    expect(getAdminUserRoleVariant('BOARD_ADMIN')).toBe('warning')
    expect(getAdminUserRoleVariant('MODERATOR')).toBe('warning')
    expect(getAdminUserRoleVariant('USER')).toBe('gray')
    expect(getAdminUserRoleVariant(undefined)).toBe('gray')
  })

  it('builds localized status and role labels', () => {
    expect(getAdminUserStatusLabel(t, 'ACTIVE')).toBe('admin.users.status.ACTIVE')
    expect(getAdminUserRoleLabel(t, 'USER')).toBe('admin.users.role.USER')
    expect(getAdminUserRoleLabel(t, undefined)).toBe('-')
  })
})

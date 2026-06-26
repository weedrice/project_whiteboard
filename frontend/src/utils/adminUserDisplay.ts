type BadgeVariant = 'success' | 'danger' | 'warning' | 'gray'
export type AdminUserMutableStatus = 'ACTIVE' | 'SUSPENDED'

export function getAdminUserStatusVariant(status: string): BadgeVariant {
  if (status === 'ACTIVE') return 'success'
  if (status === 'SUSPENDED' || status === 'SANCTIONED') return 'danger'
  if (status === 'DELETED') return 'warning'
  return 'gray'
}

export function getAdminUserRoleVariant(role: string | undefined): BadgeVariant {
  switch (role) {
    case 'SUPER_ADMIN':
      return 'danger'
    case 'BOARD_ADMIN':
    case 'MODERATOR':
      return 'warning'
    default:
      return 'gray'
  }
}

export function getAdminUserStatusLabel(t: (key: string) => string, status: string): string {
  return t(`admin.users.status.${status}`)
}

export function getAdminUserRoleLabel(t: (key: string) => string, role: string | undefined): string {
  if (!role) return '-'
  return t(`admin.users.role.${role}`)
}

export function canChangeAdminUserStatus(status: string): status is AdminUserMutableStatus {
  return status === 'ACTIVE' || status === 'SUSPENDED'
}

export function getNextAdminUserStatus(status: AdminUserMutableStatus): AdminUserMutableStatus {
  return status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
}

export function getAdminUserStatusActionLabel(
  t: (key: string) => string,
  status: AdminUserMutableStatus,
): string {
  return status === 'ACTIVE'
    ? t('admin.users.status.SUSPENDED')
    : t('admin.users.status.ACTIVE')
}

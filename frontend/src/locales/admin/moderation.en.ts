import type { AdminMessages } from '../types'

export const adminModerationMessagesEn = {
  admins: {
    title: 'Admin management',
    description: 'Assign and manage administrator permissions.',
    addSuperAdmin: 'Add super admin',
    addSuperAdminDesc: 'Grant super admin permissions to a user.',
    addBoardAdmin: 'Add space admin',
    addBoardAdminDesc: 'Assign a user as an administrator for a space.',
    boardId: 'Space ID',
    superAdmins: 'Super admins',
    boardAdmins: 'Space admins',
    loginIdPlaceholder: 'User login ID',
    table: {
      loginId: 'Login ID',
    },
    messages: {
      added: 'The administrator has been added.',
      addFailed: 'Failed to add administrator',
      statusChanged: 'The status has been changed.',
      statusChangeFailed: 'Failed to change the status',
      inputLoginId: 'Enter a login ID.',
    },
  },
  reports: {
    title: 'Report management',
    description: 'Review and process submitted reports.',
    targetType: 'Target type',
    reasonType: 'Report type',
    targetContentId: 'Target content ID',
    remark: 'Reason',
    statusFilter: 'Status',
    detail: {
      title: 'Report details',
      reportInfo: 'Report information',
    },
    table: {
      reporter: 'Reporter',
      createdAt: 'Submitted',
      processor: 'Processed by',
    },
    status: {
      PENDING: 'Pending',
      RESOLVED: 'Resolved',
      REJECTED: 'Rejected',
    },
    actions: {
      resolve: 'Approve',
      reject: 'Reject',
      sanction: 'Sanction',
    },
    messages: {
      confirmResolve: 'Approve this report?',
      resolved: 'The report has been resolved.',
      resolveFailed: 'Failed to resolve report',
      sanctionResolved: 'The report was resolved after creating the sanction.',
      sanctionResolveFailed: 'The sanction was created, but the report status could not be updated.',
      confirmReject: 'Reject this report?',
      rejected: 'The report has been rejected.',
      rejectFailed: 'Failed to reject report',
    },
  },
  inquiries: {
    title: 'Inquiries',
    description: 'Review posts in the inquiry space and respond with comments.',
    empty: 'No inquiries.',
    total: '{count} total',
    refreshing: 'Refreshing...',
    sort: {
      label: 'Sort',
      latest: 'Newest',
      oldest: 'Oldest',
    },
    table: {
      summary: 'Content',
    },
    status: {
      answered: 'Answered',
      pending: 'Awaiting response',
    },
    detail: {
      title: 'Inquiry details',
    },
  },
} satisfies Pick<AdminMessages, 'admins' | 'reports' | 'inquiries'>

/**
 * Locale message type definitions.
 */

export interface AdminMessages {
  common: {
    search: string
    reset: string
    all: string
    pageSize: string
    detail: string
    rowDetailAria: string
    footerDoubleClickHint: string
  }
  layout: {
    title: string
  }
  menu: {
    dashboard: string
    users: string
    admins: string
    boards: string
    inquiries: string
    reports: string
    security: string
    settings: string
    errorLogs: string
  }
  dashboard: {
    title: string
    totalUsers: string
    pendingReports: string
    blockedIps: string
    activeUsers24h: string
    viewDetail: string
    recentActivity: string
    noActivity: string
    deepStats: string
    days30: string
    days90: string
    dailyActivity: string
    postsLine: string
    commentsBar: string
    moderationStatus: string
    pending: string
    resolved: string
    rejected: string
    autoBlinds: string
    managerBlinds: string
    topSpaces: string
    auditLogs: string
    auditAction: string
    auditActorType: string
    auditBoardUrl: string
    auditActorUserId: string
    auditStartDate: string
    auditEndDate: string
    auditEmpty: string
    auditTarget: string
    auditActor: string
    auditReason: string
    auditCreatedAt: string
    auditActions: Record<string, string>
    auditTargets: Record<string, string>
  }
  users: {
    title: string
    description: string
    searchPlaceholder: string
    detail: {
      title: string
      basicInfo: string
      status: string
      role: string
      emailVerified: string
      emailNotVerified: string
      dateInfo: string
      createdAt: string
      modifiedAt: string
      lastLoginAt: string
      bio: string
      profile: string
      superAdmin: string
      writtenPosts: string
      writtenComments: string
      subscribedBoards: string
      reportsAndSanctions: string
      joinedAndRecentLogin: string
      joined: string
      deletedAt: string
      recentAccess: string
      accessTime: string
      postsEmpty: string
      commentsEmpty: string
      subscriptionsEmpty: string
      loadFailed: string
    }
    filters: {
      status: string
      role: string
      emailVerified: string
      superAdmin: string
      withdrawn: string
      verified: string
      unverified: string
      withdrawnOnly: string
      activeOrSuspended: string
      createdFrom: string
      createdTo: string
      lastLoginFrom: string
      lastLoginTo: string
      userSearch: string
      unit: string
    }
    detailTabs: {
      visible: string
      notice: string
      secret: string
      spoiler: string
      reply: string
      sourceDeleted: string
      boardInactive: string
      boardPrivate: string
      subscriptionInactive: string
      subscriptionPrivate: string
      subscriptionRestricted: string
      subscriptionAccessible: string
      boardActive: string
      boardInactiveShort: string
      boardPublic: string
      boardPrivateShort: string
      category: string
      postStats: string
      commentStats: string
      sortOrder: string
    }
    table: {
      nickname: string
      email: string
      status: string
      joinedAt: string
    }
    status: {
      ACTIVE: string
      SUSPENDED: string
      DELETED: string
    }
    role: {
      USER: string
      ADMIN: string
      SUPER_ADMIN: string
      BOARD_ADMIN: string
      MODERATOR: string
    }
    actions: {
      ban: string
      mute: string
    }
    messages: {
      confirmStatusChange: string
      statusChanged: string
      statusChangeFailed: string
      enterReason: string
      sanctionTitle: string
      sanctionComplete: string
      sanctionFailed: string
    }
  }
  sanction: {
    title: string
    userLabel: string
    reason: string
    description: string
    descriptionPlaceholder: string
    duration: string
    durationHint: string
    cancel: string
    processing: string
    submit: string
    success: string
    reasons: {
      SPAM: string
      ABUSIVE_LANGUAGE: string
      INAPPROPRIATE_CONTENT: string
      OTHER: string
    }
  }
  admins: {
    title: string
    description: string
    addSuperAdmin: string
    addSuperAdminDesc: string
    addBoardAdmin: string
    addBoardAdminDesc: string
    boardId: string
    superAdmins: string
    boardAdmins: string
    loginIdPlaceholder: string
    table: {
      loginId: string
    }
    messages: {
      added: string
      addFailed: string
      statusChanged: string
      statusChangeFailed: string
      inputLoginId: string
    }
  }
  reports: {
    title: string
    description: string
    targetType: string
    reasonType: string
    targetContentId: string
    remark: string
    statusFilter: string
    detail: {
      title: string
      reportInfo: string
    }
    table: {
      reporter: string
      createdAt: string
      processor: string
    }
    status: {
      PENDING: string
      RESOLVED: string
      REJECTED: string
    }
    actions: {
      resolve: string
      reject: string
      sanction: string
    }
    messages: {
      confirmResolve: string
      resolved: string
      resolveFailed: string
      sanctionResolved: string
      sanctionResolveFailed: string
      confirmReject: string
      rejected: string
      rejectFailed: string
    }
  }
  inquiries: {
    title: string
    description: string
    empty: string
    total: string
    refreshing: string
    sort: {
      label: string
      latest: string
      oldest: string
    }
    table: {
      summary: string
    }
    status: {
      answered: string
      pending: string
    }
    detail: {
      title: string
    }
  }
  security: {
    title: string
    description: string
    detail: {
      title: string
      blockInfo: string
      endDate: string
    }
    addTitle: string
    ipAddress: string
    ipPlaceholder: string
    reason: string
    reasonPlaceholder: string
    table: {
      ipAddress: string
      reason: string
      adminId: string
      createdAt: string
    }
    actions: {
      unblock: string
    }
    messages: {
      blocked: string
      blockFailed: string
      confirmUnblock: string
      unblocked: string
      unblockFailed: string
    }
  }
  settings: {
    title: string
    description: string
    addConfig: string
    table: {
      desc: string
    }
    messages: {
      saved: string
      saveFailed: string
    }
  }
  boards: {
    title: string
    description: string
    addTitle: string
    editTitle: string
    iconEmpty: string
    iconUrlEmpty: string
    chooseFile: string
    managerTitle: string
    chooseManager: string
    userSelectTitle: string
    table: {
      desc: string
      active: string
      sortOrder: string
      actions: string
    }
    messages: {
      created: string
      updated: string
      deleted: string
      createFailed: string
      updateFailed: string
      deleteFailed: string
      confirmDelete: string
      confirmDiscardChanges: string
      confirmDeactivate: string
    }
  }
  errorLogs: {
    title: string
    description: string
    empty: string
    table: {
      errorCode: string
      errorType: string
      httpStatus: string
      message: string
      requestUri: string
      requestMethod: string
      userId: string
      ipAddress: string
      isResolved: string
      createdAt: string
    }
    status: {
      resolved: string
      unresolved: string
    }
    filter: {
      all: string
      errorType: string
      httpStatus: string
      isResolved: string
      startDate: string
      endDate: string
      requestUri: string
    }
    detail: {
      title: string
      errorInfo: string
      requestInfo: string
      stackTrace: string
      resolveInfo: string
      closeAria: string
      resolveCloseAria: string
      resolvedBy: string
      resolvedAt: string
      resolvedMemo: string
    }
    actions: {
      resolve: string
      viewDetail: string
      copy: string
    }
    messages: {
      resolved: string
      resolveFailed: string
      stackTraceCopied: string
      stackTraceCopyFailed: string
    }
    stats: {
      total: string
      unresolved: string
      resolved: string
    }
    memoPlaceholder: string
  }
}

// 전체 메시지 타입

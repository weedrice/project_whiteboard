/**
 * 번역 메시지 타입 정의
 * 
 * 이 타입은 messages 객체의 구조를 정의하여 타입 안정성을 제공합니다.
 */

// 공통 메시지 타입
export interface CommonMessages {
  advertisement: string
  appName: string
  loading: string
  noData: string
  loadMore: string
  add: string
  save: string
  warning: string
  cancel: string
  delete: string
  edit: string
  close: string
  post: string
  previous: string
  next: string
  back: string
  return: string
  or: string
  top: string
  report: string
  share: string
  copy: string
  chooseFile: string
  send: string
  saving: string
  yes: string
  input: string
  saveChanges: string
  confirmDelete: string
  deleted: string
  defaultAdminName: string
  displayName: string
  mailbox: string
  confirm: string
  query: string
  boards: string
  tags: string
  skipToContent: string
  footer: {
    rights: string
    github: string
    switchToLight: string
    switchToDark: string
  }
  error: {
    goHome: string
    notFound: string
    forbidden: string
    serverError: string
    unknown: string
    defaultMessage: string
    retry: string
    showDetails: string
    chunkLoadError: string
    chunkLoadErrorDescription: string
    networkError: string
    networkErrorDescription: string
  }
  messages: {
    error: string
    defaultTitle: string
    defaultMessage: string
    sessionExpired: string
    badRequest: string
    forbidden: string
    notFound: string
    serverError: string
    unknown: string
    network: string
    networkRetry: string
    requestSetup: string
    success: string
    uploadFailed: string
    urlCopied: string
    confirmDelete: string
    deleteSuccess: string
    deleteFailed: string
    sending: string
    reporting: string
    saving: string
    save: string
    saveSuccess: string
    saveFailed: string
    noResults: string
  }
  network: {
    offline: string
    online: string
  }
  title: string
  content: string
  write: string
  date: string
  description: string
  id: string
  status: string
  createdAt: string
  updatedAt: string
  category: string
  author: string
  login: string
  loginId: string
  password: string
  email: string
  logout: string
  settings: string
  notifications: string
  myPage: string
  submit: string
  pageSize: string
  admin: string
  subscribe: string
  subscribed: string
  unsubscribe: string
  manage: string
  subscribers: string
  no: string
  noValue: string
  views: string
  likes: string
  notice: string
  points: string
  scrap: string
  block: string
  name: string
  url: string
  key: string
  value: string
  target: string
  reason: string
  role: string
  board: string
  deactivate: string
  activate: string
  active: string
  inactive: string
  sortOrder: string
  time: {
    justNow: string
    minutesAgo: string
    hoursAgo: string
    daysAgo: string
  }
  viewAll: string
  sent: string
  languages: {
    ko: string
    en: string
  }
}

// 검색 메시지 타입
export interface SearchMessages {
  boards: string
  doSearch: string
  results: string
  query: string
  noResults: string
  noResultsFor: string
  placeholder: string
}

// 레이아웃 메시지 타입
export interface LayoutMessages {
  menu: {
    admin: string
    recent: string
    reports: string
    createBoard: string
  }
  banner: string
}

// 인증 메시지 타입
export interface AuthMessages {
  createAccount: string
  socialLogin: string
  createAccountTitle: string
  signingIn: string
  loginFailed: string
  alreadyHaveAccount: string
  creatingAccount: string
  signup: string
  signupSuccess: string
  signupFailed: string
  findAccount: string
  findId: string
  findPassword: string
  sendCode: string
  resendCode: string
  verifyCode: string
  codeSent: string
  codeExpired: string
  codeVerified: string
  codePlaceholder: string
  yourIdIs: string
  resetPassword: string
  newPassword: string
  newPasswordConfirm: string
  passwordResetSuccess: string
  findIdPassword: string
  placeholders: {
    loginId: string
    password: string
    email: string
    displayName: string
    newEmail: string
  }
  email: string
  login: string
  emailNotVerified: string
  passwordMismatch: string
  verificationFailed: string
  validation: {
    passwordStrength: string
    loginIdFormat: string
    emailFormat: string
  }
}

// 게시판 메시지 타입
export interface BoardMessages {
  list: {
    subscribed: string
    all: string
    noSubscribed: string
    noBoards: string
    noPosts: string
    title: string
    noDesc: string
    subscribers: string
    empty: string
  }
  feed: {
    likes: string
    viewAllComments: string
  }
  createBoard: string
  loadFailed: string
  invalidUrl: string
  detail: {
    searchPlaceholder: string
    filter: {
      all: string
      concept: string
    }
    subscribeFailed: string
    searchType: {
      titleContent: string
      title: string
      content: string
      author: string
      tag: string
    }
    defaultAdminName: string
  }
  form: {
    createTitle: string
    editTitle: string
    editDesc: string
    name: string
    url: string
    description: string
    iconUrl: string
    iconImage: string
    sortOrder: string
    allowNsfw: string
    allowNsfwDesc: string
    change: string
    save: string
    create: string
    delete: string
    deleteConfirm: string
    successUpdate: string
    successDelete: string
    createFailed: string
    updateFailed: string
    deleteFailed: string
    placeholder: {
      name: string
      url: string
      desc: string
      icon: string
      sortOrder: string
    }
    validation: string
    uploadFailed: string
    cost: string
    currentPoints: string
    insufficientPoints: string
  }
  category: {
    placeholder: {
      new: string
    }
    empty: string
    deleteConfirm: string
    loadFailed: string
    createFailed: string
    deleteFailed: string
    updateFailed: string
    orderFailed: string
    default: string
  }
  postDetail: {
    back: string
    toList: string
    comments: string
    spoilerWarning: string
    spoilerTimer: string
    revealSpoiler: string
    deleteFailed: string
    likeFailed: string
    scrapFailed: string
    loadFailed: string
    reportReasonRequired: string
    reportSuccess: string
    reportFailed: string
  }
  writePost: {
    createTitle: string
    editTitle: string
    selectCategory: string
    noticeDesc: string
    nsfw: string
    nsfwDesc: string
    spoiler: string
    spoilerDesc: string
    placeholder: {
      title: string
      tags: string
    }
    submitting: string
    update: string
    updating: string
    createFailed: string
    updateFailed: string
    loadFailed: string
    validation: string
    tags: string
  }
  tags: {
    placeholder: string
    help: string
    remove: string
  }
}

// 댓글 메시지 타입
export interface CommentMessages {
  title: string
  deleted: string
  reply: string
  empty: string
  loginRequired: string
  deleteFailed: string
  saveFailed: string
  writeReply: string
  writeComment: string
  posting: string
  postComment: string
}

// 알림 메시지 타입
export interface NotificationMessages {
  title: string
  markAllRead: string
  empty: string
}

// 사용자 메시지 타입
export interface UserMessages {
  myPosts: string
  myComments: string
  scraps: string
  points: string
  tabs: {
    settings: string
    points: string
    scraps: string
    recent: string
    reports: string
    subscriptions: string
    blocked: string
  }
  profile: {
    edit: string
    joined: string
    lastLogin: string
    verified: string
    notVerified: string
    personalDetails: string
    displayName: string
    email: string
    choosePhoto: string
    choosePhotoPlaceholder: string
    displayNamePlaceholder: string
  }
  pointsHistory: {
    description: string
    empty: string
    adjustment: string
  }
  scrapList: {
    empty: string
  }
  reportList: {
    empty: string
    pending: string
    processed: string
    rejected: string
    targetType: string
  }
  blockList: {
    title: string
    empty: string
  }
  recentViewed: {
    empty: string
  }
  settings: {
    title: string
    general: string
    generalDesc: string
    theme: string
    language: string
    light: string
    dark: string
    notifications: string
    email: string
    emailDesc: string
    push: string
    pushDesc: string
    save: string
    saving: string
    saved: string
    failed: string
    dangerZone: string
    deleteAccount: string
    deleteAccountDesc: string
    deleteAccountConfirmation: string
    deleteAccountWarning: string
  }
  menu: {
    sendMessage: string
    report: string
    block: string
  }
  message: {
    title: string
    receiver: string
    content: string
    inputContent: string
    sendSuccess: string
    sendFailed: string
    boxTitle: string
    received: string
    sent: string
    empty: string
    from: string
    to: string
    reply: string
    replyTitle: string
  }
  block: {
    confirm: string
    success: string
    failed: string
  }
  subscriptions: {
    title: string
    empty: string
    unsubscribe: string
    unsubscribeConfirm: string
    unsubscribeSuccess: string
    unsubscribeFailed: string
  }
}

// 신고 메시지 타입
export interface ReportMessages {
  title: string
  target: string
  reason: string
  inputReason: string
  reportSuccess: string
  reportFailed: string
  types: {
    post: string
    comment: string
    user: string
  }
}

// 관리자 메시지 타입
export interface AdminMessages {
  layout: {
    title: string
  }
  menu: {
    dashboard: string
    users: string
    admins: string
    boards: string
    reports: string
    security: string
    settings: string
  }
  dashboard: {
    title: string
    totalUsers: string
    pendingReports: string
    blockedIps: string
    viewDetail: string
    recentActivity: string
    noActivity: string
  }
  users: {
    title: string
    description: string
    searchPlaceholder: string
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
    table: {
      reporter: string
      createdAt: string
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
      confirmReject: string
      rejected: string
      rejectFailed: string
    }
  }
  security: {
    title: string
    description: string
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
    }
  }
}

// 전체 메시지 타입
export interface Messages {
  common: CommonMessages
  search: SearchMessages
  layout: LayoutMessages
  auth: AuthMessages
  board: BoardMessages
  comment: CommentMessages
  notification: NotificationMessages
  user: UserMessages
  report: ReportMessages
  admin: AdminMessages
}

// 언어별 메시지 타입
export interface LocaleMessages {
  ko: Messages
  en?: Messages
}

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
  reset: string
  warning: string
  cancel: string
  delete: string
  edit: string
  close: string
  post: string
  comment: string
  user: string
  profile: string
  previous: string
  next: string
  pagination: string
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
    boardManageForbidden: string
    boardWriteForbidden: string
    postEditForbidden: string
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
    fileSizeExceeded: string
    processImageFailed: string
    profileUpdated: string
    loadFailed: string
    processFailed: string
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
  viewDetail: string
  sent: string
  seo: {
    description: string
  }
  paginationSummary: {
    total: string
    itemUnit: string
    page: string
    slash: string
    parenthesized: string
  }
  languages: {
    ko: string
    en: string
  }
}

// 검색 메시지 타입
export interface SearchMessages {
  boards: string
  boardResultsLabel: string
  doSearch: string
  results: string
  query: string
  noResults: string
  noResultsFor: string
  placeholder: string
}

export interface HomeMessages {
  landing: {
    seoDescription: string
    curatedToday: string
    liveNow: string
    online: string
    live: string
    posts: string
    boards: string
    totalPosts: string
    featuredLoading: string
    editorsPicks: string
    editorsPicksEmpty: string
    discover: string
    browseBoards: string
    topBoards: string
    loadingBoards: string
    subscribers: string
    boardsUnavailable: string
    emptyBoards: string
    emptyTitle: string
    emptyDescription: string
    emptyPrimaryAction: string
    emptySecondaryAction: string
    trending: string
    trendingNow: string
    trendingEmpty: string
    trendingPeriods: {
      last24Hours: string
      last7Days: string
      last30Days: string
    }
    liveActivity: string
    liveActivityTitle: string
    liveActivityEmpty: string
    siteStats: string
    statsCards: {
      postsToday: string
      postsTodayDelta: string
      postsTodayDeltaVsYesterday: string
      noComparisonData: string
      activeBoards: string
      activeBoardsMeta: string
      newMembers: string
      newMembersMeta: string
      comments: string
      commentsMeta: string
    }
  }
  card: {
    ariaLabel: string
    video: string
    videoPreview: string
  }
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
  recentBoards: {
    title: string
    empty: string
    clear: string
    removeAria: string
  }
  userMenu: {
    ariaLabel: string
  }
  a11y: {
    homeLink: string
    openNotifications: string
    goToNotifications: string
  }
  topNav: {
    home: string
    subscribedBoards: string
    subscribedShort: string
    boards: string
  }
  mobileNav: {
    ariaLabel: string
    home: string
    boards: string
    alerts: string
    my: string
    createPost: string
    write: string
    chooseBoard: string
    closeSheet: string
    boardOptionsError: string
    loadingBoards: string
    browseAllBoards: string
  }
  shortcuts: {
    title: string
    global: string
    dropdown: string
    boardList: string
    postDetail: string
    writeEdit: string
    help: string
    home: string
    search: string
    darkMode: string
    allBoards: string
    allBoardsPage: string
    subscribedBoards: string
    myPage: string
    notifications: string
    selectItem: string
    closeDropdown: string
    navigate: string
    select: string
    nextPage: string
    prevPage: string
    lastPage: string
    firstPage: string
    write: string
    subscribe: string
    focusSearch: string
    comments: string
    toList: string
    like: string
    scrap: string
    copyUrl: string
    share: string
    edit: string
    submit: string
    cancel: string
    logout: string
    mypageTabs: string
    nextTab: string
    prevTab: string
    openAnytime: string
  }
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
  codeInvalid: string
  yourIdIs: string
  resetPassword: string
  newPassword: string
  newPasswordConfirm: string
  passwordResetSuccess: string
  findIdPassword: string
  forgotPassword: string
  forgotPasswordDescription: string
  sendResetLink: string
  resetLinkSent: string
  invalidResetLink: string
  resetPasswordTitle: string
  userNotFoundByLoginId: string
  userDeleted: string
  reregisterGuidance: string
  placeholders: {
    loginId: string
    password: string
    email: string
    displayName: string
    newEmail: string
  }
  email: string
  login: string
  loginSuccess: string
  oauth: {
    googleLogin: string
    discordLogin: string
    githubLogin: string
  }
  emailNotVerified: string
  passwordMismatch: string
  verificationFailed: string
  verificationRequired: string
  sendCodeFailed: string
  emailRequired: string
  validation: {
    passwordStrength: string
    loginIdFormat: string
    emailFormat: string
    displayNameLength: string
  }
}

// 스페이스 메시지 타입
export interface BoardMessages {
  list: {
    subscribed: string
    subscribedShort: string
    all: string
    allShort: string
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
  inquiryStatus: {
    answered: string
    pending: string
  }
  createBoard: string
  loadFailed: string
  invalidUrl: string
  seo: {
    allBoardsDescription: string
    spaceTitleFallback: string
    spaceDescriptionFallback: string
    postTitleFallback: string
    postDescriptionFallback: string
  }
  detail: {
    searchPlaceholder: string
    filterLabel: string
    searchScopeLabel: string
    clearSearch: string
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
    notices: {
      title: string
      more: string
      collapse: string
    }
    defaultAdminName: string
    restricted: string
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
    isPublic: string
    isPublicDesc: string
    agentUseYn: string
    agentUseYnDesc: string
    guidePrompt: string
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
      guidePrompt: string
    }
    validation: string
    uploadFailed: string
    invalidIconType: string
    iconTooLarge: string
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
    add: string
    save: string
    cancel: string
    edit: string
    delete: string
  }
  postDetail: {
    back: string
    toList: string
    comments: string
    focusComposer: string
    quickActions: string
    tableOfContents: string
    scrollTop: string
    tags: string
    reactions: string
    bookmark: string
    moreActions: string
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
    restricted: string
  }
  inquiryWrite: {
    title: string
    description: string
    preparing: string
    leaveConfirm: string
    createTitle: string
    createSuccess: string
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
    secret: string
    secretDesc: string
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
    discardEditConfirm: string
    discardCreateConfirm: string
    leaveConfirm: string
    tags: string
    viewHtmlSource: string
    htmlSourceTitle: string
    htmlSourcePlaceholder: string
    visualMode: string
    videoUrlRequired: string
    invalidVideoUrl: string
    linkUrlPrompt: string
    invalidLinkUrl: string
    linkDisplayText: string
    linkInsert: string
    linkRemove: string
    fontSize: string
    lineHeight: string
    alignLeft: string
    alignCenter: string
    alignRight: string
    alignJustify: string
    tableRows: string
    tableCols: string
    tableHeaderRow: string
    tableInsert: string
    draftStatus: {
      saving: string
      savedAt: string
      ready: string
      restoredLocal: string
      restoredServer: string
      saved: string
      cleanupFailed: string
      clearLocalBackup: string
    }
    actions: {
      preview: string
      saveDraft: string
      saveNow: string
      publish: string
    }
    composeMode: {
      create: string
      edit: string
    }
    sections: {
      editor: string
      metadata: string
      status: string
      postSettings: string
      draftState: string
    }
    preview: {
      title: string
      untitledPost: string
      emptyContent: string
    }
    video: {
      dialogLabel: string
      inputLabel: string
      placeholder: string
      help: string
    }
    upload: {
      uploading: string
      retry: string
      cancel: string
      dismiss: string
    }
    dropImageHint: string
    imageAlt: {
      title: string
      label: string
      placeholder: string
      help: string
      apply: string
      clear: string
    }
    colorLabels: {
      black: string
      gray: string
      muted: string
      lightGray: string
      red: string
      orange: string
      yellow: string
      green: string
      blue: string
      purple: string
      pink: string
      teal: string
      white: string
      dark: string
      slate: string
      paleGray: string
    }
    toolbar: {
      bold: string
      italic: string
      underline: string
      strikethrough: string
      link: string
      image: string
      video: string
      bulletList: string
      orderedList: string
      emoticon: string
      slashMenu: string
      more: string
      insertBlock: string
      quote: string
      list: string
      divider: string
      heading: string
      codeBlock: string
      advanced: string
      formattingTools: string
      textColor: string
      defaultColor: string
      customColor: string
      linkDialog: string
      tableDialog: string
    }
    shortcuts: {
      saveDraft: string
      publish: string
    }
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
  blockedAuthor: string
  blockedContent: string
  reply: string
  agentBadge: string
  viewReplies: string
  hideReplies: string
  loadRepliesFailed: string
  loadFailed: string
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
  markAllReadShort?: string
  empty: string
  sourceTypes: {
    post: string
    comment: string
  }
}

// 사용자 메시지 타입
export interface UserMessages {
  deletedUser: string
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
    currentPhotoAlt: string
    choosePhotoPlaceholder: string
    displayNamePlaceholder: string
    agentTitle: string
    agentDescription: string
    agentCode: string
    agentPlaceholder: string
    agentRegister: string
    agentSuspend: string
    agentSuspendConfirmMessage: string
    agentSuspendConfirmTitle: string
    agentSuspendSuccess: string
    agentSuspendFailed: string
    agentEmpty: string
    agentCodeRequired: string
    agentClaimSuccess: string
    agentClaimFailed: string
    agentEmailVerificationRequired: string
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
  comments: {
    deletedPost: string
  }
  blockList: {
    title: string
    empty: string
  }
  selectModal: {
    title: string
    multiMode: string
    singleMode: string
    selectedCount: string
    selectedEmpty: string
    selectUser: string
    currentManager: string
  }
  dashboard: {
    agentStatus: {
      active: string
      unregistered: string
      pending: string
    }
  }
  recentViewed: {
    empty: string
  }
  inquiryDetail: {
    title: string
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
    like: string
    likeDesc: string
    comment: string
    commentDesc: string
    reply: string
    replyDesc: string
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
    blockedByUser: string
    boxTitle: string
    received: string
    sent: string
    empty: string
    from: string
    to: string
    reply: string
    replyTitle: string
    selectMessage: string
    openMessage: string
  }
  block: {
    confirm: string
    success: string
    failed: string
    unblock: string
    blockButton: string
    unblockConfirm: string
    blockConfirm: string
    processFailed: string
  }
  subscriptions: {
      title: string
      empty: string
      unsubscribe: string
      unsubscribeConfirm: string
      unsubscribeSuccess: string
      unsubscribeFailed: string
      unavailableBoard: string
      unavailableBoardDescription: string
    }
  sanctioned: string
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

// 노비콘(이모티콘) 메시지 타입
export interface EmoticonMessages {
  title: string
  purchase: {
    success: string
    failed: string
    confirm: string
    purchasing: string
    button: {
      loginRequired: string
      purchased: string
      myEmoticon: string
      buyWithPrice: string
    }
  }
  edit: {
    noPermission: string
    updated: string
    failed: string
  }
  visibility: {
    hide: string
    show: string
    hideConfirm: string
    showConfirm: string
    hidden: string
    hiddenSuccess: string
    showSuccess: string
  }
  register: {
    title: string
    description: string
    created: string
    failed: string
  }
  list: {
    description: string
    popular: string
    popularEmpty: string
    all: string
    itemCount: string
    empty: string
    salesCount: string
    register: string
    period: {
      daily: string
      weekly: string
      monthly: string
    }
    sort: {
      latest: string
      oldest: string
      popular: string
    }
    searchType: {
      all: string
      name: string
      creator: string
      tag: string
    }
  }
  detail: {
    backToList: string
    loadFailed: string
    backToListFull: string
    creator: string
    createdAt: string
    purchaseCount: string
    imageList: string
    imageEmpty: string
    tags: string
  }
  form: {
    editTitle: string
    editDescription: string
    back: string
    onSale: string
    thumbnailImage: string
    thumbnailHelp: string
    thumbnailPreview: string
    changeImageTitle: string
    name: string
    namePlaceholder: string
    image: string
    imageHelp: string
    chooseImage: string
    imageAlt: string
    newImageAlt: string
    deletePending: string
    addPending: string
    updateSubmit: string
    updatingSubmit: string
    createSubmit: string
    creatingSubmit: string
    count: string
  }
  picker: {
    detailLoadFailed: string
    listLoadFailed: string
    backToListAria: string
    closeAria: string
    imageSelectAria: string
    searchAria: string
    availableEmpty: string
  }
  tag: {
    label: string
    help: string
    placeholder: string
    count: string
  }
  search: {
    typeLabel: string
    clear: string
  }
  validation: {
    imageOnly: string
    imageLoadFailed: string
    imageSizeExceeded: string
    imageSizeExceededNamed: string
    fileSizeExceeded: string
    fileSizeExceededNamed: string
    notImage: string
    loadFailedNamed: string
    maxImages: string
    maxTags: string
  }
  upload: {
    progress: string
  }
}

// 관리자 메시지 타입
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
      SANCTIONED: string
      INACTIVE: string
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
export interface Messages {
  common: CommonMessages
  search: SearchMessages
  home: HomeMessages
  layout: LayoutMessages
  auth: AuthMessages
  board: BoardMessages
  comment: CommentMessages
  notification: NotificationMessages
  user: UserMessages
  report: ReportMessages
  emoticon: EmoticonMessages
  admin: AdminMessages
}

// 언어별 메시지 타입
export interface LocaleMessages {
  ko: Messages
  en?: Messages
}

/**
 * Locale message type definitions.
 */

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
  oauthTicket: {
    loadFailed: string
    cancelFailed: string
  }
  emailNotVerified: string
  passwordMismatch: string
  verificationFailed: string
  verificationRequired: string
  sendCodeFailed: string
  emailRequired: string
  passwordRequired: string
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
    searchLabel: string
    searchPlaceholder: string
    statusFilter: string
    sortLabel: string
    filterAll: string
    filterSubscribed: string
    filterPublic: string
    filterPrivate: string
    sortDefault: string
    sortName: string
    sortPopular: string
    sortPosts: string
    resultCount: string
    densityLabel: string
    densityDefault: string
    densityCompact: string
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
    emptyDescription: string
    emptySearchDescription: string
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
    visibility: string
    visibilityPublic: string
    visibilityPublicDesc: string
    visibilityUnlisted: string
    visibilityUnlistedDesc: string
    visibilityPrivate: string
    visibilityPrivateDesc: string
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
    leaveConfirm: string
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
    createCostLoading: string
    createCostUnavailable: string
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
    openImage: string
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
    blinded: string
    blind: string
    unblind: string
    managerActionSuccess: string
    relatedKicker: string
    relatedTitle: string
    seriesTitle: string
    seriesMeta: string
    previousInSeries: string
    nextInSeries: string
    editedCount: string
    versionHistory: string
    versionEmpty: string
    versionLoadFailed: string
    versionActions: {
      CREATE: string
      MODIFY: string
      DELETE: string
    }
    poll: {
      title: string
      multiple: string
      anonymous: string
      closed: string
      loginRequired: string
      noVotes: string
      voteCount: string
      vote: string
      revote: string
      cancelVote: string
    }
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
    editScheduledTitle: string
    scheduledNotEditable: string
    selectCategory: string
    noticeDesc: string
    nsfw: string
    nsfwDesc: string
    spoiler: string
    spoilerDesc: string
    secret: string
    secretDesc: string
    series: string
    noSeries: string
    newSeries: string
    newSeriesPlaceholder: string
    createSeries: string
    createSeriesSuccess: string
    createSeriesFailed: string
    poll: {
      add: string
      remove: string
      readOnlyNotice: string
      title: string
      question: string
      questionPlaceholder: string
      option: string
      optionPlaceholder: string
      addOption: string
      multiple: string
      anonymous: string
      closesAt: string
      validation: {
        questionRequired: string
        questionTooLong: string
        optionRequired: string
        optionCount: string
        optionTooLong: string
        closesAtFuture: string
        closesAtAfterSchedule: string
      }
    }
    placeholder: {
      title: string
      tags: string
    }
    submitting: string
    scheduling: string
    scheduleAt: string
    scheduleHelp: string
    scheduleSuccess: string
    scheduleUpdateSuccess: string
    scheduleChangedDuringSave: string
    updateSchedule: string
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
      restoring: string
      savedAt: string
      savedBrowserAt: string
      ready: string
      failed: string
      retryScheduled: string
      retryExhausted: string
      retryNow: string
      localStorageFailed: string
      restoreFailed: string
      constraintsExceeded: string
      conflict: string
      protected: string
      openScheduledPosts: string
      deleted: string
      saveAsNew: string
      discardLocal: string
      savedAsNew: string
      discarded: string
      reloadServer: string
      keepLocal: string
      retryRestore: string
      restoredLocal: string
      restoredServer: string
      referencesReset: string
      limitEvicted: string
      saved: string
      savedBrowser: string
      cleanupFailed: string
      confirmLogoutWithUnsynced: string
    }
    actions: {
      preview: string
      saveDraft: string
      saveNow: string
      publish: string
      schedule: string
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
    codeBlock: {
      copy: string
      copyAriaLabel: string
      copyDone: string
      copyFailed: string
      language: string
      auto: string
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
      poll: string
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
  blinded: string
  reply: string
  like: string
  unlike: string
  agentBadge: string
  emoticonAlt: string
  viewReplies: string
  hideReplies: string
  loadRepliesFailed: string
  loadFailed: string
  loadMore: string
  newArrivals: string
  readUntilHere: string
  sort: {
    label: string
    oldest: string
    newest: string
    likes: string
  }
  best: {
    title: string
  }
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
  unreadNotifications: string
  markAllRead: string
  markAllReadShort?: string
  empty: string
  emptyDescription: string
  groupedCount: string
  actors: {
    system: string
    unknown: string
  }
  comment: {
    created: string
    liked: string
  }
  reply: { created: string }
  post: { liked: string }
  badge: { awarded: string }
  scheduled: {
    published: string
    failed: string
  }
  message: { received: string }
  mention: { created: string }
  keyword: { matched: string }
  sourceTypes: {
    post: string
    comment: string
    message: string
  }
  types: {
    default: string
    like: string
    comment: string
    reply: string
    mention: string
    message: string
    system: string
    sanction: string
    keyword: string
    badge: string
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
    drafts: string
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
    removePhoto: string
    currentPhotoAlt: string
    choosePhotoPlaceholder: string
    profileImageFirstFree: string
    profileImageCost: string
    profileImageCostConfirm: string
    profileImageCostSpent: string
    profileImageInsufficientPoints: string
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
  publicProfile: {
    tabs: string
    overview: string
    posts: string
    comments: string
    postCount: string
    commentCount: string
    overviewDescription: string
    emptyComments: string
    badges: string
    badgesEmpty: string
    setRepresentative: string
    unsetRepresentative: string
    representativeUpdated: string
    representativeUpdateFailed: string
  }
  pointsHistory: {
    typeFilter: string
    types: {
      ALL: string
      EARN: string
      SPEND: string
      EXPIRE: string
      PENALTY: string
      REWARD_REVERSAL: string
    }
    description: string
    empty: string
    adjustment: string
    transaction: {
      earned: string
      spent: string
      expired: string
    }
  }
  scrapList: {
    empty: string
    allFolder: string
    newFolder: string
    search: string
    searchButton: string
    deleteFolder: string
    deleteConfirmTitle: string
    deleteConfirmMessage: string
    editFolder: string
    renameFolder: string
    saveFolder: string
    cancelEditFolder: string
    folderActionFailed: string
    selectScrap: string
    destinationFolder: string
    unfiled: string
    move: string
    moveFailed: string
  }
  draftList: {
    empty: string
    untitled: string
    continue: string
    editDraft: string
    deleteConfirm: string
    deleted: string
    deleteOutdated: string
    deleteFailed: string
    retentionNotice: string
    expiresAt: string
    scheduledTitle: string
    scheduledEmpty: string
    cancelScheduledConfirm: string
    cancelScheduledSuccess: string
    cancelScheduledFailed: string
    openPublished: string
    editScheduled: string
    scheduledStatus: {
      SCHEDULED: string
      PUBLISHING: string
      PUBLISHED: string
      CANCELED: string
      FAILED: string
    }
    scheduledFailure: {
      businessRejected: string
      internalError: string
      unknown: string
    }
  }
  reportList: {
    empty: string
    pending: string
    processed: string
    rejected: string
    targetType: string
    target: string
    reasonType: string
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
    deselectUser: string
    currentManager: string
  }
  dashboard: {
    summaryTitle: string
    contentSummary: string
    activitySummary: string
    communicationSummary: string
    accountSummary: string
    postsAndComments: string
    dayStreak: string
    unreadNotifications: string
    manageSettings: string
    agentStatus: {
      active: string
      unregistered: string
      pending: string
    }
  }
  navigation: {
    ariaLabel: string
    content: string
    activity: string
    communication: string
    account: string
    overview: string
    attendance: string
  }
  attendance: {
    title: string
    description: string
    previousMonth: string
    nextMonth: string
    checkedDay: string
    today: string
  }
  badges: {
    title: string
    description: string
    total: string
    acquired: string
    locked: string
    acquiredEmpty: string
    lockedEmpty: string
    acquiredAt: string
    representative: string
    setRepresentative: string
    unsetRepresentative: string
    representativeUpdated: string
    representativeUpdateFailed: string
    catalog: Record<string, {
      name: string
      description: string
    }>
  }
  badgeAward: {
    title: string
    setRepresentative: string
  }
  recentViewed: {
    empty: string
  }
  inquiryDetail: {
    title: string
  }
  settings: {
    leaveConfirm: string
    title: string
    sectionNavigation: string
    general: string
    generalDesc: string
    theme: string
    language: string
    timezone: string
    timezoneAuto: string
    timezoneHelp: string
    light: string
    dark: string
    notifications: string
    like: string
    likeDesc: string
    comment: string
    commentDesc: string
    reply: string
    replyDesc: string
    notificationTypes: Record<string, {
      label: string
      description: string
    }>
    email: string
    emailDesc: string
    push: string
    pushDesc: string
    browserPush: string
    browserPushDesc: string
    pushUnsupported: string
    pushUnavailable: string
    pushLoadFailed: string
    pushDenied: string
    pushEnabled: string
    pushDisabled: string
    pushServerOnly: string
    pushBrowserOnly: string
    pushEnable: string
    pushRepair: string
    pushDisable: string
    pushEnableSuccess: string
    pushEnableFailed: string
    pushDisableSuccess: string
    pushDisableFailed: string
    keywordSubscriptions: string
    keywordSubscriptionsDesc: string
    keywordNotificationsDisabled: string
    keywordInput: string
    keywordPlaceholder: string
    keywordAdd: string
    keywordEmpty: string
    keywordList: string
    keywordRemove: string
    keywordRequired: string
    keywordTooLong: string
    keywordDuplicate: string
    keywordAdded: string
    keywordAddFailed: string
    keywordRemoved: string
    keywordRemoveFailed: string
    sessions: {
      title: string
      description: string
      logoutOthers: string
      current: string
      empty: string
      showHistory: string
      hideHistory: string
      historyEmpty: string
      confirmTitle: string
      confirmCurrent: string
      confirmOne: string
      confirmOthers: string
      revoked: string
      revokedOthers: string
    }
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
    actions: string
    viewProfile: string
    sendMessage: string
    report: string
    block: string
  }
  message: {
    title: string
    conversationTitle: string
    receiver: string
    content: string
    inputContent: string
    contentTooLong: string
    contentLength: string
    sendSuccess: string
    sendFailed: string
    blockedByUser: string
    boxTitle: string
    conversations: string
    conversation: string
    received: string
    sent: string
    empty: string
    from: string
    to: string
    reply: string
    replyTitle: string
    selectMessage: string
    openMessage: string
    conversationContext: string
    me: string
    contextEmpty: string
    loadOlder: string
    scrollForOlder: string
    newMessagesPending: string
    detailLoadFailed: string
    conversationLoadFailed: string
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
      reorder: string
      moveUp: string
      moveDown: string
    }
}

// 신고 메시지 타입
export interface ReportMessages {
  title: string
  target: string
  reason: string
  reasonType: string
  inputReason: string
  reasonTooLong: string
  reportSuccess: string
  reportFailed: string
  reasonTypes: {
    SPAM: string
    ABUSE: string
    ADULT: string
    ETC: string
  }
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
      unavailable: string
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

/**
 * Locale message type definitions.
 */

export interface TermsOfServiceMessages {
  title: string
  lastRevised: string
  article1: {
    title: string
    body: string
  }
  article2: {
    title: string
    item1: string
    item2: string
  }
  article3: {
    title: string
    intro: string
    communitySpace: string
    postManagement: string
    commentsAndLikes: string
    userCommunication: string
    item2: string
  }
  article4: {
    title: string
    item1: string
    intro: string
    anotherIdentity: string
    falseInformation: string
    publicOrder: string
  }
  article5: {
    title: string
    intro: string
    identityTheft: string
    alterInformation: string
    unauthorizedInformation: string
    intellectualProperty: string
    reputationOrOperations: string
    improperContent: string
  }
  article6: {
    title: string
    body: string
  }
  article7: {
    title: string
    item1: string
    item2: string
    item3: string
    item4: string
  }
  article8: {
    title: string
    item1: string
    item2: string
  }
}

export interface PrivacyPolicyGroup {
  title: string
  items: string[]
}

export interface PrivacyPolicySection {
  title: string
  paragraphs: string[]
  groups?: PrivacyPolicyGroup[]
}

export interface PrivacyPolicyMessages {
  title: string
  lastRevised: string
  sections: PrivacyPolicySection[]
}

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
    contact: string
    termsOfService: string
    privacyPolicy: string
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
    reportAccepted: string
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
  pwa: {
    updateReady: string
    updateDeferred: string
    offlineReady: string
    pullToRefresh: {
      pull: string
      release: string
      refreshing: string
      complete: string
    }
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
  pointEarned: string
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
  moveUp: string
  moveDown: string
  moved: string
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
  terms: TermsOfServiceMessages
}

// 검색 메시지 타입
export interface SearchMessages {
  resultSummary: string
  boards: string
  boardResultsLabel: string
  doSearch: string
  results: string
  query: string
  noResults: string
  noResultsFor: string
  noResultsSuggestion: string
  placeholder: string
  popularKeywords: string
  recentKeywords: string
  semanticResults: string
  semanticRelated: string
  semanticEmpty: string
  semanticSource: string
  authorFilter: string
  scopeFilter: string
  scopeTitleContent: string
  scopeTitle: string
  scopeContent: string
  scopeAuthor: string
  scopeFilterChip: string
  periodFilter: string
  periodAll: string
  periodToday: string
  periodWeek: string
  periodMonth: string
  periodCustom: string
  fromDate: string
  toDate: string
  applyFilters: string
  filterToggle: string
  activeFilters: string
  clearFilter: string
  authorFilterChip: string
  periodFilterChip: string
  dateRangeFilterChip: string
  keywordFilterNotice: string
  deleteRecent: string
  clearRecent: string
  searchByKeyword: string
  relatedTags: string
  tagPostCount: string
  tagLoadFailed: string
  tagEmpty: string
  tagSeoTitle: string
  tagSeoDescription: string
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
  views: {
    label: string
    recommended: string
    personal: string
  }
  personal: {
    kicker: string
    title: string
    description: string
    loading: string
    loadFailed: string
    emptyTitle: string
    emptyDescription: string
    browseSpaces: string
    manageSubscriptions: string
    loadMore: string
    loadingMore: string
  }
  attendance: {
    title: string
    streak: string
    checkIn: string
    done: string
    earned: string
    alreadyCheckedIn: string
    milestone: string
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
    footerNavigation: string
    userNavigation: string
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

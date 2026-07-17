import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import type {
  KeywordSubscriptionPayload,
  KeywordSubscriptionResponse,
  NotificationSettingsBulkPayload,
  NotificationSettingsPayload
} from '@/api/user'
import type { LoginHistory, PageResponse, UserSession, UserSettings as UserSettingsData } from '@/types'
import UserSettings from '../UserSettings.vue'
import { BaseButtonStub, flushAll, identityT } from '@/test/vue-test-helpers'

type UserSettingsFixture = Omit<UserSettingsData, 'language'> & { language: string }
type ThemeStoreMock = {
  readonly isDark: boolean
  setTheme: (theme: UserSettingsData['theme']) => void
}
type UserComposableMock = {
  useUserSettings: () => QueryMock<typeof settingsData>
  useNotificationSettings: () => QueryMock<typeof notificationData>
  useMySessions: () => QueryMock<typeof sessionsData>
  useMyLoginHistory: () => QueryMock<typeof loginHistoryData>
  useKeywordSubscriptions: () => QueryMock<typeof keywordData>
  useUpdateUserSettings: () => {
    mutateAsync: (payload: Partial<UserSettingsFixture>) => Promise<void>
    isPending: typeof isUpdatingSettings
  }
  useUpdateNotificationSettings: () => {
    mutateAsync: (payload: NotificationSettingsBulkPayload) => Promise<void>
    isPending: typeof isUpdatingNotifications
  }
  useCreateKeywordSubscription: () => {
    mutateAsync: (payload: KeywordSubscriptionPayload) => Promise<void>
    isPending: typeof isCreatingKeyword
  }
  useDeleteKeywordSubscription: () => {
    mutateAsync: (payload: KeywordSubscriptionPayload) => Promise<void>
    isPending: typeof isDeletingKeyword
  }
  useRevokeMySession: () => {
    mutateAsync: (sessionId: number) => Promise<void>
    isPending: typeof isRevokingSession
  }
  useRevokeOtherSessions: () => {
    mutateAsync: () => Promise<void>
    isPending: typeof isRevokingOtherSessions
  }
}
type QueryMock<T> = { data: T; isLoading: { value: boolean }; isError: { value: boolean }; refetch: () => Promise<void> }

const useUserMock = vi.hoisted(() => vi.fn<() => UserComposableMock>())
const useThemeStoreMock = vi.hoisted(() => vi.fn<() => ThemeStoreMock>())
const authStoreMock = vi.hoisted(() => ({
  sessionGeneration: 1,
  accessToken: 'token-a' as string | null,
  user: { userId: 1 } as { userId: number } | null,
  clearSessionState: vi.fn(),
}))
const toastStoreMock = vi.hoisted(() => ({
  addToast: vi.fn(),
}))
const confirmMock = vi.hoisted(() => vi.fn().mockResolvedValue(true))
const routeMock = vi.hoisted(() => ({ hash: '' }))
const routerReplaceMock = vi.hoisted(() => vi.fn())
const setAppLocaleMock = vi.hoisted(() => vi.fn().mockResolvedValue(true))
const pushNotificationMock = vi.hoisted(() => ({
  enabled: { value: true },
  supported: { value: true },
  permission: { value: 'default' as NotificationPermission | 'unsupported' },
  isLoading: { value: false },
  isError: { value: false },
  error: { value: null as Error | null },
  isEnabling: { value: false },
  isDisabling: { value: false },
  enablePush: vi.fn(),
  disablePush: vi.fn(),
  refetch: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: identityT, locale: { value: 'ko' } }),
}))

vi.mock('@/i18n', () => ({
  setAppLocale: setAppLocaleMock,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => ({ replace: routerReplaceMock }),
}))

vi.mock('@/composables/useUser', () => ({
  useUser: useUserMock,
}))

vi.mock('@/stores/theme', () => ({
  useThemeStore: useThemeStoreMock,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStoreMock,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => toastStoreMock,
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: confirmMock }),
}))

vi.mock('@/features/notifications/usePushNotifications', () => ({
  usePushNotifications: () => pushNotificationMock,
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
  },
}))

const BaseSelectStub = defineComponent({
  name: 'BaseSelect',
  props: {
    modelValue: { type: [String, Number], default: '' },
    label: { type: String, default: '' },
    inputClass: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () =>
      h('label', [
        h('span', props.label),
        h(
          'select',
          {
            value: props.modelValue as string | number,
            class: props.inputClass,
            onChange: (event: Event) =>
              emit('update:modelValue', (event.target as HTMLSelectElement).value),
          },
          slots.default?.(),
        ),
      ])
  },
})

const BaseCheckboxStub = defineComponent({
  name: 'BaseCheckbox',
  props: {
    id: { type: String, default: '' },
    modelValue: { type: Boolean, default: false },
    label: { type: String, default: '' },
    description: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('label', { for: props.id }, [
        h('input', {
          id: props.id,
          type: 'checkbox',
          checked: props.modelValue,
          onChange: (event: Event) =>
            emit('update:modelValue', (event.target as HTMLInputElement).checked),
        }),
        h('span', props.label),
        h('p', props.description),
      ])
  },
})

const BaseSpinnerStub = defineComponent({
  name: 'BaseSpinner',
  setup() {
    return () => h('div', 'loading')
  },
})

const themeIsDark = ref(false)
const settingsData = ref<UserSettingsFixture>({
  theme: 'LIGHT' as const,
  language: 'ko',
  timezone: 'Asia/Seoul',
  hideNsfw: true,
  pushEnabled: false,
})
const notificationData = ref<NotificationSettingsPayload[]>([
  { notificationType: 'LIKE' as const, isEnabled: false },
  { notificationType: 'COMMENT' as const, isEnabled: true },
])
const keywordData = ref<KeywordSubscriptionResponse[]>([])
const sessionsData = ref<UserSession[]>([])
const emptyLoginHistoryPage = (): PageResponse<LoginHistory> => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 10,
  number: 0,
  first: true,
  last: true,
  empty: true,
})
const loginHistoryData = ref<PageResponse<LoginHistory>>(emptyLoginHistoryPage())
const isSettingsLoading = ref(false)
const isNotifLoading = ref(false)
const isSessionsLoading = ref(false)
const isLoginHistoryLoading = ref(false)
const isKeywordLoading = ref(false)
const isSettingsError = ref(false)
const isNotifError = ref(false)
const isSessionsError = ref(false)
const isLoginHistoryError = ref(false)
const isKeywordError = ref(false)
const isUpdatingSettings = ref(false)
const isUpdatingNotifications = ref(false)
const isCreatingKeyword = ref(false)
const isDeletingKeyword = ref(false)
const isRevokingSession = ref(false)
const isRevokingOtherSessions = ref(false)
const updateSettings = vi.fn()
const updateNotificationSettings = vi.fn()
const createKeywordSubscription = vi.fn()
const deleteKeywordSubscription = vi.fn()
const revokeSession = vi.fn()
const revokeOtherSessions = vi.fn()
const setTheme = vi.fn()
const refetchSettings = vi.fn().mockResolvedValue(undefined)
const refetchNotifications = vi.fn().mockResolvedValue(undefined)
const refetchSessions = vi.fn().mockResolvedValue(undefined)
const refetchLoginHistory = vi.fn().mockResolvedValue(undefined)
const refetchKeywords = vi.fn().mockResolvedValue(undefined)
const mountedWrappers: Array<ReturnType<typeof mount>> = []

const getSaveButtons = (wrapper: ReturnType<typeof mount>) => {
  const buttons = wrapper.findAll('button').filter((button) => button.text() === 'user.settings.save')
  expect(buttons).toHaveLength(2)
  return {
    generalSaveButton: buttons[0],
    notificationSaveButton: buttons[1],
  }
}

const mountUserSettings = () => {
  const wrapper = mount(UserSettings, {
    global: {
      mocks: {
        $t: identityT,
      },
      stubs: {
        BaseSelect: BaseSelectStub,
        BaseCheckbox: BaseCheckboxStub,
        BaseButton: BaseButtonStub,
        BaseSpinner: BaseSpinnerStub,
        Settings: true,
        Monitor: true,
      },
    },
  })

  mountedWrappers.push(wrapper)
  return wrapper
}

describe('UserSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAppLocaleMock.mockResolvedValue(true)
    routeMock.hash = ''

    themeIsDark.value = false
    settingsData.value = {
      theme: 'LIGHT',
      language: 'ko',
      timezone: 'Asia/Seoul',
      hideNsfw: true,
      pushEnabled: false,
    }
    notificationData.value = [
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: true },
    ]
    keywordData.value = []
    sessionsData.value = []
    loginHistoryData.value = emptyLoginHistoryPage()
    isSettingsLoading.value = false
    isNotifLoading.value = false
    isSessionsLoading.value = false
    isLoginHistoryLoading.value = false
    isKeywordLoading.value = false
    isSettingsError.value = false
    isNotifError.value = false
    isSessionsError.value = false
    isLoginHistoryError.value = false
    isKeywordError.value = false
    isUpdatingSettings.value = false
    isUpdatingNotifications.value = false
    isCreatingKeyword.value = false
    isDeletingKeyword.value = false
    isRevokingSession.value = false
    isRevokingOtherSessions.value = false
    updateSettings.mockResolvedValue(undefined)
    updateNotificationSettings.mockResolvedValue(undefined)
    createKeywordSubscription.mockResolvedValue(undefined)
    deleteKeywordSubscription.mockResolvedValue(undefined)
    revokeSession.mockResolvedValue(undefined)
    revokeOtherSessions.mockResolvedValue(undefined)
    authStoreMock.clearSessionState.mockReset()
    authStoreMock.sessionGeneration = 1
    authStoreMock.accessToken = 'token-a'
    authStoreMock.user = { userId: 1 }
    toastStoreMock.addToast.mockReset()
    confirmMock.mockResolvedValue(true)
    pushNotificationMock.enabled.value = true
    pushNotificationMock.supported.value = true
    pushNotificationMock.permission.value = 'default'
    pushNotificationMock.isLoading.value = false
    pushNotificationMock.isError.value = false
    pushNotificationMock.error.value = null
    pushNotificationMock.isEnabling.value = false
    pushNotificationMock.isDisabling.value = false
    pushNotificationMock.enablePush.mockResolvedValue(undefined)
    pushNotificationMock.disablePush.mockResolvedValue(undefined)
    pushNotificationMock.refetch.mockResolvedValue(undefined)

    useThemeStoreMock.mockReturnValue({
      get isDark() {
        return themeIsDark.value
      },
      setTheme,
    })

    useUserMock.mockReturnValue({
      useUserSettings: () => ({ data: settingsData, isLoading: isSettingsLoading, isError: isSettingsError, refetch: refetchSettings }),
      useNotificationSettings: () => ({ data: notificationData, isLoading: isNotifLoading, isError: isNotifError, refetch: refetchNotifications }),
      useMySessions: () => ({ data: sessionsData, isLoading: isSessionsLoading, isError: isSessionsError, refetch: refetchSessions }),
      useMyLoginHistory: () => ({ data: loginHistoryData, isLoading: isLoginHistoryLoading, isError: isLoginHistoryError, refetch: refetchLoginHistory }),
      useKeywordSubscriptions: () => ({ data: keywordData, isLoading: isKeywordLoading, isError: isKeywordError, refetch: refetchKeywords }),
      useUpdateUserSettings: () => ({
        mutateAsync: updateSettings,
        isPending: isUpdatingSettings,
      }),
      useUpdateNotificationSettings: () => ({
        mutateAsync: updateNotificationSettings,
        isPending: isUpdatingNotifications,
      }),
      useCreateKeywordSubscription: () => ({
        mutateAsync: createKeywordSubscription,
        isPending: isCreatingKeyword,
      }),
      useDeleteKeywordSubscription: () => ({
        mutateAsync: deleteKeywordSubscription,
        isPending: isDeletingKeyword,
      }),
      useRevokeMySession: () => ({
        mutateAsync: revokeSession,
        isPending: isRevokingSession,
      }),
      useRevokeOtherSessions: () => ({
        mutateAsync: revokeOtherSessions,
        isPending: isRevokingOtherSessions,
      }),
    })
  })

  afterEach(() => {
    while (mountedWrappers.length > 0) {
      mountedWrappers.pop()?.unmount()
    }
  })

  it('renders notification settings from locale keys and query data', async () => {
    const wrapper = mountUserSettings()
    await nextTick()

    expect(wrapper.text()).toContain('user.settings.notifications')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.LIKE.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.COMMENT.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.REPLY.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.MENTION.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.MESSAGE.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.SYSTEM.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.SANCTION.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.KEYWORD.label')
    expect(wrapper.text()).toContain('user.settings.notificationTypes.BADGE.label')

    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('LIGHT')
    expect((wrapper.findAll('select')[1].element as HTMLSelectElement).value).toBe('ko')
    expect((wrapper.get('#notification-like').element as HTMLInputElement).checked).toBe(false)
    expect((wrapper.get('#notification-comment').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.get('#notification-reply').element as HTMLInputElement).checked).toBe(true)
  })

  it('connects each settings tab to its tab panel', () => {
    const wrapper = mountUserSettings()
    const tabs = wrapper.findAll('[role="tab"]')
    const panels = wrapper.findAll('[role="tabpanel"]')

    expect(tabs.map((tab) => tab.attributes('aria-controls'))).toEqual(['general', 'notifications', 'security'])
    expect(panels.map((panel) => panel.attributes('aria-labelledby'))).toEqual([
      'settings-general-tab',
      'settings-notifications-tab',
      'settings-security-tab',
    ])
  })

  it('exposes login history as an accessible disclosure with an empty state', async () => {
    routeMock.hash = '#security'
    loginHistoryData.value = emptyLoginHistoryPage()
    const wrapper = mountUserSettings()
    await nextTick()

    const toggle = wrapper.findAll('button')
      .find((button) => button.text() === 'user.settings.sessions.showHistory')!
    expect(toggle.attributes()).toMatchObject({
      'aria-controls': 'settings-login-history',
      'aria-expanded': 'false',
    })

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('#settings-login-history').text()).toContain('user.settings.sessions.historyEmpty')
  })

  it('restores the selected section from the URL hash and updates deep links', async () => {
    routeMock.hash = '#security'
    const wrapper = mountUserSettings()
    await nextTick()

    expect(wrapper.get('#security').isVisible()).toBe(true)
    expect(wrapper.get('#general').isVisible()).toBe(false)

    const notificationTab = wrapper.findAll('[role="tab"]')
      .find((tab) => tab.text() === 'user.settings.notifications')!
    await notificationTab.trigger('click')

    expect(routerReplaceMock).toHaveBeenCalledWith({ hash: '#notifications' })
    expect(wrapper.get('#notifications').isVisible()).toBe(true)
  })

  it('enables save buttons only after each section changes', async () => {
    const wrapper = mountUserSettings()
    await nextTick()

    const { generalSaveButton, notificationSaveButton } = getSaveButtons(wrapper)
    expect(generalSaveButton.attributes('disabled')).toBeDefined()
    expect(notificationSaveButton.attributes('disabled')).toBeDefined()

    await wrapper.findAll('select')[0].setValue('DARK')
    expect(generalSaveButton.attributes('disabled')).toBeUndefined()
    expect(notificationSaveButton.attributes('disabled')).toBeDefined()

    await wrapper.get('#notification-like').setValue(true)
    expect(notificationSaveButton.attributes('disabled')).toBeUndefined()
  })

  it('saves only general settings from the general section', async () => {
    const wrapper = mountUserSettings()
    await nextTick()

    const selects = wrapper.findAll('select')
    await selects[0].setValue('DARK')
    await selects[1].setValue('en')

    await getSaveButtons(wrapper).generalSaveButton.trigger('click')
    await nextTick()

    expect(updateSettings).toHaveBeenCalledWith({
      theme: 'DARK',
      language: 'en',
      timezone: 'Asia/Seoul',
      hideNsfw: true,
    })
    expect(updateNotificationSettings).not.toHaveBeenCalled()
    expect(setTheme).toHaveBeenCalledWith('DARK')
    expect(wrapper.text()).toContain('user.settings.saved')
    const message = wrapper.findAll('p').find((item) => item.text() === 'user.settings.saved')
    expect(message?.attributes('role')).toBe('status')
    expect(message?.attributes('aria-live')).toBe('polite')
  })

  it('announces general settings save failures as alerts', async () => {
    updateSettings.mockRejectedValueOnce(new Error('save failed'))
    const wrapper = mountUserSettings()
    await nextTick()

    await wrapper.findAll('select')[0].setValue('DARK')
    await getSaveButtons(wrapper).generalSaveButton.trigger('click')
    await flushAll()

    const message = wrapper.findAll('p').find((item) => item.text() === 'user.settings.failed')
    expect(message?.attributes('role')).toBe('alert')
    expect(message?.attributes('aria-live')).toBeUndefined()
  })

  it('saves notification settings through the bulk endpoint payload', async () => {
    const wrapper = mountUserSettings()
    await nextTick()

    await wrapper.get('#notification-like').setValue(true)
    await wrapper.get('#notification-comment').setValue(false)
    await wrapper.get('#notification-reply').setValue(false)

    await getSaveButtons(wrapper).notificationSaveButton.trigger('click')
    await nextTick()

    expect(updateSettings).not.toHaveBeenCalled()
    expect(updateNotificationSettings).toHaveBeenCalledTimes(1)
    expect(updateNotificationSettings).toHaveBeenCalledWith({
      settings: [
        { notificationType: 'LIKE', isEnabled: true },
        { notificationType: 'COMMENT', isEnabled: false },
        { notificationType: 'REPLY', isEnabled: false },
        { notificationType: 'MENTION', isEnabled: true },
        { notificationType: 'MESSAGE', isEnabled: true },
        { notificationType: 'SYSTEM', isEnabled: true },
        { notificationType: 'SANCTION', isEnabled: true },
        { notificationType: 'KEYWORD', isEnabled: true },
        { notificationType: 'BADGE', isEnabled: true },
      ],
    })
    expect(wrapper.text()).toContain('user.settings.saved')
    const message = wrapper.findAll('p').find((item) => item.text() === 'user.settings.saved')
    expect(message?.attributes('role')).toBe('status')
    expect(message?.attributes('aria-live')).toBe('polite')
  })

  it('does not overwrite dirty notification form state on refetch', async () => {
    const wrapper = mountUserSettings()
    await nextTick()

    await wrapper.get('#notification-like').setValue(true)
    expect((wrapper.get('#notification-like').element as HTMLInputElement).checked).toBe(true)

    notificationData.value = [
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: false },
      { notificationType: 'REPLY', isEnabled: false },
    ]
    await nextTick()

    expect((wrapper.get('#notification-like').element as HTMLInputElement).checked).toBe(true)
  })

  it('adds and removes keyword subscriptions', async () => {
    keywordData.value = [
      { subscriptionId: 11, keyword: 'vue', createdAt: '2026-07-09T00:00:00' },
    ]
    const wrapper = mountUserSettings()
    await nextTick()

    expect(wrapper.text()).toContain('vue')

    await wrapper.get('#keyword-subscription-input').setValue('spring')
    await wrapper.get('form').trigger('submit')
    await flushAll()

    expect(createKeywordSubscription).toHaveBeenCalledWith({ keyword: 'spring' })
    expect(wrapper.text()).toContain('user.settings.keywordAdded')

    const removeButton = wrapper
      .findAll('button')
      .find((button) => button.attributes('aria-label') === 'user.settings.keywordRemove')!
    expect(removeButton.classes()).toEqual(expect.arrayContaining(['nv-touch-target-square', 'nv-focus-ring']))

    await removeButton.trigger('click')
    await flushAll()

    expect(deleteKeywordSubscription).toHaveBeenCalledWith({ keyword: 'vue' })
    expect(wrapper.text()).toContain('user.settings.keywordRemoved')
  })

  it('shows keyword disabled notice when keyword notifications are off', async () => {
    notificationData.value = [
      { notificationType: 'KEYWORD', isEnabled: false },
    ]
    const wrapper = mountUserSettings()
    await nextTick()

    expect(wrapper.text()).toContain('user.settings.keywordNotificationsDisabled')
  })

  it('enables and disables browser push from settings', async () => {
    settingsData.value = {
      ...settingsData.value,
      pushEnabled: false,
    }
    const wrapper = mountUserSettings()
    await nextTick()

    expect(wrapper.text()).toContain('user.settings.pushDisabled')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'user.settings.pushEnable')!
      .trigger('click')
    await flushAll()

    expect(pushNotificationMock.enablePush).toHaveBeenCalled()
    expect(wrapper.text()).toContain('user.settings.pushEnableSuccess')

    settingsData.value = {
      ...settingsData.value,
      pushEnabled: true,
    }
    await nextTick()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'user.settings.pushDisable')!
      .trigger('click')
    await flushAll()

    expect(pushNotificationMock.disablePush).toHaveBeenCalled()
    expect(wrapper.text()).toContain('user.settings.pushDisableSuccess')
  })

  it('distinguishes unsupported, unconfigured, and failed push configuration states', async () => {
    pushNotificationMock.supported.value = false
    let wrapper = mountUserSettings()
    expect(wrapper.text()).toContain('user.settings.pushUnsupported')
    wrapper.unmount()

    pushNotificationMock.supported.value = true
    pushNotificationMock.enabled.value = false
    wrapper = mountUserSettings()
    expect(wrapper.text()).toContain('user.settings.pushUnavailable')
    wrapper.unmount()

    pushNotificationMock.isError.value = true
    pushNotificationMock.error.value = new Error('key failed')
    wrapper = mountUserSettings()
    const pushError = wrapper.findAll('[role="alert"]')
      .find((candidate) => candidate.text().includes('user.settings.pushLoadFailed'))!
    expect(pushError.exists()).toBe(true)
    expect(wrapper.text()).not.toContain('user.settings.pushUnavailable')

    await pushError.get('button').trigger('click')
    expect(pushNotificationMock.refetch).toHaveBeenCalledOnce()
  })

  it('retries all critical settings queries from the error state', async () => {
    isSettingsError.value = true
    const wrapper = mountUserSettings()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'PageHeader' }).exists()).toBe(false)
    await wrapper.get('[role="alert"] button').trigger('click')

    expect(refetchSettings).toHaveBeenCalledOnce()
    expect(refetchNotifications).toHaveBeenCalledOnce()
    expect(refetchSessions).toHaveBeenCalledOnce()
  })

  it('does not revoke the new account sessions after a delayed confirmation', async () => {
    sessionsData.value = [
      { sessionId: 1, deviceSummary: 'current', ipAddress: '127.0.0.1', lastUsedAt: '', expiresAt: '', current: true },
      { sessionId: 2, deviceSummary: 'other', ipAddress: '127.0.0.2', lastUsedAt: '', expiresAt: '', current: false },
    ]
    let resolveConfirm!: (value: boolean) => void
    confirmMock.mockImplementationOnce(() => new Promise((resolve) => { resolveConfirm = resolve }))
    const wrapper = mountUserSettings()
    const button = wrapper.findAll('button')
      .find((candidate) => candidate.text() === 'user.settings.sessions.logoutOthers')!

    const click = button.trigger('click')
    authStoreMock.sessionGeneration = 2
    authStoreMock.accessToken = 'token-b'
    authStoreMock.user = { userId: 2 }
    resolveConfirm(true)
    await click
    await flushAll()

    expect(revokeOtherSessions).not.toHaveBeenCalled()
  })
})

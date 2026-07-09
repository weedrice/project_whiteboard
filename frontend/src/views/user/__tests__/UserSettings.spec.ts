import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import type {
  KeywordSubscriptionPayload,
  KeywordSubscriptionResponse,
  NotificationSettingsBulkPayload,
  NotificationSettingsPayload
} from '@/api/user'
import type { UserSettings as UserSettingsData } from '@/types'
import UserSettings from '../UserSettings.vue'
import { BaseButtonStub, flushAll, identityT } from '@/test/vue-test-helpers'

type UserSettingsFixture = Omit<UserSettingsData, 'language'> & { language: string }
type ThemeStoreMock = {
  readonly isDark: boolean
  setTheme: (theme: UserSettingsData['theme']) => void
}
type UserComposableMock = {
  useUserSettings: () => { data: typeof settingsData; isLoading: typeof isSettingsLoading }
  useNotificationSettings: () => { data: typeof notificationData; isLoading: typeof isNotifLoading }
  useKeywordSubscriptions: () => { data: typeof keywordData; isLoading: typeof isKeywordLoading }
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
}

const useUserMock = vi.hoisted(() => vi.fn<() => UserComposableMock>())
const useThemeStoreMock = vi.hoisted(() => vi.fn<() => ThemeStoreMock>())
const pushNotificationMock = vi.hoisted(() => ({
  enabled: { value: true },
  supported: { value: true },
  permission: { value: 'default' as NotificationPermission | 'unsupported' },
  isLoading: { value: false },
  isEnabling: { value: false },
  isDisabling: { value: false },
  enablePush: vi.fn(),
  disablePush: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: identityT }),
}))

vi.mock('@/composables/useUser', () => ({
  useUser: useUserMock,
}))

vi.mock('@/stores/theme', () => ({
  useThemeStore: useThemeStoreMock,
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
  emailNotification: true,
  pushNotification: true,
})
const notificationData = ref<NotificationSettingsPayload[]>([
  { notificationType: 'LIKE' as const, isEnabled: false },
  { notificationType: 'COMMENT' as const, isEnabled: true },
])
const keywordData = ref<KeywordSubscriptionResponse[]>([])
const isSettingsLoading = ref(false)
const isNotifLoading = ref(false)
const isKeywordLoading = ref(false)
const isUpdatingSettings = ref(false)
const isUpdatingNotifications = ref(false)
const isCreatingKeyword = ref(false)
const isDeletingKeyword = ref(false)
const updateSettings = vi.fn()
const updateNotificationSettings = vi.fn()
const createKeywordSubscription = vi.fn()
const deleteKeywordSubscription = vi.fn()
const setTheme = vi.fn()
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
      },
    },
  })

  mountedWrappers.push(wrapper)
  return wrapper
}

describe('UserSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()

    themeIsDark.value = false
    settingsData.value = {
      theme: 'LIGHT',
      language: 'ko',
      timezone: 'Asia/Seoul',
      hideNsfw: true,
      emailNotification: true,
      pushNotification: true,
    }
    notificationData.value = [
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: true },
    ]
    keywordData.value = []
    isSettingsLoading.value = false
    isNotifLoading.value = false
    isKeywordLoading.value = false
    isUpdatingSettings.value = false
    isUpdatingNotifications.value = false
    isCreatingKeyword.value = false
    isDeletingKeyword.value = false
    updateSettings.mockResolvedValue(undefined)
    updateNotificationSettings.mockResolvedValue(undefined)
    createKeywordSubscription.mockResolvedValue(undefined)
    deleteKeywordSubscription.mockResolvedValue(undefined)
    pushNotificationMock.enabled.value = true
    pushNotificationMock.supported.value = true
    pushNotificationMock.permission.value = 'default'
    pushNotificationMock.isLoading.value = false
    pushNotificationMock.isEnabling.value = false
    pushNotificationMock.isDisabling.value = false
    pushNotificationMock.enablePush.mockResolvedValue(undefined)
    pushNotificationMock.disablePush.mockResolvedValue(undefined)

    useThemeStoreMock.mockReturnValue({
      get isDark() {
        return themeIsDark.value
      },
      setTheme,
    })

    useUserMock.mockReturnValue({
      useUserSettings: () => ({ data: settingsData, isLoading: isSettingsLoading }),
      useNotificationSettings: () => ({ data: notificationData, isLoading: isNotifLoading }),
      useKeywordSubscriptions: () => ({ data: keywordData, isLoading: isKeywordLoading }),
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

    await wrapper
      .findAll('button')
      .find((button) => button.attributes('aria-label') === 'user.settings.keywordRemove')!
      .trigger('click')
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
})

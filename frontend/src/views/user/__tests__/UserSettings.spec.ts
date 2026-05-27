import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import type { NotificationSettingsPayload } from '@/api/user'
import UserSettings from '../UserSettings.vue'
import { useUser } from '@/composables/useUser'
import { useThemeStore } from '@/stores/theme'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/composables/useUser', () => ({
  useUser: vi.fn(),
}))

vi.mock('@/stores/theme', () => ({
  useThemeStore: vi.fn(),
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

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: {
    loading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          type: 'button',
          disabled: props.disabled,
          'data-loading': String(props.loading),
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const BaseSpinnerStub = defineComponent({
  name: 'BaseSpinner',
  setup() {
    return () => h('div', 'loading')
  },
})

const themeIsDark = ref(false)
const settingsData = ref({
  theme: 'LIGHT' as const,
  language: 'ko',
  timezone: 'Asia/Seoul',
  hideNsfw: true,
})
const notificationData = ref<NotificationSettingsPayload[]>([
  { notificationType: 'LIKE' as const, isEnabled: false },
  { notificationType: 'COMMENT' as const, isEnabled: true },
])
const isSettingsLoading = ref(false)
const isNotifLoading = ref(false)
const isUpdatingSettings = ref(false)
const isUpdatingNotifications = ref(false)
const updateSettings = vi.fn()
const updateNotificationSettings = vi.fn()
const setTheme = vi.fn()
const mountedWrappers: Array<ReturnType<typeof mount>> = []

const flushPromises = async () => {
    await Promise.resolve()
    await Promise.resolve()
}

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
        $t: (key: string) => key,
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
    }
    notificationData.value = [
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: true },
    ]
    isSettingsLoading.value = false
    isNotifLoading.value = false
    isUpdatingSettings.value = false
    isUpdatingNotifications.value = false
    updateSettings.mockResolvedValue(undefined)
    updateNotificationSettings.mockResolvedValue(undefined)

    vi.mocked(useThemeStore).mockReturnValue({
      isDark: themeIsDark,
      setTheme,
    } as never)

    vi.mocked(useUser).mockReturnValue({
      useUserSettings: () => ({ data: settingsData, isLoading: isSettingsLoading }),
      useNotificationSettings: () => ({ data: notificationData, isLoading: isNotifLoading }),
      useUpdateUserSettings: () => ({
        mutateAsync: updateSettings,
        isPending: isUpdatingSettings,
      }),
      useUpdateNotificationSettings: () => ({
        mutateAsync: updateNotificationSettings,
        isPending: isUpdatingNotifications,
      }),
    } as never)
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
    expect(wrapper.text()).toContain('user.settings.like')
    expect(wrapper.text()).toContain('user.settings.comment')
    expect(wrapper.text()).toContain('user.settings.reply')

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
    await flushPromises()

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
})

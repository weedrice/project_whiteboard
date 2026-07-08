import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import type { NotificationSettingsPayload } from '@/api/user'
import type { UserSettings } from '@/types'
import {
  useNotificationSettingsForm,
  useUserSettingsForm
} from '../useUserSettingsForm'

const t = (key: string) => key

describe('useUserSettingsForm', () => {
  it('hydrates general settings without marking the form dirty', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT',
      language: 'ko' as UserSettings['language'],
      timezone: 'Asia/Seoul',
      hideNsfw: true,
      emailNotification: true,
      pushNotification: true
    })
    const updateSettings = vi.fn().mockResolvedValue(undefined)
    const setTheme = vi.fn()
    const form = useUserSettingsForm({
      settingsData,
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings,
      setTheme,
      t
    })

    await nextTick()

    expect(form.form.theme).toBe('LIGHT')
    expect(form.canSave.value).toBe(false)

    form.form.language = 'en'
    await nextTick()

    expect(form.canSave.value).toBe(true)
    await form.save()

    expect(updateSettings).toHaveBeenCalledWith({
      theme: 'LIGHT',
      language: 'en',
      timezone: 'Asia/Seoul',
      hideNsfw: true
    })
    expect(setTheme).toHaveBeenCalledWith('LIGHT')
    expect(form.canSave.value).toBe(false)
    expect(form.message.value).toBe('user.settings.saved')
  })

  it('keeps dirty general settings when query data refetches', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT',
      language: 'ko' as UserSettings['language'],
      timezone: 'Asia/Seoul',
      hideNsfw: true,
      emailNotification: true,
      pushNotification: true
    })
    const form = useUserSettingsForm({
      settingsData,
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings: vi.fn().mockResolvedValue(undefined),
      setTheme: vi.fn(),
      t
    })

    await nextTick()
    form.form.language = 'en'
    await nextTick()

    settingsData.value = {
      ...settingsData.value,
      language: 'ko' as UserSettings['language']
    }
    await nextTick()

    expect(form.form.language).toBe('en')
  })

  it('keeps general settings clean when unchanged form receives a refetch', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT',
      language: 'ko' as UserSettings['language'],
      timezone: 'Asia/Seoul',
      hideNsfw: true,
      emailNotification: true,
      pushNotification: true
    })
    const form = useUserSettingsForm({
      settingsData,
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings: vi.fn().mockResolvedValue(undefined),
      setTheme: vi.fn(),
      t
    })

    await nextTick()

    settingsData.value = {
      ...settingsData.value,
      theme: 'DARK'
    }
    await nextTick()

    expect(form.form.theme).toBe('DARK')
    expect(form.canSave.value).toBe(false)
  })
})

describe('useNotificationSettingsForm', () => {
  it('hydrates notification settings without marking them dirty', async () => {
    const notificationData = ref<NotificationSettingsPayload[]>([
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: true }
    ])
    const updateNotificationSettings = vi.fn().mockResolvedValue(undefined)
    const form = useNotificationSettingsForm({
      notificationData,
      isSaving: ref(false),
      updateNotificationSettings,
      t
    })

    await nextTick()

    expect(form.settings.LIKE).toBe(false)
    expect(form.settings.COMMENT).toBe(true)
    expect(form.settings.REPLY).toBe(true)
    expect(form.canSave.value).toBe(false)

    form.settings.REPLY = false
    await nextTick()

    expect(form.canSave.value).toBe(true)
    await form.save()

    expect(updateNotificationSettings).toHaveBeenCalledWith({
      settings: [
        { notificationType: 'LIKE', isEnabled: false },
        { notificationType: 'COMMENT', isEnabled: true },
        { notificationType: 'REPLY', isEnabled: false },
        { notificationType: 'MENTION', isEnabled: true },
        { notificationType: 'MESSAGE', isEnabled: true },
        { notificationType: 'SYSTEM', isEnabled: true },
        { notificationType: 'SANCTION', isEnabled: true },
        { notificationType: 'KEYWORD', isEnabled: true }
      ]
    })
    expect(form.canSave.value).toBe(false)
    expect(form.message.value).toBe('user.settings.saved')
  })

  it('keeps dirty notification settings when query data refetches', async () => {
    const notificationData = ref<NotificationSettingsPayload[]>([
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: true },
      { notificationType: 'REPLY', isEnabled: true }
    ])
    const form = useNotificationSettingsForm({
      notificationData,
      isSaving: ref(false),
      updateNotificationSettings: vi.fn().mockResolvedValue(undefined),
      t
    })

    await nextTick()
    form.settings.LIKE = true
    await nextTick()

    notificationData.value = [
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: false },
      { notificationType: 'REPLY', isEnabled: false }
    ]
    await nextTick()

    expect(form.settings.LIKE).toBe(true)
  })

  it('keeps notification settings clean when unchanged settings receive a refetch', async () => {
    const notificationData = ref<NotificationSettingsPayload[]>([
      { notificationType: 'LIKE', isEnabled: false },
      { notificationType: 'COMMENT', isEnabled: true },
      { notificationType: 'REPLY', isEnabled: true }
    ])
    const form = useNotificationSettingsForm({
      notificationData,
      isSaving: ref(false),
      updateNotificationSettings: vi.fn().mockResolvedValue(undefined),
      t
    })

    await nextTick()

    notificationData.value = [
      { notificationType: 'LIKE', isEnabled: true },
      { notificationType: 'COMMENT', isEnabled: false },
      { notificationType: 'REPLY', isEnabled: true }
    ]
    await nextTick()

    expect(form.settings.LIKE).toBe(true)
    expect(form.settings.COMMENT).toBe(false)
    expect(form.canSave.value).toBe(false)
  })
})

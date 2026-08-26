import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import type { NotificationSettingsPayload } from '@/api/user'
import type { UserSettings } from '@/types'
import { AUTO_TIME_ZONE } from '@/utils/displayTimeZone'
import {
  useNotificationSettingsForm,
  useUserSettingsForm
} from '../useUserSettingsForm'

const t = (key: string) => key

describe('useUserSettingsForm', () => {
  it('stops the save and shows an error when locale messages cannot be loaded', async () => {
    const updateSettings = vi.fn()
    const form = useUserSettingsForm({
      settingsData: ref<UserSettings>({
        theme: 'LIGHT', language: 'ko', timezone: 'Asia/Seoul', hideNsfw: true, pushEnabled: false,
      }),
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings,
      setTheme: vi.fn(),
      setLocale: vi.fn().mockResolvedValue(false),
      t,
    })
    await nextTick()
    form.form.language = 'en'
    await nextTick()

    await form.save()

    expect(updateSettings).not.toHaveBeenCalled()
    expect(form.isError.value).toBe(true)
    expect(form.message.value).toBe('user.settings.failed')
  })

  it('hydrates general settings without marking the form dirty', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT',
      language: 'ko' as UserSettings['language'],
      timezone: 'Asia/Seoul',
      hideNsfw: true,
      pushEnabled: false
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
      pushEnabled: false
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
      pushEnabled: false
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

  it('does not apply a delayed locale save after the session generation changes', async () => {
    let generation = 3
    let releaseLocale: (() => void) | undefined
    const setLocale = vi.fn((_locale: UserSettings['language'], canCommit?: () => boolean) => (
      new Promise<boolean>((resolve) => {
        releaseLocale = () => resolve(canCommit?.() ?? true)
      })
    ))
    const updateSettings = vi.fn()
    const form = useUserSettingsForm({
      settingsData: ref<UserSettings>({
        theme: 'LIGHT', language: 'ko', timezone: 'Asia/Seoul', hideNsfw: true, pushEnabled: false,
      }),
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings,
      setTheme: vi.fn(),
      setLocale,
      getSessionGeneration: () => generation,
      t,
    })
    await nextTick()
    form.form.language = 'en'
    await nextTick()

    const save = form.save()
    await vi.waitFor(() => expect(setLocale).toHaveBeenCalledTimes(1))
    generation += 1
    releaseLocale?.()
    await save

    expect(updateSettings).not.toHaveBeenCalled()
    expect(form.message.value).toBe('')
  })

  it('reloads settings and asks for retry on a concurrent modification conflict', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT', language: 'ko', timezone: 'Asia/Seoul', hideNsfw: true, pushEnabled: false,
    })
    const reloadSettings = vi.fn(async () => {
      settingsData.value = {
        theme: 'DARK', language: 'en', timezone: 'UTC', hideNsfw: false, pushEnabled: false,
      }
    })
    const setLocale = vi.fn().mockResolvedValue(true)
    const setTheme = vi.fn()
    const updateSettings = vi.fn().mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { error: { code: 'C012' } } },
    })
    const form = useUserSettingsForm({
      settingsData,
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings,
      setTheme,
      setLocale,
      reloadSettings,
      t,
    })
    await nextTick()
    form.form.theme = 'DARK'
    form.form.language = 'en'
    await nextTick()

    await form.save()

    expect(reloadSettings).toHaveBeenCalledTimes(1)
    expect(form.form).toMatchObject({ theme: 'DARK', language: 'en', timezone: 'UTC', hideNsfw: false })
    expect(form.isDirty.value).toBe(false)
    expect(form.canSave.value).toBe(false)
    expect(setTheme).toHaveBeenCalledWith('DARK')
    expect(setLocale).toHaveBeenLastCalledWith('en', expect.any(Function))
    expect(form.message.value).toBe('common.messages.concurrentModification')
    expect(form.isError.value).toBe(true)
  })

  it('discards a dirty general-settings draft when the account session changes', async () => {
    const generation = ref(1)
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT', language: 'ko', timezone: 'Asia/Seoul', hideNsfw: true, pushEnabled: false,
    })
    const form = useUserSettingsForm({
      settingsData,
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings: vi.fn(),
      setTheme: vi.fn(),
      getSessionGeneration: () => generation.value,
      t,
    })
    await nextTick()
    form.form.language = 'en'
    expect(form.isDirty.value).toBe(true)

    generation.value += 1
    await nextTick()

    expect(form.form.language).toBe('ko')
    expect(form.isDirty.value).toBe(false)
    expect(form.canSave.value).toBe(false)

    settingsData.value = {
      theme: 'DARK', language: 'en', timezone: 'UTC', hideNsfw: false, pushEnabled: false,
    }
    await nextTick()
    expect(form.form).toMatchObject({ theme: 'DARK', language: 'en', timezone: 'UTC', hideNsfw: false })
    expect(form.isDirty.value).toBe(false)
  })

  // "자동"은 서버에 표식으로 저장되어야 한다. 값을 빼고 보내면 서버는 "변경 없음"으로
  // 읽어 이전에 고른 지역이 그대로 남고, 화면에는 저장 성공으로 보이는 조용한 실패가 된다.
  it('sends the AUTO marker so "자동" actually replaces the stored zone', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT',
      language: 'ko' as UserSettings['language'],
      timezone: 'Asia/Tokyo',
      hideNsfw: true,
      pushEnabled: false
    })
    const updateSettings = vi.fn().mockResolvedValue(undefined)
    const form = useUserSettingsForm({
      settingsData,
      isSaving: ref(false),
      themeIsDark: () => false,
      updateSettings,
      setTheme: vi.fn(),
      t
    })
    await nextTick()
    expect(form.form.timezone).toBe('Asia/Tokyo')

    form.form.timezone = AUTO_TIME_ZONE
    await nextTick()
    expect(form.canSave.value).toBe(true)

    await form.save()

    expect(updateSettings).toHaveBeenCalledWith({
      theme: 'LIGHT',
      language: 'ko',
      timezone: AUTO_TIME_ZONE,
      hideNsfw: true
    })
    expect(form.message.value).toBe('user.settings.saved')
    // baseline이 보낸 값과 어긋나면 isDirty가 영원히 참으로 남아, 저장 버튼이 계속
    // 활성화되고 서버 재동기화(hydrate watch)가 이 세션 동안 막힌다.
    expect(form.isDirty.value).toBe(false)
    expect(form.canSave.value).toBe(false)
  })

  it('re-syncs from the server after saving AUTO', async () => {
    const settingsData = ref<UserSettings>({
      theme: 'LIGHT',
      language: 'ko' as UserSettings['language'],
      timezone: 'Asia/Tokyo',
      hideNsfw: true,
      pushEnabled: false
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
    form.form.timezone = AUTO_TIME_ZONE
    await nextTick()
    await form.save()

    settingsData.value = {
      theme: 'LIGHT', language: 'ko', timezone: AUTO_TIME_ZONE, hideNsfw: true, pushEnabled: false,
    }
    await nextTick()

    expect(form.form.timezone).toBe(AUTO_TIME_ZONE)
    expect(form.isDirty.value).toBe(false)
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

    form.settings.LIKE = true
    await nextTick()

    expect(form.canSave.value).toBe(true)
    await form.save()

    expect(updateNotificationSettings).toHaveBeenCalledWith({
      settings: [
        { notificationType: 'LIKE', isEnabled: true },
        { notificationType: 'COMMENT', isEnabled: true }
      ]
    })
    expect(form.canSave.value).toBe(false)
    expect(form.message.value).toBe('user.settings.saved')
  })

  it('does not submit notification types omitted by the server compatibility gate', async () => {
    const updateNotificationSettings = vi.fn().mockResolvedValue(undefined)
    const form = useNotificationSettingsForm({
      notificationData: ref<NotificationSettingsPayload[]>([
        { notificationType: 'LIKE', isEnabled: true },
        { notificationType: 'COMMENT', isEnabled: true },
      ]),
      isSaving: ref(false),
      updateNotificationSettings,
      t,
    })
    await nextTick()

    expect(form.availableTypes.value).toEqual(['LIKE', 'COMMENT'])
    form.settings.LIKE = false
    form.settings.INQUIRY = false
    await form.save()

    expect(updateNotificationSettings).toHaveBeenCalledWith({
      settings: [
        { notificationType: 'LIKE', isEnabled: false },
        { notificationType: 'COMMENT', isEnabled: true },
      ],
    })
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

  it('keeps edits made during a save dirty against the submitted snapshot', async () => {
    let resolveSave!: () => void
    const updateNotificationSettings = vi.fn(() => new Promise<void>((resolve) => {
      resolveSave = resolve
    }))
    const form = useNotificationSettingsForm({
      notificationData: ref<NotificationSettingsPayload[]>([
        { notificationType: 'LIKE', isEnabled: true },
        { notificationType: 'COMMENT', isEnabled: true },
      ]),
      isSaving: ref(false),
      updateNotificationSettings,
      t,
    })
    await nextTick()
    form.settings.LIKE = false

    const save = form.save()
    await vi.waitFor(() => expect(updateNotificationSettings).toHaveBeenCalledOnce())
    form.settings.COMMENT = false
    resolveSave()
    await save

    expect(updateNotificationSettings).toHaveBeenCalledWith(expect.objectContaining({
      settings: expect.arrayContaining([
        { notificationType: 'LIKE', isEnabled: false },
        { notificationType: 'COMMENT', isEnabled: true },
      ]),
    }))
    expect(form.isDirty.value).toBe(true)
    expect(form.canSave.value).toBe(true)
  })

  it('ignores a delayed save after the auth session changes', async () => {
    let generation = 1
    let resolveSave!: () => void
    const form = useNotificationSettingsForm({
      notificationData: ref<NotificationSettingsPayload[]>([
        { notificationType: 'LIKE', isEnabled: true },
      ]),
      isSaving: ref(false),
      updateNotificationSettings: vi.fn(() => new Promise<void>((resolve) => {
        resolveSave = resolve
      })),
      getSessionGeneration: () => generation,
      t,
    })
    await nextTick()
    form.settings.LIKE = false

    const save = form.save()
    generation += 1
    resolveSave()
    await save

    expect(form.message.value).toBe('')
    expect(form.isDirty.value).toBe(true)
  })

  it('discards dirty notification settings when the account session changes', async () => {
    const generation = ref(1)
    const notificationData = ref<NotificationSettingsPayload[]>([
      { notificationType: 'LIKE', isEnabled: false },
    ])
    const form = useNotificationSettingsForm({
      notificationData,
      isSaving: ref(false),
      updateNotificationSettings: vi.fn(),
      getSessionGeneration: () => generation.value,
      t,
    })
    await nextTick()
    form.settings.LIKE = true
    expect(form.isDirty.value).toBe(true)

    generation.value += 1
    await nextTick()

    expect(form.settings.LIKE).toBe(true)
    expect(form.isDirty.value).toBe(false)
    expect(form.canSave.value).toBe(false)

    notificationData.value = [{ notificationType: 'LIKE', isEnabled: false }]
    await nextTick()
    expect(form.settings.LIKE).toBe(false)
    expect(form.isDirty.value).toBe(false)
  })
})

import { computed, reactive, ref, watch, type Ref } from 'vue'
import type { NotificationSettingType, NotificationSettingsBulkPayload, NotificationSettingsPayload } from '@/api/user'
import logger from '@/utils/logger'
import type { UserSettings } from '@/types'

export const NOTIFICATION_TYPES: NotificationSettingType[] = ['LIKE', 'COMMENT', 'REPLY', 'MENTION', 'MESSAGE', 'SYSTEM', 'SANCTION']

interface UserSettingsForm {
  theme: 'LIGHT' | 'DARK'
  language: string
  timezone?: string
  hideNsfw?: boolean
}

interface UseUserSettingsFormOptions {
  settingsData: Ref<UserSettings | undefined>
  isSaving: Ref<boolean>
  themeIsDark: () => boolean
  updateSettings: (payload: Partial<UserSettings>) => Promise<unknown>
  setTheme: (theme: 'LIGHT' | 'DARK') => void
  t: (key: string) => string
}

interface UseNotificationSettingsFormOptions {
  notificationData: Ref<NotificationSettingsPayload[] | undefined>
  isSaving: Ref<boolean>
  updateNotificationSettings: (payload: NotificationSettingsBulkPayload) => Promise<unknown>
  t: (key: string) => string
}

export function useUserSettingsForm(options: UseUserSettingsFormOptions) {
  const form = reactive<UserSettingsForm>({
    theme: 'LIGHT',
    language: 'ko',
    timezone: 'Asia/Seoul',
    hideNsfw: true
  })
  const message = ref('')
  const isError = ref(false)
  const baseline = ref<UserSettingsForm | null>(null)
  const isDirty = computed(() => (
    baseline.value !== null
    && (
      form.theme !== baseline.value.theme
      || form.language !== baseline.value.language
      || form.timezone !== baseline.value.timezone
      || form.hideNsfw !== baseline.value.hideNsfw
    )
  ))
  const canSave = computed(() => isDirty.value && !options.isSaving.value)

  watch(options.settingsData, (value) => {
    if (!value || isDirty.value) {
      return
    }

    const nextForm: UserSettingsForm = {
      theme: value.theme,
      language: value.language,
      timezone: Object.hasOwn(value, 'timezone') ? value.timezone : form.timezone,
      hideNsfw: Object.hasOwn(value, 'hideNsfw') ? value.hideNsfw : form.hideNsfw
    }
    Object.assign(form, nextForm)
    baseline.value = { ...nextForm }
  }, { immediate: true })

  watch(options.themeIsDark, (isDark) => {
    const theme = isDark ? 'DARK' : 'LIGHT'
    if (form.theme !== theme) {
      form.theme = theme
    }
  })

  const save = async () => {
    if (!canSave.value) {
      return
    }

    message.value = ''
    isError.value = false
    try {
      await options.updateSettings({
        theme: form.theme,
        language: form.language as UserSettings['language'],
        timezone: form.timezone,
        hideNsfw: form.hideNsfw
      })

      options.setTheme(form.theme)
      baseline.value = { ...form }
      message.value = options.t('user.settings.saved')
    } catch (error: unknown) {
      logger.error('Failed to save general settings:', error)
      message.value = options.t('user.settings.failed')
      isError.value = true
    }
  }

  return {
    canSave,
    form,
    isDirty,
    isError,
    message,
    save
  }
}

export function useNotificationSettingsForm(options: UseNotificationSettingsFormOptions) {
  const settings = reactive<Record<NotificationSettingType, boolean>>({
    LIKE: true,
    COMMENT: true,
    REPLY: true,
    MENTION: true,
    MESSAGE: true,
    SYSTEM: true,
    SANCTION: true
  })
  const message = ref('')
  const isError = ref(false)
  const baseline = ref<Record<NotificationSettingType, boolean> | null>(null)
  const isDirty = computed(() => (
    baseline.value !== null
    && NOTIFICATION_TYPES.some((type) => settings[type] !== baseline.value?.[type])
  ))
  const canSave = computed(() => isDirty.value && !options.isSaving.value)

  watch(options.notificationData, (value) => {
    if (!value || isDirty.value) {
      return
    }

    const nextSettings = {} as Record<NotificationSettingType, boolean>
    for (const type of NOTIFICATION_TYPES) {
      nextSettings[type] = value.find((setting) => setting.notificationType === type)?.isEnabled ?? true
    }
    Object.assign(settings, nextSettings)
    baseline.value = { ...nextSettings }
  }, { immediate: true })

  const save = async () => {
    if (!canSave.value) {
      return
    }

    message.value = ''
    isError.value = false
    try {
      await options.updateNotificationSettings({
        settings: NOTIFICATION_TYPES.map((notificationType) => ({
          notificationType,
          isEnabled: settings[notificationType]
        }))
      })

      baseline.value = { ...settings }
      message.value = options.t('user.settings.saved')
    } catch (error: unknown) {
      logger.error('Failed to save notification settings:', error)
      message.value = options.t('user.settings.failed')
      isError.value = true
    }
  }

  return {
    canSave,
    isDirty,
    isError,
    message,
    save,
    settings
  }
}

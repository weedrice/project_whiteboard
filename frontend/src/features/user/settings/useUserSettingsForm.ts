import { AUTO_TIME_ZONE, rememberUserTimeZone } from '@/utils/displayTimeZone'
import { computed, reactive, ref, watch, type Ref } from 'vue'
import type { NotificationSettingType, NotificationSettingsBulkPayload, NotificationSettingsPayload } from '@/api/user'
import logger from '@/utils/logger'
import type { UserSettings, UserSettingsUpdatePayload } from '@/types'
import { isConcurrentModificationError } from '@/utils/errorHandler'

export const NOTIFICATION_TYPES: NotificationSettingType[] = ['LIKE', 'COMMENT', 'REPLY', 'MENTION', 'MESSAGE', 'SYSTEM', 'SANCTION', 'KEYWORD', 'BADGE', 'INQUIRY']

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
  updateSettings: (payload: UserSettingsUpdatePayload) => Promise<unknown>
  setTheme: (theme: 'LIGHT' | 'DARK') => void
  setLocale?: (
    locale: UserSettings['language'],
    canCommit?: () => boolean,
  ) => Promise<boolean>
  getSessionGeneration?: () => number
  reloadSettings?: () => Promise<unknown>
  t: (key: string) => string
}

interface UseNotificationSettingsFormOptions {
  notificationData: Ref<NotificationSettingsPayload[] | undefined>
  notificationTypes?: Ref<NotificationSettingType[]>
  isSaving: Ref<boolean>
  updateNotificationSettings: (payload: NotificationSettingsBulkPayload) => Promise<unknown>
  getSessionGeneration?: () => number
  t: (key: string) => string
}

export function useUserSettingsForm(options: UseUserSettingsFormOptions) {
  let saveRevision = 0
  const createDefaultForm = (): UserSettingsForm => ({
    theme: 'LIGHT',
    language: 'ko',
    timezone: AUTO_TIME_ZONE,
    hideNsfw: true
  })
  const form = reactive<UserSettingsForm>(createDefaultForm())
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

  const toFormSnapshot = (value: UserSettings): UserSettingsForm => ({
    theme: value.theme,
    language: value.language,
    timezone: Object.hasOwn(value, 'timezone') ? (value.timezone || AUTO_TIME_ZONE) : form.timezone,
    hideNsfw: Object.hasOwn(value, 'hideNsfw') ? value.hideNsfw : form.hideNsfw
  })

  const hydrateFromSettings = (value: UserSettings) => {
    const nextForm = toFormSnapshot(value)
    Object.assign(form, nextForm)
    // 서버 설정이 정본이다. 기기에도 남겨 두면 다음 방문의 첫 렌더부터 바로 반영된다.
    rememberUserTimeZone(nextForm.timezone)
    baseline.value = { ...nextForm }
    return nextForm
  }

  watch(options.settingsData, (value) => {
    if (!value || isDirty.value) {
      return
    }

    hydrateFromSettings(value)
  }, { immediate: true })

  watch(options.themeIsDark, (isDark) => {
    const theme = isDark ? 'DARK' : 'LIGHT'
    if (form.theme !== theme) {
      form.theme = theme
    }
  })

  watch(() => options.getSessionGeneration?.(), () => {
    saveRevision += 1
    Object.assign(form, createDefaultForm())
    baseline.value = null
    message.value = ''
    isError.value = false
  }, { flush: 'sync' })

  const save = async () => {
    if (!canSave.value) {
      return
    }

    message.value = ''
    isError.value = false
    const revision = ++saveRevision
    const sessionGeneration = options.getSessionGeneration?.()
    const isCurrentSave = () => revision === saveRevision
      && (sessionGeneration === undefined || options.getSessionGeneration?.() === sessionGeneration)
    const previousLanguage = baseline.value?.language as UserSettings['language'] | undefined
    // AUTO도 그대로 보낸다. 서버가 이 표식을 저장하므로 "자동"이 다른 기기와 다음
    // 방문에도 유지된다. 빼고 보내면 서버는 "변경 없음"으로 읽어 이전에 고른 지역이
    // 그대로 남고, 화면에는 저장 성공으로 보이는 조용한 실패가 된다.
    const payload: UserSettingsForm & { language: UserSettings['language'] } = {
      theme: form.theme,
      language: form.language as UserSettings['language'],
      timezone: form.timezone,
      hideNsfw: form.hideNsfw
    }
    try {
      const localeApplied = options.setLocale
        ? await options.setLocale(payload.language, isCurrentSave)
        : true
      if (!isCurrentSave()) return
      if (!localeApplied) {
        message.value = options.t('user.settings.failed')
        isError.value = true
        return
      }
      await options.updateSettings(payload)
      if (!isCurrentSave()) return

      options.setTheme(payload.theme)
      rememberUserTimeZone(payload.timezone)
      baseline.value = { ...payload }
      message.value = options.t('user.settings.saved')
    } catch (error: unknown) {
      if (!isCurrentSave()) return
      if (previousLanguage && options.setLocale) {
        await options.setLocale(previousLanguage, isCurrentSave)
      }
      if (!isCurrentSave()) return
      if (isConcurrentModificationError(error)) {
        try {
          await options.reloadSettings?.()
        } catch (reloadError: unknown) {
          if (!isCurrentSave()) return
          logger.error('Failed to reload settings after concurrent modification:', reloadError)
          message.value = options.t('user.settings.failed')
          isError.value = true
          return
        }
        if (!isCurrentSave()) return
        const reloadedSettings = options.settingsData.value
        if (!reloadedSettings) {
          message.value = options.t('user.settings.failed')
          isError.value = true
          return
        }
        const nextForm = toFormSnapshot(reloadedSettings)
        const localeApplied = options.setLocale
          ? await options.setLocale(nextForm.language as UserSettings['language'], isCurrentSave)
          : true
        if (!isCurrentSave()) return
        if (!localeApplied) {
          message.value = options.t('user.settings.failed')
          isError.value = true
          return
        }
        hydrateFromSettings(reloadedSettings)
        options.setTheme(nextForm.theme)
        message.value = options.t('common.messages.concurrentModification')
        isError.value = true
        return
      }
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
  let saveRevision = 0
  const createDefaultSettings = (): Record<NotificationSettingType, boolean> => ({
    LIKE: true,
    COMMENT: true,
    REPLY: true,
    MENTION: true,
    MESSAGE: true,
    SYSTEM: true,
    SANCTION: true,
    KEYWORD: true,
    BADGE: true,
    INQUIRY: true
  })
  const settings = reactive<Record<NotificationSettingType, boolean>>(createDefaultSettings())
  const availableTypes = computed(() => {
    const returnedTypes = new Set(options.notificationData.value?.map((setting) => setting.notificationType) ?? [])
    const orderedTypes = options.notificationTypes?.value ?? NOTIFICATION_TYPES
    return orderedTypes.filter((type) => returnedTypes.has(type))
  })
  const message = ref('')
  const isError = ref(false)
  const baseline = ref<Record<NotificationSettingType, boolean> | null>(null)
  const isDirty = computed(() => (
    baseline.value !== null
    && availableTypes.value.some((type) => settings[type] !== baseline.value?.[type])
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

  watch(() => options.getSessionGeneration?.(), () => {
    saveRevision += 1
    Object.assign(settings, createDefaultSettings())
    baseline.value = null
    message.value = ''
    isError.value = false
  }, { flush: 'sync' })

  const save = async () => {
    if (!canSave.value) {
      return
    }

    message.value = ''
    isError.value = false
    const revision = ++saveRevision
    const sessionGeneration = options.getSessionGeneration?.()
    const isCurrentSave = () => revision === saveRevision
      && (sessionGeneration === undefined || options.getSessionGeneration?.() === sessionGeneration)
    const submittedSettings = { ...settings }
    const payload: NotificationSettingsBulkPayload = {
      settings: availableTypes.value.map((notificationType) => ({
        notificationType,
        isEnabled: submittedSettings[notificationType]
      }))
    }
    try {
      await options.updateNotificationSettings(payload)
      if (!isCurrentSave()) return

      baseline.value = submittedSettings
      message.value = options.t('user.settings.saved')
    } catch (error: unknown) {
      if (!isCurrentSave()) return
      logger.error('Failed to save notification settings:', error)
      message.value = options.t('user.settings.failed')
      isError.value = true
    }
  }

  return {
    availableTypes,
    canSave,
    isDirty,
    isError,
    message,
    save,
    settings
  }
}

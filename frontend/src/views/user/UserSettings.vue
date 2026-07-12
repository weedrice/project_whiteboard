<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useThemeStore } from '@/stores/theme'
import { useUser } from '@/composables/useUser'
import { useConfirm } from '@/composables/useConfirm'
import {
  NOTIFICATION_TYPES,
  useNotificationSettingsForm,
  useUserSettingsForm
} from '@/composables/useUserSettingsForm'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { usePushNotifications } from '@/features/notifications/usePushNotifications'
import { formatDateTimeOrDash } from '@/utils/date'
import { Monitor, Settings } from 'lucide-vue-next'
import { useFieldValidation } from '@/composables/useFieldValidation'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const {
  useUserSettings,
  useNotificationSettings,
  useMySessions,
  useMyLoginHistory,
  useUpdateUserSettings,
  useUpdateNotificationSettings,
  useRevokeMySession,
  useRevokeOtherSessions,
  useKeywordSubscriptions,
  useCreateKeywordSubscription,
  useDeleteKeywordSubscription
} = useUser()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const loginHistoryParams = ref({ page: 0, size: 10 })
const showLoginHistory = ref(false)
type SettingsSection = 'general' | 'notifications' | 'security'
const SETTINGS_SECTIONS: SettingsSection[] = ['general', 'notifications', 'security']
const activeSection = ref<SettingsSection>('general')
const settingsSectionOptions = computed(() => [
  { value: 'general', label: t('user.settings.general') },
  { value: 'notifications', label: t('user.settings.notifications') },
  { value: 'security', label: t('user.settings.sessions.title') },
])

watch(() => route.hash, (hash) => {
  const requested = hash.replace(/^#/, '') as SettingsSection
  activeSection.value = SETTINGS_SECTIONS.includes(requested) ? requested : 'general'
}, { immediate: true })

const selectSection = (section: string) => {
  if (!SETTINGS_SECTIONS.includes(section as SettingsSection)) return
  activeSection.value = section as SettingsSection
  void router.replace({ hash: `#${section}` })
}

const { data: settingsData, isLoading: isSettingsLoading } = useUserSettings()
const { data: notificationData, isLoading: isNotifLoading } = useNotificationSettings()
const { data: sessionsData, isLoading: isSessionsLoading } = useMySessions()
const { data: loginHistoryData, isLoading: isLoginHistoryLoading } = useMyLoginHistory(loginHistoryParams)
const { data: keywordData, isLoading: isKeywordLoading } = useKeywordSubscriptions()
const { mutateAsync: updateSettings, isPending: isUpdatingSettings } = useUpdateUserSettings()
const { mutateAsync: updateNotificationSettings, isPending: isUpdatingNotifications } = useUpdateNotificationSettings()
const { mutateAsync: revokeSession, isPending: isRevokingSession } = useRevokeMySession()
const { mutateAsync: revokeOtherSessions, isPending: isRevokingOtherSessions } = useRevokeOtherSessions()
const { mutateAsync: createKeywordSubscription, isPending: isCreatingKeyword } = useCreateKeywordSubscription()
const { mutateAsync: deleteKeywordSubscription, isPending: isDeletingKeyword } = useDeleteKeywordSubscription()

const keywordInput = ref('')
const keywordMessage = ref('')
const keywordIsError = ref(false)
const pushMessage = ref('')
const pushIsError = ref(false)
const pushNotifications = usePushNotifications()

const loading = computed(() => isSettingsLoading.value || isNotifLoading.value || isSessionsLoading.value)
const savingGeneral = computed(() => isUpdatingSettings.value)
const savingNotifications = computed(() => isUpdatingNotifications.value)
const sessions = computed(() => sessionsData.value ?? [])
const loginHistory = computed(() => loginHistoryData.value?.content ?? [])
const currentLocale = computed(() => locale.value as 'ko' | 'en')
const keywordSubscriptions = computed(() => keywordData.value ?? [])
const normalizedKeyword = computed(() => keywordInput.value.trim())
const keywordValidation = useFieldValidation<'keyword'>({
  validators: {
    keyword: (values) => {
      const value = String(values.keyword ?? '').trim()
      if (!value) return t('user.settings.keywordRequired')
      if (value.length > 50) return t('user.settings.keywordTooLong')
      if (keywordSubscriptions.value.some((subscription) => subscription.keyword === value)) return t('user.settings.keywordDuplicate')
      return ''
    },
  },
  fieldIds: { keyword: 'keyword-subscription-input' },
})
const keywordValidationValues = computed(() => ({ keyword: keywordInput.value }))
const keywordNotificationEnabled = computed(() => notificationSettings.KEYWORD !== false)
const keywordPending = computed(() => isKeywordLoading.value || isCreatingKeyword.value || isDeletingKeyword.value)
const pushPending = computed(() => pushNotifications.isEnabling.value || pushNotifications.isDisabling.value)
const browserPushEnabled = computed(() => Boolean(settingsData.value?.pushEnabled))
const pushStatusKey = computed(() => {
  if (!pushNotifications.supported.value) return 'user.settings.pushUnsupported'
  if (!pushNotifications.enabled.value) return 'user.settings.pushUnavailable'
  if (pushNotifications.permission.value === 'denied') return 'user.settings.pushDenied'
  return browserPushEnabled.value ? 'user.settings.pushEnabled' : 'user.settings.pushDisabled'
})
const canAddKeyword = computed(() => {
  const keyword = normalizedKeyword.value
  return keyword.length > 0
    && keyword.length <= 50
    && !keywordSubscriptions.value.some((subscription) => subscription.keyword === keyword)
    && !keywordPending.value
})
const notificationOptions = computed(() => NOTIFICATION_TYPES.map((type) => ({
  type,
  label: t(`user.settings.notificationTypes.${type}.label`),
  description: t(`user.settings.notificationTypes.${type}.description`),
})))

const {
  canSave: canSaveGeneral,
  form: userSettingsForm,
  isError: generalIsError,
  message: generalMessage,
  save: saveGeneralSettings
} = useUserSettingsForm({
  settingsData,
  isSaving: isUpdatingSettings,
  themeIsDark: () => themeStore.isDark,
  updateSettings,
  setTheme: themeStore.setTheme,
  t
})

const {
  canSave: canSaveNotifications,
  isError: notificationIsError,
  message: notificationMessage,
  save: saveNotificationSettings,
  settings: notificationSettings
} = useNotificationSettingsForm({
  notificationData,
  isSaving: isUpdatingNotifications,
  updateNotificationSettings,
  t
})

const setKeywordMessage = (messageKey: string, isError = false) => {
  keywordMessage.value = t(messageKey)
  keywordIsError.value = isError
}

const addKeyword = async () => {
  if (!keywordValidation.validateAll(keywordValidationValues.value)) return
  const keyword = normalizedKeyword.value
  if (!keyword) {
    setKeywordMessage('user.settings.keywordRequired', true)
    return
  }
  if (keyword.length > 50) {
    setKeywordMessage('user.settings.keywordTooLong', true)
    return
  }
  if (keywordSubscriptions.value.some((subscription) => subscription.keyword === keyword)) {
    setKeywordMessage('user.settings.keywordDuplicate', true)
    return
  }

  try {
    await createKeywordSubscription({ keyword })
    keywordInput.value = ''
    setKeywordMessage('user.settings.keywordAdded')
  } catch {
    setKeywordMessage('user.settings.keywordAddFailed', true)
  }
}

const removeKeyword = async (keyword: string) => {
  try {
    await deleteKeywordSubscription({ keyword })
    setKeywordMessage('user.settings.keywordRemoved')
  } catch {
    setKeywordMessage('user.settings.keywordRemoveFailed', true)
  }
}

const setPushMessage = (messageKey: string, isError = false) => {
  pushMessage.value = t(messageKey)
  pushIsError.value = isError
}

const enableBrowserPush = async () => {
  try {
    await pushNotifications.enablePush()
    setPushMessage('user.settings.pushEnableSuccess')
  } catch {
    setPushMessage('user.settings.pushEnableFailed', true)
  }
}

const disableBrowserPush = async () => {
  try {
    await pushNotifications.disablePush()
    setPushMessage('user.settings.pushDisableSuccess')
  } catch {
    setPushMessage('user.settings.pushDisableFailed', true)
  }
}

const handleRevokeSession = async (session: { sessionId: number, current: boolean }) => {
  const ok = await confirm(
    session.current ? t('user.settings.sessions.confirmCurrent') : t('user.settings.sessions.confirmOne'),
    t('user.settings.sessions.confirmTitle'),
  )
  if (!ok) return

  await revokeSession(session.sessionId)
  toastStore.addToast(t('user.settings.sessions.revoked'), 'success')
  if (session.current) {
    authStore.clearSessionState()
  }
}

const handleRevokeOtherSessions = async () => {
  const ok = await confirm(
    t('user.settings.sessions.confirmOthers'),
    t('user.settings.sessions.confirmTitle'),
  )
  if (!ok) return

  await revokeOtherSessions()
  toastStore.addToast(t('user.settings.sessions.revokedOthers'), 'success')
}
</script>

<template>
  <div class="mx-auto max-w-2xl">
    <div v-if="loading" class="text-center py-10">
      <BaseSpinner />
    </div>

    <div v-else class="nv-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 border-b nv-border flex items-center">
        <Settings class="h-5 w-5 mr-2 nv-text-subtle" />
        <h3 class="text-lg leading-6 font-medium nv-title">{{ $t('common.settings') }}</h3>
      </div>
      <div class="px-4 py-5 sm:p-6 space-y-6">
        <BaseSegmentedControl
          :model-value="activeSection"
          :options="settingsSectionOptions"
          :label="$t('user.settings.sectionNavigation')"
          selection-mode="tab"
          variant="joined"
          @update:model-value="selectSection"
        />

        <section v-show="activeSection === 'general'" id="general" aria-labelledby="settings-general-heading">
          <h3 id="settings-general-heading" class="text-lg font-medium leading-6 nv-title">{{ $t('user.settings.general') }}</h3>
          <div class="mt-4 space-y-4">
            <div>
              <BaseSelect
                v-model="userSettingsForm.theme"
                :label="$t('user.settings.theme')"
              >
                <option value="LIGHT">{{ $t('user.settings.light') }}</option>
                <option value="DARK">{{ $t('user.settings.dark') }}</option>
              </BaseSelect>
            </div>

            <div>
              <BaseSelect
                v-model="userSettingsForm.language"
                :label="$t('user.settings.language')"
              >
                <option value="ko">{{ $t('common.languages.ko') }}</option>
                <option value="en">{{ $t('common.languages.en') }}</option>
              </BaseSelect>
            </div>
          </div>
          <div class="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
            <p
              v-if="generalMessage"
              class="text-sm flex items-center"
              :role="generalIsError ? 'alert' : 'status'"
              :aria-live="generalIsError ? undefined : 'polite'"
              :class="generalIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
            >
              {{ generalMessage }}
            </p>
            <BaseButton @click="saveGeneralSettings" :loading="savingGeneral" :disabled="!canSaveGeneral">
              {{ savingGeneral ? $t('user.settings.saving') : $t('user.settings.save') }}
            </BaseButton>
          </div>
        </section>

        <section v-show="activeSection === 'notifications'" id="notifications" aria-labelledby="settings-notifications-heading">
          <h3 id="settings-notifications-heading" class="text-lg font-medium leading-6 nv-title">
            {{ $t('user.settings.notifications') }}
          </h3>
          <div class="mt-4 space-y-4">
            <BaseCheckbox
              v-for="option in notificationOptions"
              :id="`notification-${option.type.toLowerCase()}`"
              :key="option.type"
              v-model="notificationSettings[option.type]"
              :label="option.label"
              :description="option.description"
            />
          </div>
          <div class="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
            <p
              v-if="notificationMessage"
              class="text-sm flex items-center"
              :role="notificationIsError ? 'alert' : 'status'"
              :aria-live="notificationIsError ? undefined : 'polite'"
              :class="notificationIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
            >
              {{ notificationMessage }}
            </p>
            <BaseButton
              @click="saveNotificationSettings"
              :loading="savingNotifications"
              :disabled="!canSaveNotifications"
            >
              {{ savingNotifications ? $t('user.settings.saving') : $t('user.settings.save') }}
            </BaseButton>
          </div>

          <div class="mt-8 border-t nv-border pt-6">
            <div class="flex flex-col gap-1">
              <h4 class="text-base font-semibold nv-title">{{ $t('user.settings.keywordSubscriptions') }}</h4>
              <p class="text-sm nv-text-subtle">{{ $t('user.settings.keywordSubscriptionsDesc') }}</p>
              <p
                v-if="!keywordNotificationEnabled"
                class="text-sm text-[var(--nv-warning-text)]"
                role="status"
                aria-live="polite"
              >
                {{ $t('user.settings.keywordNotificationsDisabled') }}
              </p>
            </div>

            <form class="mt-4 flex flex-col gap-3 sm:flex-row" @submit.prevent="addKeyword">
              <BaseInput
                v-model="keywordInput"
                id="keyword-subscription-input"
                class="flex-1"
                :label="$t('user.settings.keywordInput')"
                :placeholder="$t('user.settings.keywordPlaceholder')"
                :disabled="keywordPending"
                maxlength="50"
                :error="keywordValidation.visibleError('keyword')"
                @blur="keywordValidation.touchField('keyword', keywordValidationValues)"
              />
              <BaseButton type="submit" :loading="isCreatingKeyword" :disabled="!canAddKeyword">
                {{ $t('user.settings.keywordAdd') }}
              </BaseButton>
            </form>

            <p
              v-if="keywordMessage"
              class="mt-3 text-sm"
              :role="keywordIsError ? 'alert' : 'status'"
              :aria-live="keywordIsError ? undefined : 'polite'"
              :class="keywordIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
            >
              {{ keywordMessage }}
            </p>

            <div v-if="isKeywordLoading" class="mt-4">
              <BaseSpinner />
            </div>
            <p v-else-if="keywordSubscriptions.length === 0" class="mt-4 text-sm nv-text-subtle">
              {{ $t('user.settings.keywordEmpty') }}
            </p>
            <ul v-else class="mt-4 flex flex-wrap gap-2" :aria-label="$t('user.settings.keywordList')">
              <li
                v-for="subscription in keywordSubscriptions"
                :key="subscription.subscriptionId"
                class="inline-flex items-center gap-2 rounded-full border nv-border px-3 py-1.5 text-sm nv-text"
              >
                <span>{{ subscription.keyword }}</span>
                <button
                  type="button"
                  class="nv-text-subtle hover:text-[var(--nv-text)]"
                  :aria-label="$t('user.settings.keywordRemove', { keyword: subscription.keyword })"
                  :disabled="isDeletingKeyword"
                  @click="removeKeyword(subscription.keyword)"
                >
                  x
                </button>
              </li>
            </ul>
          </div>

          <div class="mt-8 border-t nv-border pt-6">
            <div class="flex flex-col gap-1">
              <h4 class="text-base font-semibold nv-title">{{ $t('user.settings.browserPush') }}</h4>
              <p class="text-sm nv-text-subtle">{{ $t('user.settings.browserPushDesc') }}</p>
              <p class="text-sm nv-text-subtle" role="status" aria-live="polite">
                {{ $t(pushStatusKey) }}
              </p>
            </div>
            <div class="mt-4 flex flex-wrap gap-2">
              <BaseButton
                type="button"
                :loading="pushNotifications.isEnabling.value"
                :disabled="pushPending || browserPushEnabled || !pushNotifications.supported.value || !pushNotifications.enabled.value"
                @click="enableBrowserPush"
              >
                {{ $t('user.settings.pushEnable') }}
              </BaseButton>
              <BaseButton
                type="button"
                variant="secondary"
                :loading="pushNotifications.isDisabling.value"
                :disabled="pushPending || !browserPushEnabled"
                @click="disableBrowserPush"
              >
                {{ $t('user.settings.pushDisable') }}
              </BaseButton>
            </div>
            <p
              v-if="pushMessage"
              class="mt-3 text-sm"
              :role="pushIsError ? 'alert' : 'status'"
              :aria-live="pushIsError ? undefined : 'polite'"
              :class="pushIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
            >
              {{ pushMessage }}
            </p>
          </div>
        </section>

        <section v-show="activeSection === 'security'" id="security" aria-labelledby="settings-security-heading">
          <div>
            <div class="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h3 id="settings-security-heading" class="text-lg font-semibold nv-title">{{ $t('user.settings.sessions.title') }}</h3>
                <p class="text-sm nv-text-subtle">{{ $t('user.settings.sessions.description') }}</p>
              </div>
              <BaseButton
                class="w-full sm:w-auto"
                size="sm"
                variant="secondary"
                :loading="isRevokingOtherSessions"
                :disabled="sessions.length <= 1 || isRevokingSession"
                @click="handleRevokeOtherSessions"
              >
                {{ $t('user.settings.sessions.logoutOthers') }}
              </BaseButton>
            </div>
            <div class="mt-4 space-y-3">
              <div
                v-for="session in sessions"
                :key="session.sessionId"
                class="rounded-[var(--nv-radius-sm)] border nv-border p-3"
              >
                <div class="flex flex-col items-start gap-3 sm:flex-row sm:justify-between">
                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                      <Monitor class="h-4 w-4 nv-text-subtle" />
                      <p class="font-medium nv-title">{{ session.deviceSummary }}</p>
                      <span
                        v-if="session.current"
                        class="rounded-full bg-[var(--nv-accent-soft)] px-2 py-0.5 text-xs nv-accent-text"
                      >
                        {{ $t('user.settings.sessions.current') }}
                      </span>
                    </div>
                    <p class="mt-1 text-sm nv-text-subtle">
                      {{ session.ipAddress }} · {{ formatDateTimeOrDash(session.lastUsedAt, currentLocale) }}
                    </p>
                  </div>
                  <BaseButton
                    size="sm"
                    variant="danger"
                    :loading="isRevokingSession"
                    @click="handleRevokeSession(session)"
                  >
                    {{ $t('common.logout') }}
                  </BaseButton>
                </div>
              </div>
              <p v-if="sessions.length === 0" class="text-sm nv-text-subtle">
                {{ $t('user.settings.sessions.empty') }}
              </p>
            </div>

            <button
              type="button"
              class="mt-4 text-sm font-medium nv-accent-text"
              @click="showLoginHistory = !showLoginHistory"
            >
              {{ showLoginHistory ? $t('user.settings.sessions.hideHistory') : $t('user.settings.sessions.showHistory') }}
            </button>
            <div v-if="showLoginHistory" class="mt-3 space-y-2">
              <BaseSpinner v-if="isLoginHistoryLoading" />
              <template v-else>
                <div
                  v-for="history in loginHistory"
                  :key="history.historyId"
                  class="flex flex-col gap-1 rounded-[var(--nv-radius-sm)] border nv-border px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between"
                >
                  <span class="truncate nv-title">{{ history.deviceSummary }}</span>
                  <span class="shrink-0 nv-text-subtle">
                    {{ history.ipAddress }} · {{ formatDateTimeOrDash(history.createdAt, currentLocale) }}
                  </span>
                </div>
              </template>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

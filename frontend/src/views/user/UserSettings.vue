<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'
import { useUser } from '@/composables/useUser'
import {
  NOTIFICATION_TYPES,
  useNotificationSettingsForm,
  useUserSettingsForm
} from '@/composables/useUserSettingsForm'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { Settings } from 'lucide-vue-next'

const { t } = useI18n()
const {
  useUserSettings,
  useNotificationSettings,
  useUpdateUserSettings,
  useUpdateNotificationSettings,
  useKeywordSubscriptions,
  useCreateKeywordSubscription,
  useDeleteKeywordSubscription
} = useUser()
const themeStore = useThemeStore()

const { data: settingsData, isLoading: isSettingsLoading } = useUserSettings()
const { data: notificationData, isLoading: isNotifLoading } = useNotificationSettings()
const { data: keywordData, isLoading: isKeywordLoading } = useKeywordSubscriptions()
const { mutateAsync: updateSettings, isPending: isUpdatingSettings } = useUpdateUserSettings()
const { mutateAsync: updateNotificationSettings, isPending: isUpdatingNotifications } = useUpdateNotificationSettings()
const { mutateAsync: createKeywordSubscription, isPending: isCreatingKeyword } = useCreateKeywordSubscription()
const { mutateAsync: deleteKeywordSubscription, isPending: isDeletingKeyword } = useDeleteKeywordSubscription()

const keywordInput = ref('')
const keywordMessage = ref('')
const keywordIsError = ref(false)

const loading = computed(() => isSettingsLoading.value || isNotifLoading.value)
const savingGeneral = computed(() => isUpdatingSettings.value)
const savingNotifications = computed(() => isUpdatingNotifications.value)
const keywordSubscriptions = computed(() => keywordData.value ?? [])
const normalizedKeyword = computed(() => keywordInput.value.trim())
const keywordNotificationEnabled = computed(() => notificationSettings.KEYWORD !== false)
const keywordPending = computed(() => isKeywordLoading.value || isCreatingKeyword.value || isDeletingKeyword.value)
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
</script>

<template>
  <div class="max-w-2xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div v-if="loading" class="text-center py-10">
      <BaseSpinner />
    </div>

    <div v-else class="nv-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 border-b nv-border flex items-center">
        <Settings class="h-5 w-5 mr-2 nv-text-subtle" />
        <h3 class="text-lg leading-6 font-medium nv-title">{{ $t('common.settings') }}</h3>
      </div>
      <div class="px-4 py-5 sm:p-6 space-y-6">
        <div>
          <h3 class="text-lg font-medium leading-6 nv-title">{{ $t('user.settings.general') }}</h3>
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
          <div class="mt-5 flex justify-end">
            <p
              v-if="generalMessage"
              class="mr-4 text-sm flex items-center"
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
        </div>

        <hr class="nv-border" />

        <div>
          <h3 class="text-lg font-medium leading-6 nv-title">
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
          <div class="mt-5 flex justify-end">
            <p
              v-if="notificationMessage"
              class="mr-4 text-sm flex items-center"
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
        </div>
      </div>
    </div>
  </div>
</template>

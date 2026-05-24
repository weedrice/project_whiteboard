<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'
import { useUser } from '@/composables/useUser'
import type { NotificationSettingType, NotificationSettingsPayload } from '@/api/user'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { Settings } from 'lucide-vue-next'
import logger from '@/utils/logger'
import type { UserSettings } from '@/types'

const { t } = useI18n()
const {
  useUserSettings,
  useNotificationSettings,
  useUpdateUserSettings,
  useUpdateNotificationSettings
} = useUser()
const themeStore = useThemeStore()

const { data: settingsData, isLoading: isSettingsLoading } = useUserSettings()
const { data: notificationData, isLoading: isNotifLoading } = useNotificationSettings()
const { mutateAsync: updateSettings, isPending: isUpdatingSettings } = useUpdateUserSettings()
const { mutateAsync: updateNotificationSettings, isPending: isUpdatingNotifications } = useUpdateNotificationSettings()

const loading = computed(() => isSettingsLoading.value || isNotifLoading.value)
const savingGeneral = computed(() => isUpdatingSettings.value)
const savingNotifications = computed(() => isUpdatingNotifications.value)
const canSaveGeneral = computed(() => isGeneralDirty.value && !savingGeneral.value)
const canSaveNotifications = computed(() => isNotificationsDirty.value && !savingNotifications.value)
const generalMessage = ref('')
const generalIsError = ref(false)
const notificationMessage = ref('')
const notificationIsError = ref(false)
const hasInitializedGeneral = ref(false)
const hasInitializedNotifications = ref(false)
const isHydratingGeneral = ref(false)
const isHydratingNotifications = ref(false)
const isGeneralDirty = ref(false)
const isNotificationsDirty = ref(false)

const NOTIFICATION_TYPES: NotificationSettingType[] = ['LIKE', 'COMMENT', 'REPLY']

const userSettingsForm = reactive<{
  theme: 'LIGHT' | 'DARK'
  language: string
  timezone: string
  hideNsfw: boolean
}>({
  theme: 'LIGHT',
  language: 'ko',
  timezone: 'Asia/Seoul',
  hideNsfw: true
})

const notificationSettings = reactive<Record<NotificationSettingType, boolean>>({
  LIKE: true,
  COMMENT: true,
  REPLY: true
})

watch(settingsData, (value) => {
  if (!value || (hasInitializedGeneral.value && isGeneralDirty.value)) {
    return
  }

  isHydratingGeneral.value = true
  Object.assign(userSettingsForm, value)
  hasInitializedGeneral.value = true
  isHydratingGeneral.value = false
}, { immediate: true })

watch(notificationData, (value) => {
  if (!value || (hasInitializedNotifications.value && isNotificationsDirty.value)) {
    return
  }

  isHydratingNotifications.value = true
  for (const type of NOTIFICATION_TYPES) {
    notificationSettings[type] = value.find(
      (setting: NotificationSettingsPayload) => setting.notificationType === type
    )?.isEnabled ?? true
  }
  hasInitializedNotifications.value = true
  isHydratingNotifications.value = false
}, { immediate: true })

watch(userSettingsForm, () => {
  if (!isHydratingGeneral.value && hasInitializedGeneral.value) {
    isGeneralDirty.value = true
  }
}, { deep: true })

watch(notificationSettings, () => {
  if (!isHydratingNotifications.value && hasInitializedNotifications.value) {
    isNotificationsDirty.value = true
  }
}, { deep: true })

watch(() => themeStore.isDark, (isDark) => {
  if (userSettingsForm.theme !== (isDark ? 'DARK' : 'LIGHT')) {
    userSettingsForm.theme = isDark ? 'DARK' : 'LIGHT'
  }
})

const saveGeneralSettings = async () => {
  if (!canSaveGeneral.value) {
    return
  }

  generalMessage.value = ''
  generalIsError.value = false
  try {
    await updateSettings({
      theme: userSettingsForm.theme,
      language: userSettingsForm.language as UserSettings['language'],
      timezone: userSettingsForm.timezone,
      hideNsfw: userSettingsForm.hideNsfw
    })

    themeStore.setTheme(userSettingsForm.theme)
    isGeneralDirty.value = false
    generalMessage.value = t('user.settings.saved')
  } catch (error: unknown) {
    logger.error('Failed to save general settings:', error)
    generalMessage.value = t('user.settings.failed')
    generalIsError.value = true
  }
}

const saveNotificationSettings = async () => {
  if (!canSaveNotifications.value) {
    return
  }

  notificationMessage.value = ''
  notificationIsError.value = false
  try {
    await updateNotificationSettings({
      settings: NOTIFICATION_TYPES.map((notificationType) => ({
        notificationType,
        isEnabled: notificationSettings[notificationType]
      }))
    })

    isNotificationsDirty.value = false
    notificationMessage.value = t('user.settings.saved')
  } catch (error: unknown) {
    logger.error('Failed to save notification settings:', error)
    notificationMessage.value = t('user.settings.failed')
    notificationIsError.value = true
  }
}
</script>

<template>
  <div class="max-w-2xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div v-if="loading" class="text-center py-10">
      <BaseSpinner />
    </div>

    <div v-else class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
        <Settings class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400" />
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('common.settings') }}</h3>
      </div>
      <div class="px-4 py-5 sm:p-6 space-y-6">
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.settings.general') }}</h3>
          <div class="mt-4 space-y-4">
            <div>
              <BaseSelect
                v-model="userSettingsForm.theme"
                :label="$t('user.settings.theme')"
                inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600"
              >
                <option value="LIGHT">{{ $t('user.settings.light') }}</option>
                <option value="DARK">{{ $t('user.settings.dark') }}</option>
              </BaseSelect>
            </div>

            <div>
              <BaseSelect
                v-model="userSettingsForm.language"
                :label="$t('user.settings.language')"
                inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600"
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
              :class="generalIsError ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'"
            >
              {{ generalMessage }}
            </p>
            <BaseButton @click="saveGeneralSettings" :loading="savingGeneral" :disabled="!canSaveGeneral">
              {{ savingGeneral ? $t('user.settings.saving') : $t('user.settings.save') }}
            </BaseButton>
          </div>
        </div>

        <hr class="border-gray-200 dark:border-gray-700" />

        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">
            {{ $t('user.settings.notifications') }}
          </h3>
          <div class="mt-4 space-y-4">
            <BaseCheckbox
              id="notification-like"
              v-model="notificationSettings.LIKE"
              :label="$t('user.settings.like')"
              :description="$t('user.settings.likeDesc')"
            />
            <BaseCheckbox
              id="notification-comment"
              v-model="notificationSettings.COMMENT"
              :label="$t('user.settings.comment')"
              :description="$t('user.settings.commentDesc')"
            />
            <BaseCheckbox
              id="notification-reply"
              v-model="notificationSettings.REPLY"
              :label="$t('user.settings.reply')"
              :description="$t('user.settings.replyDesc')"
            />
          </div>
          <div class="mt-5 flex justify-end">
            <p
              v-if="notificationMessage"
              class="mr-4 text-sm flex items-center"
              :role="notificationIsError ? 'alert' : 'status'"
              :aria-live="notificationIsError ? undefined : 'polite'"
              :class="notificationIsError ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'"
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
        </div>
      </div>
    </div>
  </div>
</template>

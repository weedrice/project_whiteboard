<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'
import { useUser } from '@/composables/useUser'
import {
  useNotificationSettingsForm,
  useUserSettingsForm
} from '@/composables/useUserSettingsForm'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { Settings } from 'lucide-vue-next'

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

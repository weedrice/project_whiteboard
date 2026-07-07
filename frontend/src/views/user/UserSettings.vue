<script setup lang="ts">
import { computed } from 'vue'
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, watchEffect, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import logger from '@/utils/logger'
import { useUser } from '@/composables/useUser'

import { useThemeStore } from '@/stores/theme'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { useUserSettings, useUpdateUserSettings, useNotificationSettings, useUpdateNotificationSettings } = useUser()
const themeStore = useThemeStore()

const { data: settingsData, isLoading: isSettingsLoading } = useUserSettings()
const { data: notificationData, isLoading: isNotifLoading } = useNotificationSettings()
const { mutateAsync: updateSettings, isPending: isUpdatingSettings } = useUpdateUserSettings()
const { mutateAsync: updateNotif, isPending: isUpdatingNotif } = useUpdateNotificationSettings()


const loading = computed(() => isSettingsLoading.value || isNotifLoading.value)
const saving = computed(() => isUpdatingSettings.value || isUpdatingNotif.value)
const message = ref('')
const isError = ref(false)

const userSettingsForm = reactive({
  theme: 'LIGHT',
  language: 'ko',
  timezone: 'Asia/Seoul',
  hideNsfw: true
})

const notificationSettings = reactive({
  emailNotifications: true,
  pushNotifications: true
})



// Sync data when loaded
watchEffect(() => {
  if (settingsData.value) {
    Object.assign(userSettingsForm, settingsData.value)
  }
  if (notificationData.value) {
    const notifList = notificationData.value
    const emailSetting = notifList.find(s => s.notificationType === 'EMAIL')
    const pushSetting = notifList.find(s => s.notificationType === 'PUSH')

    if (emailSetting) notificationSettings.emailNotifications = emailSetting.isEnabled
    if (pushSetting) notificationSettings.pushNotifications = pushSetting.isEnabled
  }
})

// Sync with global theme store (e.g. when toggled from footer)
watch(() => themeStore.isDark, (isDark) => {
  userSettingsForm.theme = isDark ? 'DARK' : 'LIGHT'
})

const saveSettings = async () => {
  message.value = ''
  isError.value = false
  try {
    // Update General Settings
    await updateSettings({
      theme: userSettingsForm.theme,
      language: userSettingsForm.language,
      timezone: userSettingsForm.timezone,
      hideNsfw: userSettingsForm.hideNsfw
    })

    // Update Notification Settings
    await Promise.all([
      updateNotif({
        notificationType: 'EMAIL',
        isEnabled: notificationSettings.emailNotifications
      }),
      updateNotif({
        notificationType: 'PUSH',
        isEnabled: notificationSettings.pushNotifications
      })
    ])

    // Update theme immediately
    themeStore.setTheme(userSettingsForm.theme)

    message.value = t('user.settings.saved')
  } catch (error) {
    logger.error('Failed to save settings:', error)
    message.value = t('user.settings.failed')
    isError.value = true
  }
}


</script>

<template>
  <div class="max-w-2xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div v-if="loading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- General Settings -->
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.settings.general') }}</h3>
          <div class="mt-4 space-y-4">

            <!-- Theme -->
            <div>
              <BaseSelect v-model="userSettingsForm.theme" :label="$t('user.settings.theme')"
                inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
                <option value="LIGHT">{{ $t('user.settings.light') }}</option>
                <option value="DARK">{{ $t('user.settings.dark') }}</option>
              </BaseSelect>
            </div>

            <!-- Language -->
            <div>
              <BaseSelect v-model="userSettingsForm.language" :label="$t('user.settings.language')"
                inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
                <option value="ko">{{ $t('common.languages.ko') }}</option>
                <option value="en">{{ $t('common.languages.en') }}</option>
              </BaseSelect>
            </div>

          </div>
        </div>

        <hr class="border-gray-200 dark:border-gray-700" />

        <!-- Notification Settings -->
        <!-- Notification Settings (Hidden) -->
        <!-- <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.settings.notifications')
            }}</h3>
          <div class="mt-4 space-y-4">
            <BaseCheckbox id="email_notifications" v-model="notificationSettings.emailNotifications"
              :label="$t('user.settings.email')" :description="$t('user.settings.emailDesc')" />
            <BaseCheckbox id="push_notifications" v-model="notificationSettings.pushNotifications"
              :label="$t('user.settings.push')" :description="$t('user.settings.pushDesc')" />
          </div>
        </div> -->
      </div>



      <div class="pt-5 px-4 sm:px-6 pb-5">
        <div class="flex justify-end">
          <p v-if="message" class="mr-4 text-sm flex items-center"
            :class="isError ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'">
            {{ message }}
          </p>
          <BaseButton @click="saveSettings" :loading="saving">
            {{ saving ? $t('user.settings.saving') : $t('user.settings.save') }}
          </BaseButton>
        </div>
      </div>
    </div>
  </div>


</template>

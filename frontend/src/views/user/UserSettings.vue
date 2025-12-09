<script setup>
import { ref, onMounted, reactive, watchEffect, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUser } from '@/composables/useUser'
import BaseButton from '@/components/common/BaseButton.vue'
import logger from '@/utils/logger'

import { useThemeStore } from '@/stores/theme'

const { t } = useI18n()
const { useUserSettings, useUpdateUserSettings, useNotificationSettings, useUpdateNotificationSettings } = useUser()
const themeStore = useThemeStore()

const { data: settingsData, isLoading: isSettingsLoading } = useUserSettings()
const { data: notificationData, isLoading: isNotifLoading } = useNotificationSettings()
const { mutateAsync: updateSettings, isPending: isUpdatingSettings } = useUpdateUserSettings()
const { mutateAsync: updateNotif, isPending: isUpdatingNotif } = useUpdateNotificationSettings()

const loading = computed(() => isSettingsLoading.value || isNotifLoading.value)
const saving = computed(() => isUpdatingSettings.value || isUpdatingNotif.value)
const message = ref('')

const settings = reactive({
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
        Object.assign(settings, settingsData.value)
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
    settings.theme = isDark ? 'DARK' : 'LIGHT'
})

const saveSettings = async () => {
  message.value = ''
  try {
    // Update General Settings
    await updateSettings({
        theme: settings.theme,
        language: settings.language,
        timezone: settings.timezone,
        hideNsfw: settings.hideNsfw
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
    themeStore.setTheme(settings.theme)

    message.value = t('user.settings.saved')
  } catch (error) {
    logger.error('설정 저장 실패:', error)
    message.value = t('user.settings.failed')
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
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('user.settings.theme') }}</label>
                <select v-model="settings.theme" class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 dark:border-gray-600 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                    <option value="LIGHT">{{ $t('user.settings.light') }}</option>
                    <option value="DARK">{{ $t('user.settings.dark') }}</option>
                </select>
            </div>

            <!-- Language -->
             <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('user.settings.language') }}</label>
                <select v-model="settings.language" class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 dark:border-gray-600 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                    <option value="ko">한국어</option>
                    <option value="en">English</option>
                </select>
            </div>

             <!-- NSFW -->
             <!-- <div class="flex items-start pt-2">
                <div class="flex items-center h-5">
                    <input id="hideNsfw" v-model="settings.hideNsfw" true-value="Y" false-value="N" type="checkbox" class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded" />
                </div>
                <div class="ml-3 text-sm">
                    <label for="hideNsfw" class="font-medium text-gray-700">Hide NSFW Content</label>
                </div>
             </div> -->

          </div>
        </div>

        <hr class="border-gray-200 dark:border-gray-700" />

        <!-- Notification Settings -->
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.settings.notifications') }}</h3>
          <div class="mt-4 space-y-4">
            <div class="flex items-start">
              <div class="flex items-center h-5">
                <input
                  id="email_notifications"
                  v-model="notificationSettings.emailNotifications"
                  type="checkbox"
                  class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-700"
                />
              </div>
              <div class="ml-3 text-sm">
                <label for="email_notifications" class="font-medium text-gray-700 dark:text-gray-300">{{ $t('user.settings.email') }}</label>
                <p class="text-gray-500 dark:text-gray-400">{{ $t('user.settings.emailDesc') }}</p>
              </div>
            </div>
            <div class="flex items-start">
              <div class="flex items-center h-5">
                <input
                  id="push_notifications"
                  v-model="notificationSettings.pushNotifications"
                  type="checkbox"
                  class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-700"
                />
              </div>
              <div class="ml-3 text-sm">
                <label for="push_notifications" class="font-medium text-gray-700 dark:text-gray-300">{{ $t('user.settings.push') }}</label>
                <p class="text-gray-500 dark:text-gray-400">{{ $t('user.settings.pushDesc') }}</p>
              </div>
            </div>
          </div>
        </div>

        <div class="pt-5">
          <div class="flex justify-end">
            <p v-if="message" class="mr-4 text-sm flex items-center" :class="message.includes('실패') ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'">
              {{ message }}
            </p>
            <BaseButton @click="saveSettings" :disabled="saving">
              {{ saving ? $t('user.settings.saving') : $t('user.settings.save') }}
            </BaseButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
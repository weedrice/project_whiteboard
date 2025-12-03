<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { userApi } from '@/api/user'
import BaseButton from '@/components/common/BaseButton.vue'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
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

const fetchSettings = async () => {
  loading.value = true
  try {
    // Fetch general settings
    const settingsRes = await userApi.getUserSettings()
    if (settingsRes.data.success) {
      Object.assign(settings, settingsRes.data.data)
    }

    // Fetch notification settings
    const notifRes = await userApi.getNotificationSettings()
    if (notifRes.data.success) {
      const notifList = notifRes.data.data
      const emailSetting = notifList.find(s => s.notificationType === 'EMAIL')
      const pushSetting = notifList.find(s => s.notificationType === 'PUSH')
      
      if (emailSetting) notificationSettings.emailNotifications = emailSetting.isEnabled === 'Y'
      if (pushSetting) notificationSettings.pushNotifications = pushSetting.isEnabled === 'Y'
    }
  } catch (error) {
    console.error('설정을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  message.value = ''
  try {
    await userApi.updateUserSettings({
        theme: settings.theme,
        language: settings.language,
        timezone: settings.timezone,
        hideNsfw: settings.hideNsfw
    })

    await userApi.updateNotificationSettings({
        notificationType: 'EMAIL',
        isEnabled: notificationSettings.emailNotifications ? 'Y' : 'N'
    })
    
    await userApi.updateNotificationSettings({
        notificationType: 'PUSH',
        isEnabled: notificationSettings.pushNotifications ? 'Y' : 'N'
    })

    message.value = t('user.settings.saved')
  } catch (error) {
    console.error('설정 저장 실패:', error)
    message.value = t('user.settings.failed')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchSettings()
})
</script>

<template>
  <div class="max-w-2xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div v-if="loading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white shadow overflow-hidden sm:rounded-lg">
      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- General Settings -->
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900">{{ $t('user.settings.general') }}</h3>
          <div class="mt-4 space-y-4">
            
            <!-- Theme -->
            <div>
                <label class="block text-sm font-medium text-gray-700">Theme</label>
                <select v-model="settings.theme" class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md">
                    <option value="LIGHT">Light</option>
                    <option value="DARK">Dark</option>
                </select>
            </div>

            <!-- Language -->
             <div>
                <label class="block text-sm font-medium text-gray-700">Language</label>
                <select v-model="settings.language" class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md">
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

        <hr class="border-gray-200" />

        <!-- Notification Settings -->
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900">{{ $t('user.settings.notifications') }}</h3>
          <div class="mt-4 space-y-4">
            <div class="flex items-start">
              <div class="flex items-center h-5">
                <input
                  id="email_notifications"
                  v-model="notificationSettings.emailNotifications"
                  type="checkbox"
                  class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
                />
              </div>
              <div class="ml-3 text-sm">
                <label for="email_notifications" class="font-medium text-gray-700">{{ $t('user.settings.email') }}</label>
                <p class="text-gray-500">{{ $t('user.settings.emailDesc') }}</p>
              </div>
            </div>
            <div class="flex items-start">
              <div class="flex items-center h-5">
                <input
                  id="push_notifications"
                  v-model="notificationSettings.pushNotifications"
                  type="checkbox"
                  class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
                />
              </div>
              <div class="ml-3 text-sm">
                <label for="push_notifications" class="font-medium text-gray-700">{{ $t('user.settings.push') }}</label>
                <p class="text-gray-500">{{ $t('user.settings.pushDesc') }}</p>
              </div>
            </div>
          </div>
        </div>

        <div class="pt-5">
          <div class="flex justify-end">
            <p v-if="message" class="mr-4 text-sm flex items-center" :class="message.includes('실패') ? 'text-red-600' : 'text-green-600'">
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
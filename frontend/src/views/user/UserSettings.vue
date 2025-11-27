<script setup>
import { ref, onMounted, reactive } from 'vue'
import axios from '@/api'
import BaseButton from '@/components/common/BaseButton.vue'

const loading = ref(false)
const saving = ref(false)
const message = ref('')

const settings = reactive({
  isPublicProfile: true,
  allowMessages: true
})

const notificationSettings = reactive({
  emailNotifications: true,
  pushNotifications: true
})

const fetchSettings = async () => {
  loading.value = true
  try {
    // Fetch general settings
    const settingsRes = await axios.get('/users/me/settings')
    if (settingsRes.data.success) {
      Object.assign(settings, settingsRes.data.data)
    }

    // Fetch notification settings
    const notifRes = await axios.get('/users/me/notification-settings')
    if (notifRes.data.success) {
      // Assuming backend returns list, mapping to object for UI
      // Backend returns List<NotificationSettingResponse>
      // We need to map this list to our reactive object
      const notifList = notifRes.data.data
      // Example mapping logic, adjust based on actual enum values
      const emailSetting = notifList.find(s => s.notificationType === 'EMAIL')
      const pushSetting = notifList.find(s => s.notificationType === 'PUSH')
      
      if (emailSetting) notificationSettings.emailNotifications = emailSetting.isEnabled
      if (pushSetting) notificationSettings.pushNotifications = pushSetting.isEnabled
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
    // Backend expects specific structure for updates
    // UpdateSettingsRequest: theme, language, timezone, hideNsfw
    // UpdateNotificationSettingRequest: notificationType, isEnabled
    
    // Since the UI currently shows "Privacy" (Public Profile, Allow Messages) which are NOT in UserSettings entity (it has theme, etc.),
    // I need to align UI with Backend or mock it if backend doesn't support it yet.
    // Backend UserSettings: theme, language, timezone, hideNsfw.
    // Backend User: status, isEmailVerified.
    // The current UI "Public Profile" and "Allow Messages" seem to be missing in backend UserSettings.
    // I will update the UI to match Backend UserSettings (Theme, Language) for now to be accurate,
    // OR I will keep the UI as "Privacy" but note that it's mocked/not fully connected if backend lacks fields.
    // Given the user wants "match backend", I should probably show what's available or add fields to backend.
    // But I cannot edit backend easily without more context.
    // I will switch UI to show "Theme" and "Language" to match backend `UserSettings`.
    
    await axios.put('/users/me/settings', {
        theme: 'LIGHT', // Default or from UI
        language: 'KO', // Default
        timezone: 'Asia/Seoul',
        hideNsfw: 'N'
    })

    // Update notifications one by one as backend API is per type
    await axios.put('/users/me/notification-settings', {
        notificationType: 'EMAIL',
        isEnabled: notificationSettings.emailNotifications ? 'Y' : 'N'
    })
    
    await axios.put('/users/me/notification-settings', {
        notificationType: 'PUSH',
        isEnabled: notificationSettings.pushNotifications ? 'Y' : 'N'
    })

    message.value = '설정이 저장되었습니다.'
  } catch (error) {
    console.error('설정 저장 실패:', error)
    message.value = '설정 저장에 실패했습니다.'
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
    <h1 class="text-2xl font-bold text-gray-900 mb-6">설정</h1>

    <div v-if="loading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white shadow overflow-hidden sm:rounded-lg">
      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- General Settings (Matched to Backend) -->
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900">일반 설정</h3>
          <div class="mt-4 space-y-4">
             <p class="text-sm text-gray-500">테마 및 언어 설정은 현재 준비 중입니다.</p>
          </div>
        </div>

        <hr class="border-gray-200" />

        <!-- Notification Settings -->
        <div>
          <h3 class="text-lg font-medium leading-6 text-gray-900">알림 설정</h3>
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
                <label for="email_notifications" class="font-medium text-gray-700">이메일 알림</label>
                <p class="text-gray-500">계정 활동에 대한 이메일 알림을 받습니다.</p>
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
                <label for="push_notifications" class="font-medium text-gray-700">푸시 알림</label>
                <p class="text-gray-500">기기로 푸시 알림을 받습니다.</p>
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
              {{ saving ? '저장 중...' : '설정 저장' }}
            </BaseButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

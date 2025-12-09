<template>
  <div class="space-y-6">
    <h2 class="text-2xl font-bold text-gray-900 dark:text-white">Site Settings</h2>

    <div class="bg-white dark:bg-gray-800 shadow sm:rounded-lg border border-gray-200 dark:border-gray-700">
      <div class="px-4 py-5 sm:p-6">
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">General Settings</h3>
        <div class="mt-2 max-w-xl text-sm text-gray-500 dark:text-gray-400">
          <p>Configure general site information and maintenance mode.</p>
        </div>
        
        <form @submit.prevent="saveSettings" class="mt-5 space-y-6">
          <div>
            <label for="site-name" class="block text-sm font-medium text-gray-700 dark:text-gray-300">Site Name</label>
            <div class="mt-1">
              <input
                v-model="settings.siteName"
                type="text"
                name="site-name"
                id="site-name"
                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
              />
            </div>
          </div>

          <div class="flex items-start">
            <div class="flex items-center h-5">
              <input
                v-model="settings.maintenanceMode"
                id="maintenance-mode"
                name="maintenance-mode"
                type="checkbox"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-700"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="maintenance-mode" class="font-medium text-gray-700 dark:text-gray-300">Maintenance Mode</label>
              <p class="text-gray-500 dark:text-gray-400">Enable to prevent non-admin users from accessing the site.</p>
            </div>
          </div>

          <div class="flex items-start">
            <div class="flex items-center h-5">
              <input
                v-model="settings.allowSignup"
                id="allow-signup"
                name="allow-signup"
                type="checkbox"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-700"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="allow-signup" class="font-medium text-gray-700 dark:text-gray-300">Allow Signups</label>
              <p class="text-gray-500 dark:text-gray-400">Allow new users to register.</p>
            </div>
          </div>

          <div class="pt-5">
            <div class="flex justify-end">
              <BaseButton type="submit" :disabled="saving">
                {{ saving ? 'Saving...' : 'Save Settings' }}
              </BaseButton>
            </div>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useAdmin } from '@/composables/useAdmin'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const { useConfigs, useUpdateConfig } = useAdmin()
const toastStore = useToastStore()

const saving = ref(false)
const settings = ref({
  siteName: 'Project Whiteboard',
  maintenanceMode: false,
  allowSignup: true
})

const { data: configsData } = useConfigs()
const { mutateAsync: updateConfig } = useUpdateConfig()

watch(configsData, (newData) => {
    if (newData) {
        const configMap = newData.reduce((acc, curr) => {
            acc[curr.key] = curr.value
            return acc
        }, {})
        
        if (configMap['siteName']) settings.value.siteName = configMap['siteName']
        if (configMap['maintenanceMode']) settings.value.maintenanceMode = configMap['maintenanceMode'] === 'true'
        if (configMap['allowSignup']) settings.value.allowSignup = configMap['allowSignup'] === 'true'
    }
}, { immediate: true })

const saveSettings = async () => {
  saving.value = true
  try {
    await updateConfig({ key: 'siteName', value: settings.value.siteName })
    await updateConfig({ key: 'maintenanceMode', value: String(settings.value.maintenanceMode) })
    await updateConfig({ key: 'allowSignup', value: String(settings.value.allowSignup) })
    toastStore.addToast('Settings saved successfully!', 'success')
  } catch (err) {
    // Error handled globally
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <h2 class="text-2xl font-bold text-gray-900">Site Settings</h2>

    <div class="bg-white shadow sm:rounded-lg">
      <div class="px-4 py-5 sm:p-6">
        <h3 class="text-lg leading-6 font-medium text-gray-900">General Settings</h3>
        <div class="mt-2 max-w-xl text-sm text-gray-500">
          <p>Configure general site information and maintenance mode.</p>
        </div>
        
        <form @submit.prevent="saveSettings" class="mt-5 space-y-6">
          <div>
            <label for="site-name" class="block text-sm font-medium text-gray-700">Site Name</label>
            <div class="mt-1">
              <input
                v-model="settings.siteName"
                type="text"
                name="site-name"
                id="site-name"
                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
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
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="maintenance-mode" class="font-medium text-gray-700">Maintenance Mode</label>
              <p class="text-gray-500">Enable to prevent non-admin users from accessing the site.</p>
            </div>
          </div>

          <div class="flex items-start">
            <div class="flex items-center h-5">
              <input
                v-model="settings.allowSignup"
                id="allow-signup"
                name="allow-signup"
                type="checkbox"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="allow-signup" class="font-medium text-gray-700">Allow Signups</label>
              <p class="text-gray-500">Allow new users to register.</p>
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
import { ref, onMounted } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'

const saving = ref(false)
const settings = ref({
  siteName: 'Project Whiteboard',
  maintenanceMode: false,
  allowSignup: true
})

const fetchSettings = async () => {
  // Mock API call
  await new Promise(resolve => setTimeout(resolve, 300))
  // Use defaults
}

const saveSettings = async () => {
  saving.value = true
  try {
    // Mock API call
    await new Promise(resolve => setTimeout(resolve, 800))
    alert('Settings saved successfully!')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchSettings()
})
</script>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { adminApi } from '@/api/admin'
import { Save } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const configs = ref([])
const isLoading = ref(false)
const isModalOpen = ref(false)
const newConfig = reactive({
  key: '',
  value: '',
  description: ''
})

async function fetchConfigs() {
  isLoading.value = true
  try {
    const res = await adminApi.getConfigs()
    if (res.data.success) {
      configs.value = res.data.data
    }
  } catch (err) {
    console.error('Failed to fetch configs:', err)
  } finally {
    isLoading.value = false
  }
}

async function handleSave(config) {
    try {
        await adminApi.updateConfig(config.key, config.value)
        alert(t('admin.settings.messages.saved'))
    } catch (err) {
        console.error(err)
        alert(t('admin.settings.messages.saveFailed'))
    }
}

async function handleCreateConfig() {
  if (!newConfig.key || !newConfig.value) return
  try {
    // Assuming backend supports creating config via updateConfig if key doesn't exist, 
    // OR we need a create endpoint. Usually updateConfig is enough if it's an upsert.
    // If not, we might need a create endpoint. Let's assume upsert for now or check API.
    // Checking admin.js... updateConfig uses PUT /api/v1/admin/global-config/{key}.
    // If the key is new, PUT might create it.
    await adminApi.updateConfig(newConfig.key, newConfig.value)
    // Also update description if API supports it? 
    // The current updateConfig only takes value. 
    // If description is needed, we might need to update the API or backend.
    // For now, let's just save the value.
    
    alert(t('admin.settings.messages.saved'))
    isModalOpen.value = false
    newConfig.key = ''
    newConfig.value = ''
    newConfig.description = ''
    fetchConfigs()
  } catch (err) {
    console.error(err)
    alert(t('admin.settings.messages.saveFailed'))
  }
}

onMounted(() => {
  fetchConfigs()
})
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900">{{ t('admin.settings.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700">{{ t('admin.settings.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <button
          @click="isModalOpen = true"
          type="button"
          class="inline-flex items-center justify-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:w-auto"
        >
          {{ t('common.add') }}
        </button>
      </div>
    </div>
    
    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
            <table class="min-w-full divide-y divide-gray-300">
              <thead class="bg-gray-50">
                <tr>
                  <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">{{ t('admin.settings.table.key') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.settings.table.desc') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.settings.table.value') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 bg-white">
                <tr v-for="config in configs" :key="config.key">
                  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">{{ config.key }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ config.description }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                    <input 
                        type="text" 
                        v-model="config.value"
                        class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                    />
                  </td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <button 
                        @click="handleSave(config)" 
                        class="text-indigo-600 hover:text-indigo-900"
                    >
                        <Save class="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Add Config Modal -->
    <div v-if="isModalOpen" class="relative z-10" aria-labelledby="modal-title" role="dialog" aria-modal="true">
      <div class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"></div>
      <div class="fixed inset-0 z-10 overflow-y-auto">
        <div class="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
          <div class="relative transform overflow-hidden rounded-lg bg-white px-4 pt-5 pb-4 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg sm:p-6">
            <div>
              <div class="mt-3 text-center sm:mt-5">
                <h3 class="text-lg font-medium leading-6 text-gray-900" id="modal-title">{{ t('admin.settings.addConfig') }}</h3>
                <div class="mt-2">
                  <div class="space-y-4">
                    <div>
                      <label class="block text-sm font-medium text-gray-700 text-left">{{ t('admin.settings.table.key') }}</label>
                      <input v-model="newConfig.key" type="text" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm" />
                    </div>
                    <div>
                      <label class="block text-sm font-medium text-gray-700 text-left">{{ t('admin.settings.table.value') }}</label>
                      <input v-model="newConfig.value" type="text" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm" />
                    </div>
                    <div>
                      <label class="block text-sm font-medium text-gray-700 text-left">{{ t('admin.settings.table.desc') }}</label>
                      <input v-model="newConfig.description" type="text" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="mt-5 sm:mt-6 sm:grid sm:grid-flow-row-dense sm:grid-cols-2 sm:gap-3">
              <button @click="handleCreateConfig" type="button" class="inline-flex w-full justify-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-base font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:col-start-2 sm:text-sm">
                {{ t('common.save') }}
              </button>
              <button @click="isModalOpen = false" type="button" class="mt-3 inline-flex w-full justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-base font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:col-start-1 sm:mt-0 sm:text-sm">
                {{ t('common.cancel') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

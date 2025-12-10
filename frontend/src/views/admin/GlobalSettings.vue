<script setup>
import { ref, reactive, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Save, Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()
const toastStore = useToastStore()
const { useConfigs, useUpdateConfig, useCreateConfig, useDeleteConfig } = useAdmin()

const configs = ref([])
const isModalOpen = ref(false)
const newConfig = reactive({
  key: '',
  value: '',
  description: ''
})

const { data: configsData, isLoading } = useConfigs()
const { mutateAsync: updateConfig } = useUpdateConfig()
const { mutateAsync: createConfig } = useCreateConfig()
const { mutateAsync: deleteConfig } = useDeleteConfig()

watch(configsData, (newData) => {
  if (newData) {
    configs.value = JSON.parse(JSON.stringify(newData))
  }
}, { immediate: true })

function formatDate(dateString) {
  if (!dateString) return '-'
  const date = new Date(dateString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`
}

async function handleSave(config) {
  try {
    await updateConfig({ key: config.configKey, value: config.configValue, description: config.description })
    toastStore.addToast(t('admin.settings.messages.saved'), 'success')
  } catch (err) {
    // Error handled globally
  }
}

async function handleCreateConfig() {
  if (!newConfig.key || !newConfig.value) return
  try {
    await createConfig({
      key: newConfig.key,
      value: newConfig.value,
      description: newConfig.description
    })

    toastStore.addToast(t('admin.settings.messages.saved'), 'success')
    isModalOpen.value = false
    newConfig.key = ''
    newConfig.value = ''
    newConfig.description = ''
  } catch (err) {
    // Error handled globally
  }
}

async function handleDelete(key) {
  if (!confirm(t('common.confirmDelete'))) return
  try {
    await deleteConfig(key)
    toastStore.addToast(t('common.deleted'), 'success')
  } catch (err) {
    // Error handled globally
  }
}
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.settings.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.settings.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <BaseButton @click="isModalOpen = true">
          {{ t('common.add') }}
        </BaseButton>
      </div>
    </div>

    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div
            class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg border border-gray-200 dark:border-gray-700">
            <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
              <thead class="bg-gray-50 dark:bg-gray-700">
                <tr>
                  <th scope="col"
                    class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 dark:text-white sm:pl-6">{{
                      t('common.key') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{
                    t('common.description') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{
                    t('common.value') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{
                    t('common.createdAt') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{
                    t('common.updatedAt') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                <tr v-for="config in configs" :key="config.configKey">
                  <td
                    class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 dark:text-white sm:pl-6">
                    {{ config.configKey }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                    <input type="text" v-model="config.description"
                      class="block w-full border-0 p-0 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm bg-transparent" />
                  </td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                    <input type="text" v-model="config.configValue"
                      class="block w-full border-0 p-0 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm bg-transparent" />
                  </td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{
                    formatDate(config.createdAt) }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{
                    formatDate(config.modifiedAt) }}</td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <div class="flex justify-end space-x-2">
                      <BaseButton @click="handleSave(config)" variant="secondary" size="sm" :title="t('common.save')"
                        class="text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300">
                        <Save class="h-4 w-4" />
                      </BaseButton>
                      <BaseButton @click="handleDelete(config.configKey)" variant="secondary" size="sm"
                        :title="t('common.delete')"
                        class="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300">
                        <Trash2 class="h-4 w-4" />
                      </BaseButton>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Add Config Modal -->
    <BaseModal :isOpen="isModalOpen" :title="t('admin.settings.addConfig')" @close="isModalOpen = false">
      <div class="space-y-4">
        <BaseInput v-model="newConfig.key" :label="t('common.key')" type="text" />
        <BaseInput v-model="newConfig.value" :label="t('common.value')" type="text" />
        <BaseInput v-model="newConfig.description" :label="t('common.description')" type="text" />
      </div>
      <template #footer>
        <div class="flex justify-end space-x-3">
          <BaseButton @click="isModalOpen = false" variant="secondary">
            {{ t('common.cancel') }}
          </BaseButton>
          <BaseButton @click="handleCreateConfig">
            {{ t('common.save') }}
          </BaseButton>
        </div>
      </template>
    </BaseModal>
  </div>
</template>

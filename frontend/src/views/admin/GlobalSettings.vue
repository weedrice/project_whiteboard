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
import BaseTable from '@/components/common/BaseTable.vue'
import { useConfirm } from '@/composables/useConfirm'


const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
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
  const isConfirmed = await confirm(t('common.confirmDelete'))
  if (!isConfirmed) return
  try {
    await deleteConfig(key)
    toastStore.addToast(t('common.deleted'), 'success')
  } catch (err) {
    // Error handled globally
  }
}

const columns = [
  { key: 'configKey', label: t('common.key'), width: '15%' },
  { key: 'description', label: t('common.description'), width: '25%' },
  { key: 'configValue', label: t('common.value'), width: '25%' },
  { key: 'createdAt', label: t('common.createdAt'), width: '15%' },
  { key: 'modifiedAt', label: t('common.updatedAt'), width: '15%' },
  { key: 'actions', label: '', align: 'right', width: '5%' }
]
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

    <div class="mt-8">
      <BaseTable :columns="columns" :items="configs" :loading="isLoading" :emptyText="t('common.noData')">
        <template #cell-description="{ item }">
          <BaseInput v-model="item.description" hideLabel
            inputClass="block w-full border-0 p-0 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm bg-transparent shadow-none" />
        </template>

        <template #cell-configValue="{ item }">
          <BaseInput v-model="item.configValue" hideLabel
            inputClass="block w-full border-0 p-0 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm bg-transparent shadow-none" />
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #cell-modifiedAt="{ item }">
          {{ formatDate(item.modifiedAt) }}
        </template>

        <template #cell-actions="{ item }">
          <div class="flex justify-end space-x-2">
            <BaseButton @click="handleSave(item)" variant="ghost" size="sm" :title="t('common.save')"
              class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300">
              <Save class="h-4 w-4" />
            </BaseButton>
            <BaseButton @click="handleDelete(item.configKey)" variant="ghost" size="sm" :title="t('common.delete')"
              class="p-1 text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300">
              <Trash2 class="h-4 w-4" />
            </BaseButton>
          </div>
        </template>
      </BaseTable>
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

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useConfigEditor } from '@/composables/useConfigEditor'
import { Save, Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { useToastStore } from '@/stores/toast'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import { useConfirm } from '@/composables/useConfirm'


const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useConfigs, useUpdateConfig, useCreateConfig, useDeleteConfig } = useAdmin()

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
const { configs, updateDraft, getDraft } = useConfigEditor(configsData)

async function handleSave(key: string) {
  const config = getDraft(key)
  if (!config) return

  try {
    await updateConfig({ key: config.key, value: config.value, description: config.description })
    toastStore.addToast(t('admin.settings.messages.saved'), 'success')
  } catch {
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
  } catch {
    // Error handled globally
  }
}

async function handleDelete(key: string) {
  const isConfirmed = await confirm(t('common.confirmDelete'))
  if (!isConfirmed) return
  try {
    await deleteConfig(key)
    toastStore.addToast(t('common.deleted'), 'success')
  } catch {
    // Error handled globally
  }
}

const columns = [
  { key: 'key', label: t('common.key'), width: '20%' },
  { key: 'description', label: t('common.description'), width: '35%' },
  { key: 'value', label: t('common.value'), width: '35%' },
  { key: 'actions', label: '', align: 'right' as const, width: '10%' }
]
</script>

<template>
  <AdminDataPage :title="t('admin.settings.title')" :description="t('admin.settings.description')">
    <template #actions>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <BaseButton @click="isModalOpen = true">
          {{ t('common.add') }}
        </BaseButton>
      </div>
    </template>

    <div class="mt-8">
      <AdminPaginatedTable
        table-class=""
        :columns="columns"
        :items="configs"
        row-key="key"
        :loading="isLoading"
        :empty-text="t('common.noData')"
        :show-footer="false"
      >
        <template #cell-description="{ item }">
          <BaseInput :model-value="item.description"
            :label="`${item.key} ${t('common.description')}`" hideLabel
            @update:model-value="updateDraft(item.key, { description: String($event) })"
            inputClass="block w-full border-0 p-0 nv-text placeholder-[var(--nv-text-subtle)] focus:ring-0 sm:text-sm bg-transparent shadow-none" />
        </template>

        <template #cell-value="{ item }">
          <BaseInput :model-value="item.value"
            :label="`${item.key} ${t('common.value')}`" hideLabel
            @update:model-value="updateDraft(item.key, { value: String($event) })"
            inputClass="block w-full border-0 p-0 nv-text placeholder-[var(--nv-text-subtle)] focus:ring-0 sm:text-sm bg-transparent shadow-none" />
        </template>

        <template #cell-actions="{ item }">
          <div class="flex justify-end space-x-2">
            <AdminActionButton :label="t('common.save')" tone="accent" icon-only @click="handleSave(item.key)">
              <Save class="h-4 w-4" aria-hidden="true" />
            </AdminActionButton>
            <AdminActionButton :label="t('common.delete')" tone="danger" icon-only @click="handleDelete(item.key)">
              <Trash2 class="h-4 w-4" aria-hidden="true" />
            </AdminActionButton>
          </div>
        </template>
      </AdminPaginatedTable>
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
  </AdminDataPage>
</template>

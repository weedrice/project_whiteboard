<script setup lang="ts">
import { Save, Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminTableActions from '@/components/admin/AdminTableActions.vue'
import { useGlobalSettingsManager } from '@/features/admin/settings/useGlobalSettingsManager'


const { t } = useI18n()
const {
  closeCreateModal,
  configs,
  handleCreateConfig,
  handleDelete,
  handleSave,
  isLoading,
  isModalOpen,
  newConfig,
  openCreateModal,
  updateDraft,
} = useGlobalSettingsManager()

const columns = [
  { key: 'key', label: t('common.key'), width: '20%' },
  { key: 'description', label: t('common.description'), width: '35%', hideBelow: 'sm' as const },
  { key: 'value', label: t('common.value'), width: '35%' },
  { key: 'actions', label: '', align: 'right' as const, width: '10%' }
]
</script>

<template>
  <AdminDataPage :title="t('admin.settings.title')" :description="t('admin.settings.description')">
    <template #actions>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <BaseButton @click="openCreateModal">
          {{ t('common.add') }}
        </BaseButton>
      </div>
    </template>

    <div class="mt-8">
      <AdminPaginatedTable
        table-class=""
        :columns="columns"
        :caption="t('admin.settings.title')"
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
            inputClass="block w-full border-0 p-0 nv-text placeholder-[var(--nv-muted)] focus:ring-0 sm:text-sm bg-transparent shadow-none" />
        </template>

        <template #cell-value="{ item }">
          <BaseInput :model-value="item.value"
            :label="`${item.key} ${t('common.value')}`" hideLabel
            @update:model-value="updateDraft(item.key, { value: String($event) })"
            inputClass="block w-full border-0 p-0 nv-text placeholder-[var(--nv-muted)] focus:ring-0 sm:text-sm bg-transparent shadow-none" />
        </template>

        <template #cell-actions="{ item }">
          <AdminTableActions>
            <AdminActionButton :label="t('common.save')" tone="accent" icon-only @click="handleSave(item.key)">
              <Save class="h-4 w-4" aria-hidden="true" />
            </AdminActionButton>
            <AdminActionButton :label="t('common.delete')" tone="danger" icon-only @click="handleDelete(item.key)">
              <Trash2 class="h-4 w-4" aria-hidden="true" />
            </AdminActionButton>
          </AdminTableActions>
        </template>
      </AdminPaginatedTable>
    </div>

    <!-- Add Config Modal -->
    <BaseModal :isOpen="isModalOpen" :title="t('admin.settings.addConfig')" @close="closeCreateModal">
      <div class="space-y-4">
        <BaseInput v-model="newConfig.key" :label="t('common.key')" type="text" />
        <BaseInput v-model="newConfig.value" :label="t('common.value')" type="text" />
        <BaseInput v-model="newConfig.description" :label="t('common.description')" type="text" />
      </div>
      <template #footer>
        <AdminModalActions>
          <BaseButton @click="closeCreateModal" variant="secondary">
            {{ t('common.cancel') }}
          </BaseButton>
          <BaseButton @click="handleCreateConfig">
            {{ t('common.save') }}
          </BaseButton>
        </AdminModalActions>
      </template>
    </BaseModal>
  </AdminDataPage>
</template>

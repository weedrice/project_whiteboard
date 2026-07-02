<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import AdminFilterActions from '@/components/admin/AdminFilterActions.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import type { AdminUserFilterForm } from '@/composables/useAdminUserListState'

defineProps<{
  filterForm: AdminUserFilterForm
  getStatusLabel: (status: string) => string
  getRoleLabel: (role: string) => string
}>()

const emit = defineEmits<{
  search: []
  reset: []
}>()

const { t } = useI18n()
</script>

<template>
  <AdminFilterPanel>
    <div class="flex flex-col items-start gap-4">
      <div class="flex flex-wrap items-end gap-3">
        <AdminFilterField :label="t('admin.users.filters.status')">
          <select v-model="filterForm.status" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="ACTIVE">{{ getStatusLabel('ACTIVE') }}</option>
            <option value="SUSPENDED">{{ getStatusLabel('SUSPENDED') }}</option>
            <option value="DELETED">{{ getStatusLabel('DELETED') }}</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.role')">
          <select v-model="filterForm.role" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="USER">{{ getRoleLabel('USER') }}</option>
            <option value="SUPER_ADMIN">{{ getRoleLabel('SUPER_ADMIN') }}</option>
            <option value="BOARD_ADMIN">{{ getRoleLabel('BOARD_ADMIN') }}</option>
            <option value="MODERATOR">{{ getRoleLabel('MODERATOR') }}</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.emailVerified')">
          <select v-model="filterForm.emailVerified" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="true">{{ t('admin.users.filters.verified') }}</option>
            <option value="false">{{ t('admin.users.filters.unverified') }}</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.superAdmin')">
          <select v-model="filterForm.superAdmin" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="true">Y</option>
            <option value="false">N</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.withdrawn')">
          <select v-model="filterForm.withdrawn" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="true">{{ t('admin.users.filters.withdrawnOnly') }}</option>
            <option value="false">{{ t('admin.users.filters.activeOrSuspended') }}</option>
          </select>
        </AdminFilterField>
      </div>

      <div class="flex flex-wrap items-end gap-3">
        <AdminFilterField :label="t('admin.users.filters.createdFrom')" width-class="w-44">
          <input v-model="filterForm.createdFrom" type="date" class="input-base" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.createdTo')" width-class="w-44">
          <input v-model="filterForm.createdTo" type="date" class="input-base" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.lastLoginFrom')" width-class="w-44">
          <input v-model="filterForm.lastLoginFrom" type="date" class="input-base" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.lastLoginTo')" width-class="w-44">
          <input v-model="filterForm.lastLoginTo" type="date" class="input-base" />
        </AdminFilterField>
      </div>

      <div class="flex flex-wrap items-end gap-3">
        <div class="w-80 max-w-full">
          <label class="mb-1 block text-xs nv-text-subtle">{{ t('admin.users.filters.userSearch') }}</label>
          <BaseInput
            v-model="filterForm.q"
            :label="t('admin.users.searchPlaceholder')"
            :placeholder="t('admin.users.searchPlaceholder')"
            hide-label
            @keyup.enter="emit('search')"
          >
            <template #prefix>
              <Search class="h-5 w-5 nv-text-subtle" aria-hidden="true" />
            </template>
          </BaseInput>
        </div>
        <AdminFilterActions @search="emit('search')" @reset="emit('reset')" />
      </div>
    </div>
  </AdminFilterPanel>
</template>

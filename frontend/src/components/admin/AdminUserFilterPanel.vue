<script setup lang="ts">
import { computed } from 'vue'
import { Search } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import AdminFilterActions from '@/components/admin/AdminFilterActions.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import { isComposingKeyboardEvent } from '@/utils/keyboard'
import type { AdminUserFilterForm } from '@/features/admin/users/useAdminUserListState'

const props = defineProps<{
  filterForm: AdminUserFilterForm
  getStatusLabel: (status: string) => string
  getRoleLabel: (role: string) => string
}>()

const emit = defineEmits<{
  search: []
  reset: []
  updateFilter: [field: keyof AdminUserFilterForm, value: string]
}>()

const { t } = useI18n()

function createFilterModel(field: keyof AdminUserFilterForm) {
  return computed({
    get: () => props.filterForm[field],
    set: (value: string) => emit('updateFilter', field, value),
  })
}

const filterModels = {
  q: createFilterModel('q'),
  status: createFilterModel('status'),
  role: createFilterModel('role'),
  emailVerified: createFilterModel('emailVerified'),
  superAdmin: createFilterModel('superAdmin'),
  withdrawn: createFilterModel('withdrawn'),
  createdFrom: createFilterModel('createdFrom'),
  createdTo: createFilterModel('createdTo'),
  lastLoginFrom: createFilterModel('lastLoginFrom'),
  lastLoginTo: createFilterModel('lastLoginTo'),
}

const filterControlIds = {
  q: 'admin-user-filter-q',
  status: 'admin-user-filter-status',
  role: 'admin-user-filter-role',
  emailVerified: 'admin-user-filter-email-verified',
  superAdmin: 'admin-user-filter-super-admin',
  withdrawn: 'admin-user-filter-withdrawn',
  createdFrom: 'admin-user-filter-created-from',
  createdTo: 'admin-user-filter-created-to',
  lastLoginFrom: 'admin-user-filter-last-login-from',
  lastLoginTo: 'admin-user-filter-last-login-to',
} as const

function handleSearchKeyup(event: KeyboardEvent) {
  if (isComposingKeyboardEvent(event)) return
  emit('search')
}
</script>

<template>
  <AdminFilterPanel>
    <div class="flex flex-col items-start gap-4">
      <div class="flex flex-wrap items-end gap-3">
        <AdminFilterField :label="t('admin.users.filters.status')" :for-id="filterControlIds.status">
          <select :id="filterControlIds.status" v-model="filterModels.status.value" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="ACTIVE">{{ getStatusLabel('ACTIVE') }}</option>
            <option value="SUSPENDED">{{ getStatusLabel('SUSPENDED') }}</option>
            <option value="DELETED">{{ getStatusLabel('DELETED') }}</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.role')" :for-id="filterControlIds.role">
          <select :id="filterControlIds.role" v-model="filterModels.role.value" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="USER">{{ getRoleLabel('USER') }}</option>
            <option value="SUPER_ADMIN">{{ getRoleLabel('SUPER_ADMIN') }}</option>
            <option value="BOARD_ADMIN">{{ getRoleLabel('BOARD_ADMIN') }}</option>
            <option value="MODERATOR">{{ getRoleLabel('MODERATOR') }}</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.emailVerified')" :for-id="filterControlIds.emailVerified">
          <select :id="filterControlIds.emailVerified" v-model="filterModels.emailVerified.value" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="true">{{ t('admin.users.filters.verified') }}</option>
            <option value="false">{{ t('admin.users.filters.unverified') }}</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.superAdmin')" :for-id="filterControlIds.superAdmin">
          <select :id="filterControlIds.superAdmin" v-model="filterModels.superAdmin.value" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="true">Y</option>
            <option value="false">N</option>
          </select>
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.withdrawn')" :for-id="filterControlIds.withdrawn">
          <select :id="filterControlIds.withdrawn" v-model="filterModels.withdrawn.value" class="input-base">
            <option value="">{{ t('admin.common.all') }}</option>
            <option value="true">{{ t('admin.users.filters.withdrawnOnly') }}</option>
            <option value="false">{{ t('admin.users.filters.activeOrSuspended') }}</option>
          </select>
        </AdminFilterField>
      </div>

      <div class="flex flex-wrap items-end gap-3">
        <AdminFilterField :label="t('admin.users.filters.createdFrom')" :for-id="filterControlIds.createdFrom" width-class="w-44">
          <input :id="filterControlIds.createdFrom" v-model="filterModels.createdFrom.value" type="date" class="input-base" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.createdTo')" :for-id="filterControlIds.createdTo" width-class="w-44">
          <input :id="filterControlIds.createdTo" v-model="filterModels.createdTo.value" type="date" class="input-base" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.lastLoginFrom')" :for-id="filterControlIds.lastLoginFrom" width-class="w-44">
          <input :id="filterControlIds.lastLoginFrom" v-model="filterModels.lastLoginFrom.value" type="date" class="input-base" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.lastLoginTo')" :for-id="filterControlIds.lastLoginTo" width-class="w-44">
          <input :id="filterControlIds.lastLoginTo" v-model="filterModels.lastLoginTo.value" type="date" class="input-base" />
        </AdminFilterField>
      </div>

      <div class="flex flex-wrap items-end gap-3">
        <div class="w-80 max-w-full">
          <BaseInput
            :id="filterControlIds.q"
            v-model="filterModels.q.value"
            :label="t('admin.users.filters.userSearch')"
            :placeholder="t('admin.users.searchPlaceholder')"
            @keyup.enter="handleSearchKeyup"
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

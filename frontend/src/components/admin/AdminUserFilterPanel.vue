<script setup lang="ts">
import { computed } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
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

const activeFilters = computed(() => Object.entries(props.filterForm)
  .filter(([, value]) => Boolean(value))
  .map(([field, value]) => ({ field: field as keyof AdminUserFilterForm, value })))

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

const statusOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  { value: 'ACTIVE', label: props.getStatusLabel('ACTIVE') },
  { value: 'SUSPENDED', label: props.getStatusLabel('SUSPENDED') },
  { value: 'DELETED', label: props.getStatusLabel('DELETED') },
])
const roleOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  ...['USER', 'SUPER_ADMIN', 'BOARD_ADMIN', 'MODERATOR'].map((value) => ({
    value,
    label: props.getRoleLabel(value),
  })),
])
const booleanOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  { value: 'true', label: t('common.yes') },
  { value: 'false', label: t('common.noValue') },
])
const verifiedOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  { value: 'true', label: t('admin.users.filters.verified') },
  { value: 'false', label: t('admin.users.filters.unverified') },
])
const withdrawnOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  { value: 'true', label: t('admin.users.filters.withdrawnOnly') },
  { value: 'false', label: t('admin.users.filters.activeOrSuspended') },
])

function handleSearchKeyup(event: KeyboardEvent) {
  if (isComposingKeyboardEvent(event)) return
  emit('search')
}
</script>

<template>
  <AdminFilterPanel collapsible :title="t('admin.users.filters.userSearch')">
    <div class="flex flex-col items-start gap-4">
      <div class="flex flex-wrap items-end gap-3">
        <AdminFilterField :label="t('admin.users.filters.status')" :for-id="filterControlIds.status">
          <BaseSelect :id="filterControlIds.status" v-model="filterModels.status.value" :options="statusOptions" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.role')" :for-id="filterControlIds.role">
          <BaseSelect :id="filterControlIds.role" v-model="filterModels.role.value" :options="roleOptions" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.emailVerified')" :for-id="filterControlIds.emailVerified">
          <BaseSelect :id="filterControlIds.emailVerified" v-model="filterModels.emailVerified.value" :options="verifiedOptions" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.superAdmin')" :for-id="filterControlIds.superAdmin">
          <BaseSelect :id="filterControlIds.superAdmin" v-model="filterModels.superAdmin.value" :options="booleanOptions" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.withdrawn')" :for-id="filterControlIds.withdrawn">
          <BaseSelect :id="filterControlIds.withdrawn" v-model="filterModels.withdrawn.value" :options="withdrawnOptions" />
        </AdminFilterField>
      </div>

      <div v-if="activeFilters.length" class="flex flex-wrap gap-2" aria-live="polite">
        <button
          v-for="filter in activeFilters"
          :key="filter.field"
          type="button"
          class="nv-chip max-w-full"
          :aria-label="`${filter.field}: ${filter.value}`"
          @click="emit('updateFilter', filter.field, '')"
        >
          <span class="truncate">{{ filter.value }}</span>
          <X class="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>

      <div class="flex flex-wrap items-end gap-3">
        <AdminFilterField :label="t('admin.users.filters.createdFrom')" :for-id="filterControlIds.createdFrom" width="date">
          <BaseInput :id="filterControlIds.createdFrom" v-model="filterModels.createdFrom.value" type="date" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.createdTo')" :for-id="filterControlIds.createdTo" width="date">
          <BaseInput :id="filterControlIds.createdTo" v-model="filterModels.createdTo.value" type="date" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.lastLoginFrom')" :for-id="filterControlIds.lastLoginFrom" width="date">
          <BaseInput :id="filterControlIds.lastLoginFrom" v-model="filterModels.lastLoginFrom.value" type="date" />
        </AdminFilterField>
        <AdminFilterField :label="t('admin.users.filters.lastLoginTo')" :for-id="filterControlIds.lastLoginTo" width="date">
          <BaseInput :id="filterControlIds.lastLoginTo" v-model="filterModels.lastLoginTo.value" type="date" />
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

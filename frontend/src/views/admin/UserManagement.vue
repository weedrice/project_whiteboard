<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import AdminTableActions from '@/components/admin/AdminTableActions.vue'
import BooleanBadge from '@/components/admin/BooleanBadge.vue'
import UserDetailModal from '@/components/admin/UserDetailModal.vue'
import AdminUserFilterPanel from '@/components/admin/AdminUserFilterPanel.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { formatAdminPaginationSummary } from '@/utils/adminPaginationSummary'
import { formatDateOnly } from '@/utils/date'
import { useConfirm } from '@/composables/useConfirm'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import {
  useAdminUserListState,
  type AdminUserFilterForm,
} from '@/features/admin/users/useAdminUserListState'
import type { User } from '@/types'
import {
  canChangeAdminUserStatus,
  getAdminUserStatusActionLabel,
  getNextAdminUserStatus,
  getAdminUserRoleLabel,
  getAdminUserStatusLabel,
  getAdminUserStatusVariant,
  type AdminUserMutableStatus,
} from '@/utils/adminUserDisplay'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirmWithReason } = useConfirm()
const { useUsers, useUpdateUserStatus } = useAdmin()

const {
  applyFilters,
  filterForm,
  getSortLabel,
  handleSort,
  page,
  params,
  resetFilters,
  size,
} = useAdminUserListState()

const { data: usersData, isLoading } = useUsers(params)
const { mutateAsync: updateUserStatus } = useUpdateUserStatus()

const {
  items: users,
  totalElements: totalCount,
  totalPages,
  currentPage,
} = usePageResponseState(usersData, page)

const isDetailModalOpen = ref(false)
const selectedUserId = ref<number | null>(null)

function openDetailModal(user: User) {
  selectedUserId.value = user.userId
  isDetailModalOpen.value = true
}

function getStatusVariant(status: string) {
  return getAdminUserStatusVariant(status)
}

function getStatusLabel(status: string) {
  return getAdminUserStatusLabel(t, status)
}

function getRoleLabel(role: string) {
  return getAdminUserRoleLabel(t, role)
}

function updateFilterForm(field: keyof AdminUserFilterForm, value: string) {
  filterForm[field] = value
}

function getStatusActionLabel(status: AdminUserMutableStatus) {
  return getAdminUserStatusActionLabel(t, status)
}

async function handleStatusChange(user: User, status: AdminUserMutableStatus) {
  if (!canChangeAdminUserStatus(user.status)) return

  const reason = await confirmWithReason(t('admin.users.messages.confirmStatusChange', { action: getStatusActionLabel(user.status) }))
  if (!reason) return
  try {
    await updateUserStatus({ userId: user.userId, status, reason })
    toastStore.addToast(t('admin.users.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

const columns = computed(() => [
  { key: 'userId', label: getSortLabel(t('common.id'), 'userId'), width: '5%', sortable: true, hideBelow: 'lg' as const },
  { key: 'profile', label: t('admin.users.detail.profile'), width: '7%', align: 'center' as const, hideBelow: 'sm' as const },
  { key: 'loginId', label: getSortLabel(t('common.loginId'), 'loginId'), width: '10%', sortable: true },
  { key: 'displayName', label: getSortLabel(t('common.displayName'), 'displayName'), width: '11%', sortable: true },
  { key: 'email', label: t('common.email'), width: '14%', hideBelow: 'sm' as const },
  { key: 'isEmailVerified', label: getSortLabel(t('admin.users.detail.emailVerified'), 'isEmailVerified'), width: '8%', sortable: true, align: 'center' as const, hideBelow: 'lg' as const },
  { key: 'isSuperAdmin', label: getSortLabel(t('admin.users.detail.superAdmin'), 'isSuperAdmin'), width: '8%', sortable: true, align: 'center' as const, hideBelow: 'lg' as const },
  { key: 'status', label: getSortLabel(t('admin.users.table.status'), 'status'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'lastLoginAt', label: getSortLabel(t('admin.users.detail.lastLoginAt'), 'lastLoginAt'), width: '9%', sortable: true, hideBelow: 'lg' as const },
  { key: 'createdAt', label: getSortLabel(t('admin.users.table.joinedAt'), 'createdAt'), width: '9%', sortable: true, hideBelow: 'sm' as const },
  { key: 'actions', label: '', align: 'right' as const, width: '10%' }
])
const pageSizeOptions = [10, 20, 50]
</script>

<template>
  <AdminDataPage :title="t('admin.users.title')" :description="t('admin.users.description')">
    <template #filters>
      <AdminUserFilterPanel
        :filter-form="filterForm"
        :get-status-label="getStatusLabel"
        :get-role-label="getRoleLabel"
        @search="applyFilters"
        @reset="resetFilters"
        @update-filter="updateFilterForm"
      />
    </template>

    <AdminPaginatedTable
        :columns="columns"
        :items="users"
        row-key="userId"
        :loading="isLoading"
        :empty-text="t('common.noData')"
        interactive-rows
        row-activation-event="row-dblclick"
        :row-action-label="(item) => t('admin.common.rowDetailAria', { name: item.displayName || item.loginId })"
        :page="currentPage"
        :total-pages="totalPages"
        :summary="formatAdminPaginationSummary(totalCount, {
          unit: t('admin.users.filters.unit'),
          page: currentPage,
          totalPages,
          pageFormat: 'slash',
          includePage: totalPages > 0,
          t,
        })"
        @sort="handleSort"
        @row-dblclick="openDetailModal($event as User)"
        @page-change="page = $event"
      >
        <template #footer-description>
          <p class="mt-1 text-xs nv-text-subtle">{{ t('admin.common.footerDoubleClickHint') }}</p>
        </template>
        <template #footer-actions>
          <label for="admin-user-page-size" class="text-xs nv-text-subtle">{{ t('admin.common.pageSize') }}</label>
          <BaseSelect
            id="admin-user-page-size"
            v-model="size"
            :options="pageSizeOptions"
            input-class="px-2 py-1 text-xs"
          />
        </template>

        <template #cell-profile="{ item }">
          <div class="flex justify-center">
            <UserAvatar :image-url="item.profileImageUrl" :name="item.displayName || item.loginId" />
          </div>
        </template>

        <template #cell-isEmailVerified="{ item }">
          <BooleanBadge :value="item.isEmailVerified === true" />
        </template>

        <template #cell-isSuperAdmin="{ item }">
          <BooleanBadge :value="item.isSuperAdmin === true" true-variant="danger" />
        </template>

        <template #cell-status="{ item }">
          <AdminStatusBadge :label="getStatusLabel(item.status)" :variant="getStatusVariant(item.status)" />
        </template>

        <template #cell-lastLoginAt="{ item }">
          {{ item.lastLoginAt ? formatDateOnly(item.lastLoginAt) : '-' }}
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDateOnly(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <AdminTableActions gap-class="gap-1">
            <AdminActionButton
              :label="t('admin.common.rowDetailAria', { name: item.displayName || item.loginId })"
              @click.stop="openDetailModal(item)"
            >
              {{ t('admin.common.detail') }}
            </AdminActionButton>
            <AdminActionButton
              v-if="canChangeAdminUserStatus(item.status)"
              @click.stop="handleStatusChange(item, getNextAdminUserStatus(item.status))"
              :tone="item.status === 'ACTIVE' ? 'danger' : 'neutral'"
              :label="getStatusActionLabel(item.status)"
            >
              {{ getStatusActionLabel(item.status) }}
            </AdminActionButton>
          </AdminTableActions>
        </template>
      </AdminPaginatedTable>

    <UserDetailModal
      :isOpen="isDetailModalOpen"
      :userId="selectedUserId"
      @close="isDetailModalOpen = false"
    />
  </AdminDataPage>
</template>

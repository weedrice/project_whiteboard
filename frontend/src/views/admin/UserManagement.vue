<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Search } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFilterActions from '@/components/admin/AdminFilterActions.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import AdminTableActions from '@/components/admin/AdminTableActions.vue'
import BooleanBadge from '@/components/admin/BooleanBadge.vue'
import UserDetailModal from '@/components/admin/UserDetailModal.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import { formatAdminPaginationSummary } from '@/utils/adminPaginationSummary'
import { formatDateOnly } from '@/utils/date'
import { useConfirm } from '@/composables/useConfirm'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import { useAdminUserListState } from '@/composables/useAdminUserListState'
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
const { confirm } = useConfirm()
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

function getStatusActionLabel(status: AdminUserMutableStatus) {
  return getAdminUserStatusActionLabel(t, status)
}

async function handleStatusChange(user: User, status: AdminUserMutableStatus) {
  if (!canChangeAdminUserStatus(user.status)) return

  const isConfirmed = await confirm(t('admin.users.messages.confirmStatusChange', { action: getStatusActionLabel(user.status) }))
  if (!isConfirmed) return
  try {
    await updateUserStatus({ userId: user.userId, status })
    toastStore.addToast(t('admin.users.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

const columns = computed(() => [
  { key: 'userId', label: getSortLabel(t('common.id'), 'userId'), width: '5%', sortable: true },
  { key: 'profile', label: t('admin.users.detail.profile'), width: '7%', align: 'center' as const },
  { key: 'loginId', label: getSortLabel(t('common.loginId'), 'loginId'), width: '10%', sortable: true },
  { key: 'displayName', label: getSortLabel(t('common.displayName'), 'displayName'), width: '11%', sortable: true },
  { key: 'email', label: t('common.email'), width: '14%' },
  { key: 'isEmailVerified', label: getSortLabel(t('admin.users.detail.emailVerified'), 'isEmailVerified'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'isSuperAdmin', label: getSortLabel(t('admin.users.detail.superAdmin'), 'isSuperAdmin'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'status', label: getSortLabel(t('admin.users.table.status'), 'status'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'lastLoginAt', label: getSortLabel(t('admin.users.detail.lastLoginAt'), 'lastLoginAt'), width: '9%', sortable: true },
  { key: 'createdAt', label: getSortLabel(t('admin.users.table.joinedAt'), 'createdAt'), width: '9%', sortable: true },
  { key: 'actions', label: '', align: 'right' as const, width: '10%' }
])
</script>

<template>
  <AdminDataPage :title="t('admin.users.title')" :description="t('admin.users.description')">
    <template #filters>
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
              hideLabel
              @keyup.enter="applyFilters"
            >
              <template #prefix>
                <Search class="h-5 w-5 nv-text-subtle" aria-hidden="true" />
              </template>
            </BaseInput>
          </div>
          <AdminFilterActions @search="applyFilters" @reset="resetFilters" />
        </div>
      </div>
    </AdminFilterPanel>
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
          <label class="text-xs nv-text-subtle">{{ t('admin.common.pageSize') }}</label>
          <select v-model.number="size" class="input-base px-2 py-1 text-xs">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
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

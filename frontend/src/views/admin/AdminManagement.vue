<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { UserPlus, UserMinus, Shield } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'

const { t } = useI18n()
const toastStore = useToastStore()
const {
  useSuperAdmins,
  useAdmins,
  useCreateAdmin,
  useUpdateAdminStatus,
  useUpdateSuperAdminStatus
} = useAdmin()

const newSuperAdminLoginId = ref('')
const newBoardAdminLoginId = ref('')
const newBoardId = ref('')

// Queries
const { data: superAdminsData, isLoading: isSuperAdminsLoading } = useSuperAdmins()
const { data: boardAdminsData, isLoading: isBoardAdminsLoading } = useAdmins()

// Mutations
const { mutateAsync: createAdmin } = useCreateAdmin()
const { mutateAsync: updateAdminStatus } = useUpdateAdminStatus()
const { mutateAsync: updateSuperAdminStatus } = useUpdateSuperAdminStatus()

// Computed
const superAdmins = computed(() => {
  return (superAdminsData.value || []).map(admin => ({
    ...admin,
    type: 'SUPER',
    isActive: admin.active === true
  }))
})

const boardAdmins = computed(() => {
  return (boardAdminsData.value || []).map(admin => ({
    ...admin,
    type: 'BOARD',
    isActive: admin.active === true
  }))
})

const isLoading = computed(() => isSuperAdminsLoading.value || isBoardAdminsLoading.value)

import { formatDate } from '@/utils/date'

async function handleCreateSuperAdmin() {
  if (!newSuperAdminLoginId.value) {
    toastStore.addToast(t('admin.admins.messages.inputLoginId'), 'warning')
    return
  }
  try {
    await updateSuperAdminStatus({ loginId: newSuperAdminLoginId.value, action: 'activate' })
    toastStore.addToast(t('admin.admins.messages.added'), 'success')
    newSuperAdminLoginId.value = ''
  } catch (err) {
    // Error handled globally
  }
}

async function handleCreateBoardAdmin() {
  if (!newBoardAdminLoginId.value || !newBoardId.value) {
    toastStore.addToast(t('admin.admins.messages.inputLoginId'), 'warning')
    return
  }
  try {
    await createAdmin({ loginId: newBoardAdminLoginId.value, role: 'BOARD_ADMIN', boardId: newBoardId.value })
    toastStore.addToast(t('admin.admins.messages.added'), 'success')
    newBoardAdminLoginId.value = ''
    newBoardId.value = ''
  } catch (err) {
    // Error handled globally
  }
}

async function toggleAdminStatus(admin) {
  try {
    if (admin.type === 'SUPER') {
      const action = admin.superAdmin ? 'deactivate' : 'activate'
      await updateSuperAdminStatus({ loginId: admin.loginId, action })
    } else {
      // Board Admin
      const action = admin.isActive ? 'deactivate' : 'activate'
      await updateAdminStatus({ adminId: admin.adminId, action })
    }
    toastStore.addToast(t('admin.admins.messages.statusChanged'), 'success')
  } catch (err) {
    // Error handled globally
  }
}

const superAdminColumns = [
  { key: 'loginId', label: t('common.loginId'), width: '20%' },
  { key: 'displayName', label: t('common.name'), width: '20%' },
  { key: 'status', label: t('common.status'), width: '15%' },
  { key: 'createdAt', label: t('common.createdAt'), width: '25%' },
  { key: 'actions', label: '', align: 'right', width: '20%' }
]

const boardAdminColumns = [
  { key: 'loginId', label: t('common.loginId'), width: '15%' },
  { key: 'displayName', label: t('common.name'), width: '15%' },
  { key: 'boardName', label: t('common.board'), width: '25%' },
  { key: 'status', label: t('common.status'), width: '15%' },
  { key: 'createdAt', label: t('common.createdAt'), width: '20%' },
  { key: 'actions', label: '', align: 'right', width: '10%' }
]
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.admins.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.admins.description') }}</p>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2 mt-6">
      <!-- Add Super Admin Form -->
      <div
        class="bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 flex flex-col h-full border border-gray-200 dark:border-gray-700">
        <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.admins.addSuperAdmin') }}
        </h3>
        <div class="mt-2 max-w-xl text-sm text-gray-500 dark:text-gray-400">
          <p>{{ t('admin.admins.addSuperAdminDesc') }}</p>
        </div>
        <form @submit.prevent="handleCreateSuperAdmin" class="mt-5 flex-1 flex flex-col">
          <div class="w-full">
            <label for="superAdminLoginId" class="sr-only">{{ t('admin.admins.table.loginId') }}</label>
            <BaseInput id="superAdminLoginId" v-model="newSuperAdminLoginId"
              :placeholder="t('admin.admins.loginIdPlaceholder')" hideLabel />
          </div>
          <BaseButton type="submit" class="mt-auto w-full">
            <UserPlus class="h-4 w-4 mr-2" />
            {{ t('common.add') }}
          </BaseButton>
        </form>
      </div>

      <!-- Add Board Admin Form -->
      <div
        class="bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 flex flex-col h-full border border-gray-200 dark:border-gray-700">
        <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.admins.addBoardAdmin') }}
        </h3>
        <div class="mt-2 max-w-xl text-sm text-gray-500 dark:text-gray-400">
          <p>{{ t('admin.admins.addBoardAdminDesc') }}</p>
        </div>
        <form @submit.prevent="handleCreateBoardAdmin" class="mt-5 space-y-3 flex-1 flex flex-col">
          <div class="w-full">
            <label for="boardAdminLoginId" class="sr-only">{{ t('admin.admins.table.loginId') }}</label>
            <BaseInput id="boardAdminLoginId" v-model="newBoardAdminLoginId"
              :placeholder="t('admin.admins.loginIdPlaceholder')" hideLabel />
          </div>
          <div class="w-full">
            <label for="boardId" class="sr-only">{{ t('admin.admins.boardId') }}</label>
            <BaseInput type="number" id="boardId" v-model="newBoardId" :placeholder="t('admin.admins.boardId')"
              hideLabel />
          </div>
          <BaseButton type="submit" class="mt-auto w-full">
            <UserPlus class="h-4 w-4 mr-2" />
            {{ t('common.add') }}
          </BaseButton>
        </form>
      </div>
    </div>

    <!-- Super Admins List -->
    <div class="mt-8">
      <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white mb-4">{{ t('admin.admins.superAdmins') }}
      </h3>
      <BaseTable :columns="superAdminColumns" :items="superAdmins" :loading="isSuperAdminsLoading"
        :emptyText="t('common.noData')">
        <template #cell-loginId="{ item }">
          {{ item.loginId || '-' }}
        </template>

        <template #cell-displayName="{ item }">
          {{ item.displayName || '-' }}
        </template>

        <template #cell-status="{ item }">
          <BaseBadge :variant="item.superAdmin ? 'success' : 'danger'" size="sm">
            {{ item.superAdmin ? t('common.active') : t('common.inactive') }}
          </BaseBadge>
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <BaseButton @click="toggleAdminStatus(item)" variant="ghost" size="sm"
            class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300">
            {{ item.superAdmin ? t('common.deactivate') : t('common.activate') }}
          </BaseButton>
        </template>
      </BaseTable>
    </div>

    <!-- Board Admins List -->
    <div class="mt-8">
      <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white mb-4">{{ t('admin.admins.boardAdmins') }}
      </h3>
      <BaseTable :columns="boardAdminColumns" :items="boardAdmins" :loading="isBoardAdminsLoading"
        :emptyText="t('common.noData')">
        <template #cell-loginId="{ item }">
          {{ item.user?.loginId || '-' }}
        </template>

        <template #cell-displayName="{ item }">
          {{ item.user?.displayName || '-' }}
        </template>

        <template #cell-boardName="{ item }">
          {{ item.board?.boardName || '-' }} (ID: {{ item.board?.boardId || 'N/A' }})
        </template>

        <template #cell-status="{ item }">
          <BaseBadge :variant="item.isActive ? 'success' : 'danger'" size="sm">
            {{ item.isActive ? t('common.active') : t('common.inactive') }}
          </BaseBadge>
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <BaseButton @click="toggleAdminStatus(item)" variant="ghost" size="sm"
            class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300">
            {{ item.isActive ? t('common.deactivate') : t('common.activate') }}
          </BaseButton>
        </template>
      </BaseTable>
    </div>
  </div>
</template>


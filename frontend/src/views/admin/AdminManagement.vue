<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { UserPlus } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { formatDate } from '@/utils/date'

const { t } = useI18n()
const toastStore = useToastStore()
const {
  useSuperAdmins,
  useUpdateSuperAdminStatus
} = useAdmin()

const newSuperAdminLoginId = ref('')

const { data: superAdminsData, isLoading: isSuperAdminsLoading } = useSuperAdmins()
const { mutateAsync: updateSuperAdminStatus } = useUpdateSuperAdminStatus()

interface SuperAdminRow {
  superAdmin: boolean
  isActive: boolean
  loginId?: string
  displayName?: string
  createdAt?: string
  [key: string]: unknown
}

const superAdmins = computed<SuperAdminRow[]>(() => {
  const list = (superAdminsData.value || []) as unknown[]
  return list.map((admin) => {
    const a = admin as Record<string, unknown>
    const superAdmin = !!(a.superAdmin ?? a.isSuperAdmin)
    return { ...a, superAdmin, isActive: superAdmin }
  }) as SuperAdminRow[]
})

async function handleCreateSuperAdmin() {
  if (!newSuperAdminLoginId.value) {
    toastStore.addToast(t('admin.admins.messages.inputLoginId'), 'warning')
    return
  }

  try {
    await updateSuperAdminStatus({ loginId: newSuperAdminLoginId.value, action: 'activate' })
    toastStore.addToast(t('admin.admins.messages.added'), 'success')
    newSuperAdminLoginId.value = ''
  } catch {
    // Error handled globally
  }
}

async function toggleSuperAdminStatus(admin: SuperAdminRow) {
  try {
    const action = admin.superAdmin ? 'deactivate' : 'activate'
    await updateSuperAdminStatus({ loginId: String(admin.loginId ?? ''), action })
    toastStore.addToast(t('admin.admins.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

const superAdminColumns: { key: string; label: string; width: string; align?: 'left' | 'center' | 'right' }[] = [
  { key: 'loginId', label: t('common.loginId'), width: '20%' },
  { key: 'displayName', label: t('common.name'), width: '20%' },
  { key: 'status', label: t('common.status'), width: '20%' },
  { key: 'createdAt', label: t('common.createdAt'), width: '25%' },
  { key: 'actions', label: '', align: 'right', width: '15%' }
]
</script>

<template>
  <div>
    <AdminPageHeader :title="t('admin.admins.title')" :description="t('admin.admins.description')" />

    <div class="mt-6">
      <div
        class="bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 border border-gray-200 dark:border-gray-700 max-w-xl">
        <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.admins.addSuperAdmin') }}</h3>
        <div class="mt-2 text-sm text-gray-500 dark:text-gray-400">
          <p>{{ t('admin.admins.addSuperAdminDesc') }}</p>
        </div>
        <form @submit.prevent="handleCreateSuperAdmin" class="mt-5 flex gap-3">
          <div class="flex-1">
            <label for="superAdminLoginId" class="sr-only">{{ t('admin.admins.table.loginId') }}</label>
            <BaseInput
              id="superAdminLoginId"
              v-model="newSuperAdminLoginId"
              :placeholder="t('admin.admins.loginIdPlaceholder')"
              hideLabel
            />
          </div>
          <BaseButton type="submit">
            <UserPlus class="h-4 w-4 mr-2" />
            {{ t('common.add') }}
          </BaseButton>
        </form>
      </div>
    </div>

    <div class="mt-8">
      <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white mb-4">{{ t('admin.admins.superAdmins') }}</h3>
      <BaseTable :columns="superAdminColumns" :items="superAdmins" row-key="userId" :loading="isSuperAdminsLoading" :emptyText="t('common.noData')">
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
          {{ formatDate((item as SuperAdminRow).createdAt ?? '') }}
        </template>

        <template #cell-actions="{ item }">
          <BaseButton
            @click="toggleSuperAdminStatus(item as SuperAdminRow)"
            variant="ghost"
            size="sm"
            class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300"
          >
            {{ (item as SuperAdminRow).superAdmin ? t('common.deactivate') : t('common.activate') }}
          </BaseButton>
        </template>
      </BaseTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { UserPlus } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFormPanel from '@/components/admin/AdminFormPanel.vue'
import AdminInlineForm from '@/components/admin/AdminInlineForm.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import { formatDate } from '@/utils/date'
import type { SuperAdminInfo } from '@/types'
import { useConfirm } from '@/composables/useConfirm'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirmWithReason } = useConfirm()
const {
  useSuperAdmins,
  useUpdateSuperAdminStatus
} = useAdmin()

const newSuperAdminLoginId = ref('')

const { data: superAdminsData, isLoading: isSuperAdminsLoading } = useSuperAdmins()
const { mutateAsync: updateSuperAdminStatus } = useUpdateSuperAdminStatus()

interface SuperAdminRow {
  userId: number
  superAdmin: boolean
  isActive: boolean
  loginId: string
  displayName: string
  createdAt: string
}

type SuperAdminInfoWithLegacyFlag = SuperAdminInfo & {
  superAdmin?: boolean
}

const toSuperAdminRow = (admin: SuperAdminInfoWithLegacyFlag): SuperAdminRow => {
  const superAdmin = admin.superAdmin ?? admin.isSuperAdmin
  return {
    userId: admin.userId,
    loginId: admin.loginId,
    displayName: admin.displayName,
    createdAt: admin.createdAt,
    superAdmin,
    isActive: superAdmin,
  }
}

const superAdmins = computed<SuperAdminRow[]>(() => {
  return (superAdminsData.value ?? []).map(toSuperAdminRow)
})

async function handleCreateSuperAdmin() {
  const loginId = newSuperAdminLoginId.value.trim()
  if (!loginId) {
    toastStore.addToast(t('admin.admins.messages.inputLoginId'), 'warning')
    return
  }

  const reason = await confirmWithReason(t('admin.admins.addSuperAdminDesc'))
  if (!reason) return

  try {
    await updateSuperAdminStatus({ loginId, action: 'activate', reason })
    toastStore.addToast(t('admin.admins.messages.added'), 'success')
    newSuperAdminLoginId.value = ''
  } catch {
    // Error handled globally
  }
}

async function toggleSuperAdminStatus(admin: SuperAdminRow) {
  const reason = await confirmWithReason(
    admin.superAdmin ? t('common.confirmDelete') : t('admin.admins.addSuperAdminDesc')
  )
  if (!reason) return

  try {
    const action = admin.superAdmin ? 'deactivate' : 'activate'
    await updateSuperAdminStatus({ loginId: String(admin.loginId ?? ''), action, reason })
    toastStore.addToast(t('admin.admins.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

const superAdminColumns: { key: string; label: string; width: string; align?: 'left' | 'center' | 'right'; hideBelow?: 'sm' | 'lg' }[] = [
  { key: 'loginId', label: t('common.loginId'), width: '20%' },
  { key: 'displayName', label: t('common.name'), width: '20%', hideBelow: 'sm' },
  { key: 'status', label: t('common.status'), width: '20%' },
  { key: 'createdAt', label: t('common.createdAt'), width: '25%', hideBelow: 'lg' },
  { key: 'actions', label: '', align: 'right', width: '15%' }
]
</script>

<template>
  <AdminDataPage :title="t('admin.admins.title')" :description="t('admin.admins.description')">
    <div class="mt-6">
      <AdminFormPanel
        :title="t('admin.admins.addSuperAdmin')"
        :description="t('admin.admins.addSuperAdminDesc')"
        class-name=""
        max-width-class="max-w-xl"
      >
        <AdminInlineForm @submit.prevent="handleCreateSuperAdmin">
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
        </AdminInlineForm>
      </AdminFormPanel>
    </div>

    <div class="mt-8">
      <p class="nv-kicker mb-2">{{ t('common.status') }}</p>
      <h2 class="text-lg font-medium leading-6 nv-title mb-4">{{ t('admin.admins.superAdmins') }}</h2>
      <AdminPaginatedTable
        table-class=""
        :columns="superAdminColumns"
        :caption="t('admin.admins.superAdmins')"
        :items="superAdmins"
        row-key="userId"
        :loading="isSuperAdminsLoading"
        :empty-text="t('common.noData')"
        :show-footer="false"
      >
        <template #cell-loginId="{ item }">
          {{ item.loginId || '-' }}
        </template>

        <template #cell-displayName="{ item }">
          {{ item.displayName || '-' }}
        </template>

        <template #cell-status="{ item }">
          <AdminStatusBadge
            :label="item.superAdmin ? t('common.active') : t('common.inactive')"
            :variant="item.superAdmin ? 'success' : 'danger'"
          />
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <AdminActionButton
            :label="item.superAdmin ? t('common.deactivate') : t('common.activate')"
            tone="accent"
            @click="toggleSuperAdminStatus(item)"
          >
            {{ item.superAdmin ? t('common.deactivate') : t('common.activate') }}
          </AdminActionButton>
        </template>
      </AdminPaginatedTable>
    </div>
  </AdminDataPage>
</template>

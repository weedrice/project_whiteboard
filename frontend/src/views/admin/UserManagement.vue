<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Search, MoreVertical, Shield, Ban, VolumeX, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import UserDetailModal from '@/components/admin/UserDetailModal.vue'
import { formatDate } from '@/utils/date'
import { useConfirm } from '@/composables/useConfirm'
import { usePrompt } from '@/composables/usePrompt'
import type { User } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { prompt } = usePrompt()
const { useUsers, useUpdateUserStatus, useSanctionUser } = useAdmin()

const page = ref(0)
const size = ref(20)
const searchQuery = ref('')

const params = computed(() => ({
  page: page.value,
  size: size.value,
  q: searchQuery.value
}))

const { data: usersData, isLoading } = useUsers(params)
const { mutateAsync: updateUserStatus } = useUpdateUserStatus()
const { mutateAsync: sanctionUser } = useSanctionUser()

const users = computed(() => usersData.value?.content || [])
const totalCount = computed(() => usersData.value?.totalElements || 0)

const isDetailModalOpen = ref(false)
const selectedUser = ref<User | null>(null)

function openDetailModal(user: User) {
  selectedUser.value = user
  isDetailModalOpen.value = true
}

async function handleStatusChange(user: User, status: User['status']) {
  const isConfirmed = await confirm(t('admin.users.messages.confirmStatusChange', { status }))
  if (!isConfirmed) return
  try {
    await updateUserStatus({ userId: user.userId, status })
    toastStore.addToast(t('admin.users.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

async function handleSanction(user: User, type: 'BAN' | 'MUTE') {
  const typeLabel = type === 'BAN' ? t('admin.users.actions.ban') : t('admin.users.actions.mute')
  const reason = await prompt(t('admin.users.messages.enterReason', { type: typeLabel }), t('admin.users.messages.sanctionTitle', { type: typeLabel }))
  if (!reason) return

  try {
    await sanctionUser({ userId: user.userId, type, reason })
    toastStore.addToast(t('admin.users.messages.sanctionComplete', { type: typeLabel }), 'success')
  } catch {
    // Error handled globally
  }
}

watch(searchQuery, () => {
  page.value = 0
})

const columns = computed(() => [
  { key: 'userId', label: t('common.id'), width: '10%' },
  { key: 'loginId', label: t('common.loginId'), width: '15%' },
  { key: 'displayName', label: t('common.displayName'), width: '15%' },
  { key: 'email', label: t('common.email'), width: '20%' },
  { key: 'status', label: t('admin.users.table.status'), width: '15%' },
  { key: 'createdAt', label: t('admin.users.table.joinedAt'), width: '15%' },
  { key: 'actions', label: '', align: 'right' as const, width: '10%' }
])
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.users.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.users.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <div class="w-64">
          <BaseInput v-model="searchQuery" :placeholder="t('admin.users.searchPlaceholder')" hideLabel>
            <template #prefix>
              <Search class="h-5 w-5 text-gray-400 dark:text-gray-500" aria-hidden="true" />
            </template>
          </BaseInput>
        </div>
      </div>
    </div>

    <div class="mt-8">
      <BaseTable :columns="columns" :items="users" :loading="isLoading" :emptyText="t('common.noData')">
        <template #cell-status="{ item }">
          <BaseBadge :variant="item.status === 'ACTIVE' ? 'success' : item.status === 'SANCTIONED' ? 'danger' : 'gray'"
            size="sm">
            {{ t(`admin.users.status.${item.status}`) }}
          </BaseBadge>
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <div class="flex justify-end space-x-2">
            <BaseButton @click="openDetailModal(item)" variant="ghost" size="sm"
              class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300"
              :title="t('common.viewDetail')">
              <Eye class="h-4 w-4" />
            </BaseButton>
            <BaseButton @click="handleSanction(item, 'BAN')" variant="ghost" size="sm"
              class="p-1 text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300"
              :title="t('admin.users.actions.ban')">
              <Ban class="h-4 w-4" />
            </BaseButton>
            <BaseButton @click="handleSanction(item, 'MUTE')" variant="ghost" size="sm"
              class="p-1 text-orange-600 hover:text-orange-900 dark:text-orange-400 dark:hover:text-orange-300"
              :title="t('admin.users.actions.mute')">
              <VolumeX class="h-4 w-4" />
            </BaseButton>
          </div>
        </template>
      </BaseTable>
    </div>

    <UserDetailModal :isOpen="isDetailModalOpen" :user="selectedUser" @close="isDetailModalOpen = false" />
  </div>
</template>

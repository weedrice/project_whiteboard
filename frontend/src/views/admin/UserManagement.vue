<script setup>
import { ref, computed, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Search, MoreVertical, Shield, Ban, VolumeX } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseBadge from '@/components/common/BaseBadge.vue'
import BaseTable from '@/components/common/BaseTable.vue'

const { t } = useI18n()
const toastStore = useToastStore()
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

async function handleStatusChange(user, status) {
  if (!confirm(t('admin.users.messages.confirmStatusChange', { status }))) return
  try {
    await updateUserStatus({ userId: user.userId, status })
    toastStore.addToast(t('admin.users.messages.statusChanged'), 'success')
  } catch (err) {
    // Error handled globally
  }
}

async function handleSanction(user, type) {
  const reason = prompt(t('admin.users.messages.enterReason', { type }))
  if (!reason) return

  try {
    await sanctionUser({ userId: user.userId, type, reason })
    toastStore.addToast(t('admin.users.messages.sanctionComplete', { type }), 'success')
  } catch (err) {
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
  { key: 'actions', label: '', align: 'right', width: '10%' }
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
          <BaseBadge :variant="item.status === 'ACTIVE' ? 'success' : item.status === 'SUSPENDED' ? 'danger' : 'gray'"
            size="sm">
            {{ t(`admin.users.status.${item.status}`) }}
          </BaseBadge>
        </template>

        <template #cell-actions="{ item }">
          <div class="flex justify-end space-x-2">
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
  </div>
</template>

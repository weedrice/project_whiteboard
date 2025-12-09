<script setup>
import { ref, computed, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Search, MoreVertical, Shield, Ban, VolumeX } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

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
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.users.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.users.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <div class="relative rounded-md shadow-sm">
          <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <Search class="h-5 w-5 text-gray-400 dark:text-gray-500" aria-hidden="true" />
          </div>
          <input
            type="text"
            v-model="searchQuery"
            class="focus:ring-indigo-500 focus:border-indigo-500 block w-full pl-10 sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
            :placeholder="t('admin.users.searchPlaceholder')"
          />
        </div>
      </div>
    </div>
    
    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg border border-gray-200 dark:border-gray-700">
            <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
              <thead class="bg-gray-50 dark:bg-gray-700">
                <tr>
                  <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 dark:text-white sm:pl-6">{{ t('common.id') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.loginId') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.displayName') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.email') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('admin.users.table.status') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('admin.users.table.joinedAt') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                <tr v-for="user in users" :key="user.userId">
                  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 dark:text-white sm:pl-6">{{ user.userId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ user.loginId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ user.displayName }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ user.email }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                    <span class="inline-flex rounded-full px-2 text-xs font-semibold leading-5" 
                        :class="{
                            'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200': user.status === 'ACTIVE',
                            'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200': user.status === 'SUSPENDED',
                            'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300': user.status === 'DELETED'
                        }">
                      {{ t(`admin.users.status.${user.status}`) }}
                    </span>
                  </td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ user.createdAt }}</td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <div class="flex justify-end space-x-2">
                        <button @click="handleSanction(user, 'BAN')" class="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300" :title="t('admin.users.actions.ban')">
                            <Ban class="h-4 w-4" />
                        </button>
                        <button @click="handleSanction(user, 'MUTE')" class="text-orange-600 hover:text-orange-900 dark:text-orange-400 dark:hover:text-orange-300" :title="t('admin.users.actions.mute')">
                            <VolumeX class="h-4 w-4" />
                        </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

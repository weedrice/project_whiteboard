<script setup>
import { ref, onMounted, watch } from 'vue'
import { adminApi } from '@/api/admin'
import { Search, MoreVertical, Shield, Ban, VolumeX } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const users = ref([])
const totalCount = ref(0)
const page = ref(0)
const size = ref(20)
const searchQuery = ref('')
const isLoading = ref(false)

async function fetchUsers() {
  isLoading.value = true
  try {
    const res = await adminApi.getUsers({ page: page.value, size: size.value, q: searchQuery.value })
    if (res.data.success) {
      users.value = res.data.data.content
      totalCount.value = res.data.data.totalElements
    }
  } catch (err) {
    logger.error('Failed to fetch users:', err)
  } finally {
    isLoading.value = false
  }
}

async function handleStatusChange(user, status) {
  if (!confirm(t('admin.users.messages.confirmStatusChange', { status }))) return
  try {
    await adminApi.updateUserStatus(user.userId, status)
    user.status = status
    alert(t('admin.users.messages.statusChanged'))
  } catch (err) {
    logger.error(err)
    alert(t('admin.users.messages.statusChangeFailed'))
  }
}

async function handleSanction(user, type) {
    const reason = prompt(t('admin.users.messages.enterReason', { type }))
    if (!reason) return
    
    try {
        await adminApi.sanctionUser({ userId: user.userId, type, reason })
        alert(t('admin.users.messages.sanctionComplete', { type }))
    } catch (err) {
        logger.error(err)
        alert(t('admin.users.messages.sanctionFailed'))
    }
}

onMounted(() => {
  fetchUsers()
})

watch(searchQuery, () => {
    page.value = 0
    fetchUsers()
})
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900">{{ t('admin.users.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700">{{ t('admin.users.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <div class="relative rounded-md shadow-sm">
          <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <Search class="h-5 w-5 text-gray-400" aria-hidden="true" />
          </div>
          <input
            type="text"
            v-model="searchQuery"
            class="focus:ring-indigo-500 focus:border-indigo-500 block w-full pl-10 sm:text-sm border-gray-300 rounded-md"
            :placeholder="t('admin.users.searchPlaceholder')"
          />
        </div>
      </div>
    </div>
    
    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
            <table class="min-w-full divide-y divide-gray-300">
              <thead class="bg-gray-50">
                <tr>
                  <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">{{ t('common.id') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.loginId') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.displayName') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('common.email') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.users.table.status') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.users.table.joinedAt') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 bg-white">
                <tr v-for="user in users" :key="user.userId">
                  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">{{ user.userId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ user.loginId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ user.displayName }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ user.email }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                    <span class="inline-flex rounded-full px-2 text-xs font-semibold leading-5" 
                        :class="{
                            'bg-green-100 text-green-800': user.status === 'ACTIVE',
                            'bg-red-100 text-red-800': user.status === 'SUSPENDED',
                            'bg-gray-100 text-gray-800': user.status === 'DELETED'
                        }">
                      {{ t(`admin.users.status.${user.status}`) }}
                    </span>
                  </td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ user.createdAt }}</td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <div class="flex justify-end space-x-2">
                        <button @click="handleSanction(user, 'BAN')" class="text-red-600 hover:text-red-900" :title="t('admin.users.actions.ban')">
                            <Ban class="h-4 w-4" />
                        </button>
                        <button @click="handleSanction(user, 'MUTE')" class="text-orange-600 hover:text-orange-900" :title="t('admin.users.actions.mute')">
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

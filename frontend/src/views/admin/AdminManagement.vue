<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { UserPlus, UserMinus, Shield } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

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

function formatDate(dateString) {
  if (!dateString) return '-'
  const date = Array.isArray(dateString) 
    ? new Date(dateString[0], dateString[1] - 1, dateString[2], dateString[3], dateString[4], dateString[5] || 0)
    : new Date(dateString)
    
  const pad = (n) => n.toString().padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

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
      <div class="bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 flex flex-col h-full border border-gray-200 dark:border-gray-700">
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.admins.addSuperAdmin') }}</h3>
          <div class="mt-2 max-w-xl text-sm text-gray-500 dark:text-gray-400">
              <p>{{ t('admin.admins.addSuperAdminDesc') }}</p>
          </div>
          <form @submit.prevent="handleCreateSuperAdmin" class="mt-5 flex-1 flex flex-col">
              <div class="w-full">
                  <label for="superAdminLoginId" class="sr-only">{{ t('admin.admins.table.loginId') }}</label>
                  <input
                      type="text"
                      id="superAdminLoginId"
                      v-model="newSuperAdminLoginId"
                      class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
                      :placeholder="t('admin.admins.loginIdPlaceholder')"
                  />
              </div>
              <button
                  type="submit"
                  class="mt-auto w-full inline-flex items-center justify-center px-4 py-2 border border-transparent shadow-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:text-sm"
              >
                  <UserPlus class="h-4 w-4 mr-2" />
                  {{ t('common.add') }}
              </button>
          </form>
      </div>

      <!-- Add Board Admin Form -->
      <div class="bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 flex flex-col h-full border border-gray-200 dark:border-gray-700">
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.admins.addBoardAdmin') }}</h3>
          <div class="mt-2 max-w-xl text-sm text-gray-500 dark:text-gray-400">
              <p>{{ t('admin.admins.addBoardAdminDesc') }}</p>
          </div>
          <form @submit.prevent="handleCreateBoardAdmin" class="mt-5 space-y-3 flex-1 flex flex-col">
              <div class="w-full">
                  <label for="boardAdminLoginId" class="sr-only">{{ t('admin.admins.table.loginId') }}</label>
                  <input
                      type="text"
                      id="boardAdminLoginId"
                      v-model="newBoardAdminLoginId"
                      class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
                      :placeholder="t('admin.admins.loginIdPlaceholder')"
                  />
              </div>
              <div class="w-full">
                  <label for="boardId" class="sr-only">{{ t('admin.admins.boardId') }}</label>
                  <input
                      type="number"
                      id="boardId"
                      v-model="newBoardId"
                      class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
                      :placeholder="t('admin.admins.boardId')"
                  />
              </div>
              <button
                  type="submit"
                  class="mt-auto w-full inline-flex items-center justify-center px-4 py-2 border border-transparent shadow-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:text-sm"
              >
                  <UserPlus class="h-4 w-4 mr-2" />
                  {{ t('common.add') }}
              </button>
          </form>
      </div>
    </div>

    <!-- Super Admins List -->
    <div class="mt-8">
      <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white mb-4">{{ t('admin.admins.superAdmins') }}</h3>
      <div class="flex flex-col">
        <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
          <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
            <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg border border-gray-200 dark:border-gray-700">
              <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
                <thead class="bg-gray-50 dark:bg-gray-700">
                  <tr>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.loginId') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.name') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.status') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.createdAt') }}</th>
                    <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                      <span class="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                  <tr v-for="admin in superAdmins" :key="admin.adminId">
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ admin.loginId || '-' }}</td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ admin.displayName || '-' }}</td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                      <span class="inline-flex rounded-full px-2 text-xs font-semibold leading-5" 
                          :class="admin.superAdmin ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'">
                        {{ admin.superAdmin ? t('common.active') : t('common.inactive') }}
                      </span>
                    </td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ formatDate(admin.createdAt) }}</td>
                    <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                      <button 
                          @click="toggleAdminStatus(admin)" 
                          class="text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300"
                      >
                          {{ admin.superAdmin ? t('common.deactivate') : t('common.activate') }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Board Admins List -->
    <div class="mt-8">
      <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white mb-4">{{ t('admin.admins.boardAdmins') }}</h3>
      <div class="flex flex-col">
        <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
          <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
            <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg border border-gray-200 dark:border-gray-700">
              <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
                <thead class="bg-gray-50 dark:bg-gray-700">
                  <tr>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.loginId') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.name') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.board') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.status') }}</th>
                    <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('common.createdAt') }}</th>
                    <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                      <span class="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                  <tr v-for="admin in boardAdmins" :key="admin.adminId">
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ admin.user?.loginId || '-' }}</td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ admin.user?.displayName || '-' }}</td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ admin.board?.boardName || '-' }} (ID: {{ admin.board?.boardId || 'N/A' }})</td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">
                      <span class="inline-flex rounded-full px-2 text-xs font-semibold leading-5" 
                          :class="admin.isActive ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'">
                        {{ admin.isActive ? t('common.active') : t('common.inactive') }}
                      </span>
                    </td>
                    <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ formatDate(admin.createdAt) }}</td>
                    <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                      <button 
                          @click="toggleAdminStatus(admin)" 
                          class="text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300"
                      >
                          {{ admin.isActive ? t('common.deactivate') : t('common.activate') }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

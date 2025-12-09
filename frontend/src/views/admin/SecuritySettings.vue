<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Shield, Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()
const toastStore = useToastStore()
const { useIpBlocks, useBlockIp, useUnblockIp } = useAdmin()

const newIp = ref('')
const blockReason = ref('')

const { data: ipBlocksData, isLoading } = useIpBlocks()
const { mutateAsync: blockIp } = useBlockIp()
const { mutateAsync: unblockIp } = useUnblockIp()

const ipBlocks = computed(() => ipBlocksData.value || [])

async function handleBlockIp() {
    if (!newIp.value || !blockReason.value) return
    try {
        await blockIp({ ipAddress: newIp.value, reason: blockReason.value })
        toastStore.addToast(t('admin.security.messages.blocked'), 'success')
        newIp.value = ''
        blockReason.value = ''
    } catch (err) {
        // Error handled globally
    }
}

async function handleUnblockIp(ipAddress) {
    if (!confirm(t('admin.security.messages.confirmUnblock', { ip: ipAddress }))) return
    try {
        await unblockIp(ipAddress)
        toastStore.addToast(t('admin.security.messages.unblocked'), 'success')
    } catch (err) {
        // Error handled globally
    }
}
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.security.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.security.description') }}</p>
      </div>
    </div>
    
    <!-- Block IP Form -->
    <div class="mt-6 bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 border border-gray-200 dark:border-gray-700">
        <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.security.addTitle') }}</h3>
        <form @submit.prevent="handleBlockIp" class="mt-5 sm:flex sm:items-end space-x-4">
            <div class="w-full sm:max-w-xs">
                <label for="ipAddress" class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('admin.security.ipAddress') }}</label>
                <input
                    type="text"
                    name="ipAddress"
                    id="ipAddress"
                    v-model="newIp"
                    class="mt-1 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
                    :placeholder="t('admin.security.ipPlaceholder')"
                />
            </div>
            <div class="w-full sm:max-w-md">
                <label for="reason" class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('admin.security.reason') }}</label>
                <input
                    type="text"
                    name="reason"
                    id="reason"
                    v-model="blockReason"
                    class="mt-1 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
                    :placeholder="t('admin.security.reasonPlaceholder')"
                />
            </div>
            <button
                type="submit"
                class="mt-3 w-full inline-flex items-center justify-center px-4 py-2 border border-transparent shadow-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 sm:mt-0 sm:text-sm"
            >
                <Shield class="h-4 w-4 mr-2" />
                {{ t('common.block') }}
            </button>
        </form>
    </div>

    <!-- IP Block List -->
    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg border border-gray-200 dark:border-gray-700">
            <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
              <thead class="bg-gray-50 dark:bg-gray-700">
                <tr>
                  <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 dark:text-white sm:pl-6">{{ t('admin.security.table.ipAddress') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('admin.security.table.reason') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('admin.security.table.adminId') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white">{{ t('admin.security.table.createdAt') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                <tr v-for="block in ipBlocks" :key="block.ipAddress">
                  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 dark:text-white sm:pl-6">{{ block.ipAddress }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ block.reason }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ block.adminId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-400">{{ block.createdAt }}</td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <button 
                        @click="handleUnblockIp(block.ipAddress)" 
                        class="text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
                    >
                        <Trash2 class="h-4 w-4" />
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
</template>

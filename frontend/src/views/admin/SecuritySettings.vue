<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { Shield, Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const ipBlocks = ref([])
const newIp = ref('')
const blockReason = ref('')
const isLoading = ref(false)

async function fetchIpBlocks() {
  isLoading.value = true
  try {
    const res = await adminApi.getIpBlocks()
    if (res.data.success) {
      ipBlocks.value = res.data.data
    }
  } catch (err) {
    console.error('Failed to fetch IP blocks:', err)
  } finally {
    isLoading.value = false
  }
}

async function handleBlockIp() {
    if (!newIp.value || !blockReason.value) return
    try {
        await adminApi.blockIp({ ipAddress: newIp.value, reason: blockReason.value })
        alert(t('admin.security.messages.blocked'))
        newIp.value = ''
        blockReason.value = ''
        fetchIpBlocks()
    } catch (err) {
        console.error(err)
        alert(t('admin.security.messages.blockFailed'))
    }
}

async function handleUnblockIp(ipAddress) {
    if (!confirm(t('admin.security.messages.confirmUnblock', { ip: ipAddress }))) return
    try {
        await adminApi.unblockIp(ipAddress)
        alert(t('admin.security.messages.unblocked'))
        fetchIpBlocks()
    } catch (err) {
        console.error(err)
        alert(t('admin.security.messages.unblockFailed'))
    }
}

onMounted(() => {
  fetchIpBlocks()
})
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900">{{ t('admin.security.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700">{{ t('admin.security.description') }}</p>
      </div>
    </div>
    
    <!-- Block IP Form -->
    <div class="mt-6 bg-white shadow sm:rounded-lg p-4">
        <h3 class="text-lg font-medium leading-6 text-gray-900">{{ t('admin.security.addTitle') }}</h3>
        <form @submit.prevent="handleBlockIp" class="mt-5 sm:flex sm:items-end space-x-4">
            <div class="w-full sm:max-w-xs">
                <label for="ipAddress" class="block text-sm font-medium text-gray-700">{{ t('admin.security.ipAddress') }}</label>
                <input
                    type="text"
                    name="ipAddress"
                    id="ipAddress"
                    v-model="newIp"
                    class="mt-1 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                    :placeholder="t('admin.security.ipPlaceholder')"
                />
            </div>
            <div class="w-full sm:max-w-md">
                <label for="reason" class="block text-sm font-medium text-gray-700">{{ t('admin.security.reason') }}</label>
                <input
                    type="text"
                    name="reason"
                    id="reason"
                    v-model="blockReason"
                    class="mt-1 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                    :placeholder="t('admin.security.reasonPlaceholder')"
                />
            </div>
            <button
                type="submit"
                class="mt-3 w-full inline-flex items-center justify-center px-4 py-2 border border-transparent shadow-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 sm:mt-0 sm:text-sm"
            >
                <Shield class="h-4 w-4 mr-2" />
                {{ t('admin.security.block') }}
            </button>
        </form>
    </div>

    <!-- IP Block List -->
    <div class="mt-8 flex flex-col">
      <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
            <table class="min-w-full divide-y divide-gray-300">
              <thead class="bg-gray-50">
                <tr>
                  <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">{{ t('admin.security.table.ipAddress') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.security.table.reason') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.security.table.adminId') }}</th>
                  <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">{{ t('admin.security.table.createdAt') }}</th>
                  <th scope="col" class="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 bg-white">
                <tr v-for="block in ipBlocks" :key="block.ipAddress">
                  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">{{ block.ipAddress }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ block.reason }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ block.adminId }}</td>
                  <td class="whitespace-nowrap px-3 py-4 text-sm text-gray-500">{{ block.createdAt }}</td>
                  <td class="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                    <button 
                        @click="handleUnblockIp(block.ipAddress)" 
                        class="text-gray-600 hover:text-gray-900"
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

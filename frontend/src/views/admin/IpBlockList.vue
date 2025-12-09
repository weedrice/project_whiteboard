<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-900 dark:text-white">IP Block Management</h2>
      <div class="flex space-x-2">
        <input
          v-model="newIp"
          type="text"
          placeholder="Enter IP address"
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
        />
        <BaseButton @click="blockIp" :disabled="!newIp || loading">Block IP</BaseButton>
      </div>
    </div>

    <div v-if="loading" class="text-center py-8">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="blockedIps.length === 0" class="text-center py-8 bg-white dark:bg-gray-800 shadow rounded-lg border border-gray-200 dark:border-gray-700">
      <p class="text-gray-500 dark:text-gray-400">No blocked IPs.</p>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-md border border-gray-200 dark:border-gray-700">
      <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="ip in blockedIps" :key="ip.id" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div>
            <p class="text-sm font-medium text-gray-900 dark:text-white">{{ ip.address }}</p>
            <p class="text-xs text-gray-500 dark:text-gray-400">Blocked on: {{ new Date(ip.createdAt).toLocaleDateString() }}</p>
          </div>
          <BaseButton size="sm" variant="danger" @click="unblockIp(ip.id)">Unblock</BaseButton>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useAdmin } from '@/composables/useAdmin'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const { useIpBlocks, useBlockIp, useUnblockIp } = useAdmin()
const toastStore = useToastStore()

const newIp = ref('')

const { data: blockedIpsData, isLoading: loading } = useIpBlocks()
const { mutateAsync: blockIpMutation } = useBlockIp()
const { mutateAsync: unblockIpMutation } = useUnblockIp()

const blockedIps = computed(() => blockedIpsData.value || [])

const blockIp = async () => {
  if (!newIp.value) return
  
  try {
    await blockIpMutation({ ipAddress: newIp.value })
    toastStore.addToast('IP blocked successfully.', 'success')
    newIp.value = ''
  } catch (error) {
    // Error handled globally
  }
}

const unblockIp = async (ipAddress) => {
  if (!confirm('Unblock this IP?')) return

  try {
    await unblockIpMutation(ipAddress)
    toastStore.addToast('IP unblocked successfully.', 'success')
  } catch (error) {
    // Error handled globally
  }
}
</script>

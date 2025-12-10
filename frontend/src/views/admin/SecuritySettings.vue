<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Shield } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import IpBlockList from '@/components/admin/IpBlockList.vue'

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
    <IpBlockList :ip-blocks="ipBlocks" @unblock="handleUnblockIp" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Shield } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import IpBlockList from '@/components/admin/IpBlockList.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useConfirm } from '@/composables/useConfirm'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
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
    const isConfirmed = await confirm(t('admin.security.messages.confirmUnblock', { ip: ipAddress }))
    if (!isConfirmed) return
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
        <div
            class="mt-6 bg-white dark:bg-gray-800 shadow sm:rounded-lg p-4 border border-gray-200 dark:border-gray-700">
            <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ t('admin.security.addTitle') }}
            </h3>
            <form @submit.prevent="handleBlockIp" class="mt-5 sm:flex sm:items-end space-x-4">
                <div class="w-full sm:max-w-xs">
                    <BaseInput id="ipAddress" v-model="newIp" name="ipAddress" type="text"
                        :label="t('admin.security.ipAddress')" :placeholder="t('admin.security.ipPlaceholder')" />
                </div>
                <div class="w-full sm:max-w-md">
                    <BaseInput id="reason" v-model="blockReason" name="reason" type="text"
                        :label="t('admin.security.reason')" :placeholder="t('admin.security.reasonPlaceholder')" />
                </div>
                <BaseButton type="submit" variant="danger" class="mt-3 sm:mt-0">
                    <Shield class="h-4 w-4 mr-2" />
                    {{ t('common.block') }}
                </BaseButton>
            </form>
        </div>

        <!-- IP Block List -->
        <IpBlockList :ip-blocks="ipBlocks" @unblock="handleUnblockIp" />
    </div>
</template>

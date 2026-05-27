<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Shield } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import IpBlockList from '@/components/admin/IpBlockList.vue'
import IpBlockDetailModal from '@/components/admin/IpBlockDetailModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import { useConfirm } from '@/composables/useConfirm'
import type { IpBlock } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useIpBlocks, useBlockIp, useUnblockIp } = useAdmin()

const newIp = ref('')
const blockReason = ref('')
const page = ref(0)
const ipBlockParams = computed(() => ({
    page: page.value,
    size: 20
}))

const { data: ipBlocksData } = useIpBlocks(ipBlockParams)
const { mutateAsync: blockIp } = useBlockIp()
const { mutateAsync: unblockIp } = useUnblockIp()

const ipBlocks = computed(() => ipBlocksData.value?.content || [])
const totalPages = computed(() => ipBlocksData.value?.totalPages || 0)

const isDetailModalOpen = ref(false)
const selectedIpBlock = ref<IpBlock | null>(null)

function openDetailModal(ipBlock: IpBlock) {
    selectedIpBlock.value = ipBlock
    isDetailModalOpen.value = true
}

async function handleBlockIp() {
    if (!newIp.value || !blockReason.value) return
    try {
        await blockIp({ ipAddress: newIp.value, reason: blockReason.value })
        page.value = 0
        toastStore.addToast(t('admin.security.messages.blocked'), 'success')
        newIp.value = ''
        blockReason.value = ''
    } catch {
        // Error handled globally
    }
}

async function handleUnblockIp(ipAddress: string) {
    const isConfirmed = await confirm(t('admin.security.messages.confirmUnblock', { ip: ipAddress }))
    if (!isConfirmed) return
    try {
        await unblockIp(ipAddress)
        if (page.value > 0 && ipBlocks.value.length === 1) {
            page.value -= 1
        }
        toastStore.addToast(t('admin.security.messages.unblocked'), 'success')
    } catch {
        // Error handled globally
    }
}

watch(totalPages, (nextTotalPages) => {
    if (nextTotalPages === 0) {
        page.value = 0
        return
    }

    if (page.value >= nextTotalPages) {
        page.value = nextTotalPages - 1
    }
})
</script>

<template>
    <div>
        <AdminPageHeader :title="t('admin.security.title')" :description="t('admin.security.description')" />

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
                        :label="t('admin.security.reason')" :placeholder="t('admin.security.reasonPlaceholder')"
                        maxlength="255" />
                </div>
                <BaseButton type="submit" variant="danger" class="mt-3 sm:mt-0">
                    <Shield class="h-4 w-4 mr-2" />
                    {{ t('common.block') }}
                </BaseButton>
            </form>
        </div>

        <!-- IP Block List -->
        <IpBlockList :ip-blocks="ipBlocks" @unblock="handleUnblockIp" @viewDetail="openDetailModal" />

        <div v-if="totalPages > 0" class="mt-4">
            <Pagination :current-page="page" :total-pages="totalPages" @page-change="page = $event" />
        </div>

        <!-- IP Block Detail Modal -->
        <IpBlockDetailModal :isOpen="isDetailModalOpen" :ipBlock="selectedIpBlock" @close="isDetailModalOpen = false" />
    </div>
</template>

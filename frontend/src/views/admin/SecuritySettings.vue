<script setup lang="ts">
import { Shield } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFormPanel from '@/components/admin/AdminFormPanel.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminInlineForm from '@/components/admin/AdminInlineForm.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import IpBlockList from '@/components/admin/IpBlockList.vue'
import IpBlockDetailModal from '@/components/admin/IpBlockDetailModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useAdminIpBlocksManager } from '@/features/admin/security/useAdminIpBlocksManager'

const { t } = useI18n()
const {
    blockReason,
    closeDetailModal,
    handleBlockIp,
    handleUnblockIp,
    ipBlocks,
    isDetailModalOpen,
    newIp,
    openDetailModal,
    page,
    selectedIpBlock,
    totalPages,
} = useAdminIpBlocksManager()
</script>

<template>
    <AdminDataPage :title="t('admin.security.title')" :description="t('admin.security.description')">
        <template #filters>
        <AdminFormPanel :title="t('admin.security.addTitle')">
            <AdminInlineForm gap-class="gap-4" @submit.prevent="handleBlockIp">
                <AdminFilterField :label="t('admin.security.ipAddress')" width="fluid">
                    <BaseInput id="ipAddress" v-model="newIp" name="ipAddress" type="text"
                        :label="t('admin.security.ipAddress')" :placeholder="t('admin.security.ipPlaceholder')" hideLabel />
                </AdminFilterField>
                <AdminFilterField :label="t('admin.security.reason')" width="wide">
                    <BaseInput id="reason" v-model="blockReason" name="reason" type="text"
                        :label="t('admin.security.reason')" :placeholder="t('admin.security.reasonPlaceholder')"
                        maxlength="255" hideLabel />
                </AdminFilterField>
                <BaseButton type="submit" variant="danger" class="mt-3 sm:mt-0">
                    <Shield class="h-4 w-4 mr-2" />
                    {{ t('common.block') }}
                </BaseButton>
            </AdminInlineForm>
        </AdminFormPanel>
        </template>

        <!-- IP Block List -->
        <IpBlockList :ip-blocks="ipBlocks" @unblock="handleUnblockIp" @viewDetail="openDetailModal" />

        <AdminPaginationFooter :page="page" :total-pages="totalPages" @page-change="page = $event" />

        <!-- IP Block Detail Modal -->
        <IpBlockDetailModal :isOpen="isDetailModalOpen" :ipBlock="selectedIpBlock" @close="closeDetailModal" />
    </AdminDataPage>
</template>
